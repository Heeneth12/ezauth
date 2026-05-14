package com.ezh.ezauth.user.service;

import com.ezh.ezauth.common.dto.AddressDto;
import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.EntityType;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import com.ezh.ezauth.common.repository.ModuleRepository;
import com.ezh.ezauth.common.repository.PrivilegeRepository;
import com.ezh.ezauth.common.repository.RoleRepository;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.dto.*;
import com.ezh.ezauth.user.entity.*;

import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.repository.projection.UserMiniProjection;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final ModuleRepository moduleRepository;
    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserTypeConfigService userTypeConfigService;
    private final CacheManager cacheManager;

    public UserInitResponse getUserInitDetails(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (!user.getIsActive()) {
            throw new CommonException("Account is inactive. Contact your administrator.", HttpStatus.FORBIDDEN);
        }

        Set<UserApplicationDto> applicationDtos = user.getUserApplications().stream()
                .filter(UserApplication::getIsActive) // Only active applications
                .map(ua -> UserApplicationDto.builder()
                        .id(ua.getId())
                        .appKey(ua.getApplication().getAppKey())
                        .appName(ua.getApplication().getAppName())
                        .isActive(ua.getIsActive())
                        .modulePrivileges(groupModulePrivileges(ua))
                        .build()
                ).collect(Collectors.toSet());

        Set<String> roles = user.getUserRoles().stream()
                .filter(UserRole::getIsActive)
                .filter(ur -> ur.getExpiresAt() == null || ur.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(ur -> ur.getRole().getRoleKey()) // or getRoleName()
                .collect(Collectors.toSet());

        return UserInitResponse.builder()
                .id(user.getId())
                .userUuid(user.getUserUuid())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .userType(user.getUserType().toString())
                .isActive(user.getIsActive())
                .tenantName(user.getTenant().getTenantName())
                .tenantId(user.getTenant().getId())
                .userApplications(applicationDtos)
                .userRoles(roles)
                .build();
    }

    @Transactional
    public CommonResponse createUser(CreateUserRequest request) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Invalid tenant", HttpStatus.BAD_REQUEST));

        boolean isLoginEnabled = !request.getUserType().equals(UserType.CUSTOMER);

        // accountScope is always TENANT here — platform users can only be created via internal tooling.
        // This prevents a tenant admin from escalating by passing userType = KUBEE_OPS.
        User user = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .isActive(true)
                .userType(request.getUserType())
                .accountScope(AccountScope.TENANT)
                .isLoginEnabled(isLoginEnabled)
                .tenant(tenant)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .userRoles(new HashSet<>())
                .userApplications(new HashSet<>())
                .addresses(new HashSet<>())
                .build();

        // Apply user type defaults if applicable
        applyUserTypeDefaults(user, request, tenant);

        // Delegate to sync methods
        syncUserRoles(user, request.getRoleIds());

        syncUserApplications(user, request.getApplicationIds());

        if (isLoginEnabled) {
            syncUserPrivileges(user, request.getPrivilegeMapping());
        }

        syncUserAddresses(user, request.getAddress());

        User savedUser = userRepository.save(user);

        return CommonResponse.builder()
                .id(savedUser.getId().toString())
                .message("User successfully created")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    @CacheEvict(value = "userInitCache", key = "#userId")
    public CommonResponse updateUser(Long userId, CreateUserRequest request) throws CommonException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        // Update Basic Info
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // Delegate to sync methods
        syncUserRoles(user, request.getRoleIds());
        syncUserApplications(user, request.getApplicationIds());
        syncUserPrivileges(user, request.getPrivilegeMapping());
        syncUserAddresses(user, request.getAddress());

        User savedUser = userRepository.save(user);

        return CommonResponse.builder()
                .id(savedUser.getId().toString())
                .message("User successfully updated")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public Map<Long, UserMiniDto> getUsersMiniByIds(List<Long> userIds, boolean includeAddress) throws CommonException {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // When addresses are requested, bypass cache and fetch full entities with JOIN FETCH
        if (includeAddress) {
            Map<Long, UserMiniDto> resultMap = new HashMap<>();
            List<User> users = userRepository.findUsersWithAddressesByIds(userIds);
            for (User user : users) {
                List<AddressDto> addresses = user.getAddresses() == null ? Collections.emptyList() :
                        user.getAddresses().stream()
                                .map(this::mapToAddressDto)
                                .collect(Collectors.toList());

                UserMiniDto dto = UserMiniDto.builder()
                        .id(user.getId())
                        .userType(user.getUserType().toString())
                        .UserUuid(user.getUserUuid())
                        .name(user.getFullName())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .userAddresses(addresses)
                        .build();
                resultMap.put(dto.getId(), dto);
            }
            return resultMap;
        }

        // Default path: use cache + projection query (no addresses)
        Cache cache = cacheManager.getCache("userMiniCache");
        Map<Long, UserMiniDto> resultMap = new HashMap<>();
        List<Long> missingIds = new ArrayList<>();

        for (Long id : userIds) {
            UserMiniDto cachedUser = null;
            if (cache != null) {
                cachedUser = cache.get(id, UserMiniDto.class);
            }

            if (cachedUser != null) {
                resultMap.put(id, cachedUser);
            } else {
                missingIds.add(id);
            }
        }

        if (!missingIds.isEmpty()) {
            List<UserMiniProjection> projections = userRepository.findUserMini(missingIds);

            for (UserMiniProjection p : projections) {
                UserMiniDto dto = UserMiniDto.builder()
                        .id(p.getId())
                        .userType(p.getUserType())
                        .UserUuid(p.getUserUuid())
                        .name(p.getFullName())
                        .email(p.getEmail())
                        .phone(p.getPhone())
                        .build();

                resultMap.put(dto.getId(), dto);

                if (cache != null) {
                    cache.put(dto.getId(), dto);
                }
            }
        }
        return resultMap;
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long userId, boolean isFullDetails) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();

        // 1. Fetch User
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        // 2. Build Base DTO
        UserDto.UserDtoBuilder dtoBuilder = UserDto.builder()
                .id(user.getId())
                .userUuid(user.getUserUuid())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .userType(user.getUserType().toString())
                .profilePictureUuid(user.getProfilePictureUuid())
                .tenantId(user.getTenant().getId());

        // 3. Basic Role Keys (Set<String>)
        if (user.getUserRoles() != null) {
            dtoBuilder.roles(user.getUserRoles().stream()
                    .filter(UserRole::getIsActive)
                    .map(ur -> ur.getRole().getRoleKey())
                    .collect(Collectors.toSet()));
        }

        // 4. Full Details (For Edit Screen)
        if (isFullDetails) {

            // A. Detailed Roles
            if (user.getUserRoles() != null) {
                dtoBuilder.userRoles(user.getUserRoles().stream()
                        .filter(UserRole::getIsActive)
                        .map(ur -> UserRoleDto.builder()
                                .id(ur.getId())
                                .roleId(ur.getRole().getId())
                                .roleName(ur.getRole().getRoleName())
                                .roleKey(ur.getRole().getRoleKey())
                                .build())
                        .collect(Collectors.toList()));
            }

            // B. Addresses
            if (user.getAddresses() != null) {
                dtoBuilder.addresses(user.getAddresses().stream()
                        .map(this::mapToAddressDto)
                        .collect(Collectors.toSet()));
            }

            // C. Application IDs (Simple Set)
            if (user.getUserApplications() != null) {
                dtoBuilder.applicationIds(user.getUserApplications().stream()
                        .filter(UserApplication::getIsActive)
                        .map(ua -> ua.getApplication().getId())
                        .collect(Collectors.toSet()));
            }

            // D. Detailed Applications & Privileges
            if (user.getUserApplications() != null) {
                List<UserDto.UserAppEditDto> appEditDtos = user.getUserApplications().stream()
                        .filter(UserApplication::getIsActive)
                        .map(ua -> {
                            // Map Module Privileges
                            List<UserDto.UserModulePrivilegeDto> privs = ua.getModulePrivileges().stream()
                                    .filter(UserModulePrivilege::getIsActive)
                                    .map(ump -> UserDto.UserModulePrivilegeDto.builder()
                                        .moduleId(ump.getPrivilege().getModule().getId())  // ✅ via privilege
                                        .privilegeId(ump.getPrivilege().getId())
                                        .privilegeName(ump.getPrivilege().getPrivilegeName())
                                        .privilegeKey(ump.getPrivilege().getPrivilegeKey())
                                        .build())
                                    .collect(Collectors.toList());

                            return UserDto.UserAppEditDto.builder()
                                    .applicationId(ua.getApplication().getId())
                                    .appName(ua.getApplication().getAppName())
                                    .isActive(ua.getIsActive())
                                    .modulePrivileges(privs)
                                    .build();
                        })
                        .collect(Collectors.toList());

                dtoBuilder.userApplications(appEditDtos);
            }
        }

        return dtoBuilder.build();
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(UserFilter filter, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        String email = (filter != null && StringUtils.hasText(filter.getEmail())) ? filter.getEmail().trim().toLowerCase() : null;
        String search = (filter != null && StringUtils.hasText(filter.getSearchQuery())) ? filter.getSearchQuery().trim().toLowerCase() : null;
        Long tenantId = (filter != null) ? filter.getTenantId() : null;
        Long userId = (filter != null) ? filter.getUserId() : null;
        String userUuid = (filter != null) ? filter.getUserUuid() : null;
        String phone = (filter != null) ? filter.getPhone() : null;
        List<UserType> userTypes = (filter != null) ? filter.getUserType() : null;
        Boolean isActive = (filter != null) ? filter.getIsActive() : null;

        return userRepository
                .findUsersWithAllFilters(tenantId, userId, userUuid, email, phone, search, userTypes, isActive, pageable)
                .map(dto -> constructUserDto(dto, false)); // false for basic details only
    }

    @Transactional
    @CacheEvict(value = "userInitCache", key = "#userId")
    public CommonResponse toggleUserStatus(Long userId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        // Prevent toggling SUPER_ADMIN status
        boolean isSuperAdmin = user.getUserRoles().stream()
                .anyMatch(userRole -> "SUPER_ADMIN".equals(userRole.getRole().getRoleKey()));

        if (isSuperAdmin){
            return CommonResponse.builder()
                    .id(user.getId().toString())
                    .status(Status.FAILURE)
                    .message("Cannot change status of SUPER_ADMIN user")
                    .build();
        }

        user.setIsActive(!user.getIsActive());
        userRepository.save(user);

        return CommonResponse.builder()
                .id(user.getId().toString())
                .status(Status.SUCCESS)
                .message("User status toggled successfully, current status: " + (user.getIsActive() ? "Active" : "Inactive"))
                .build();
    }

    @Transactional
    @CacheEvict(value = "userInitCache", key = "#userId")
    public CommonResponse addUserAddress(Long userId, AddressDto request) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (user.getAddresses() == null) {
            user.setAddresses(new HashSet<>());
        }

        Address newAddress = mapToAddressEntity(request, userId);
        user.getAddresses().add(newAddress);
        userRepository.save(user);

        return CommonResponse.builder()
                .id(user.getId().toString())
                .message("User address successfully added")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    @CacheEvict(value = "userInitCache", key = "#userId")
    public CommonResponse updateUserAddress(Long userId, Long addressId, AddressDto request) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (user.getAddresses() == null) {
            throw new CommonException("Address not found", HttpStatus.NOT_FOUND);
        }

        Address addressToUpdate = user.getAddresses().stream()
                .filter(a -> a.getId() != null && a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));

        updateAddressEntity(addressToUpdate, request);
        userRepository.save(user);

        return CommonResponse.builder()
                .id(user.getId().toString())
                .message("User address successfully updated")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public Set<AddressDto> getUserAddresses(Long userId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (user.getAddresses() == null) {
            return Collections.emptySet();
        }

        return user.getAddresses().stream()
                .map(this::mapToAddressDto)
                .collect(Collectors.toSet());
    }

    @Transactional
    @CacheEvict(value = "userInitCache", key = "#userId")
    public CommonResponse deleteUserAddress(Long userId, Long addressId) throws CommonException {
        Long tenantId = UserContextUtil.getTenantId();
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (user.getAddresses() == null || user.getAddresses().isEmpty()) {
            throw new CommonException("No addresses found for this user", HttpStatus.NOT_FOUND);
        }

        Address addressToDelete = user.getAddresses().stream()
                .filter(a -> a.getId() != null && a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));

        user.getAddresses().remove(addressToDelete);
        userRepository.save(user);

        return CommonResponse.builder()
                .id(userId.toString())
                .message("Address deleted successfully")
                .status(Status.SUCCESS)
                .build();
    }

    /**
     * Syncs Roles: Removes unselected, Adds new ones.
     */
    private void syncUserRoles(User user, Set<Long> roleIds) {
        if (roleIds == null) return;
        if (user.getUserRoles() == null) user.setUserRoles(new HashSet<>());

        // 1. Remove roles not in the request
        user.getUserRoles().removeIf(ur -> !roleIds.contains(ur.getRole().getId()));

        // 2. Add new roles
        Set<Long> existingRoleIds = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        roleIds.stream()
                .filter(id -> !existingRoleIds.contains(id))
                .forEach(id -> {
                    Role role = roleRepository.findById(id)
                            .orElseThrow(() -> new CommonException("Role not found: " + id, HttpStatus.BAD_REQUEST));
                    user.getUserRoles().add(UserRole.builder().user(user).role(role).isActive(true).build());
                });
    }

    /**
     * Syncs Applications: Removes unselected, Adds new ones.
     */
    private void syncUserApplications(User user, Set<Long> appIds) {
        if (appIds == null) return;
        if (user.getUserApplications() == null) user.setUserApplications(new HashSet<>());

        // 1. Remove apps not in the request
        user.getUserApplications().removeIf(ua -> !appIds.contains(ua.getApplication().getId()));

        // 2. Add new apps
        Set<Long> existingAppIds = user.getUserApplications().stream()
                .map(ua -> ua.getApplication().getId())
                .collect(Collectors.toSet());

        for (Long appId : appIds) {
            if (!existingAppIds.contains(appId)) {
                Application app = applicationRepository.findById(appId)
                        .orElseThrow(() -> new CommonException("App not found: " + appId, HttpStatus.BAD_REQUEST));

                user.getUserApplications().add(UserApplication.builder()
                        .user(user)
                        .application(app)
                        .isActive(true)
                        .modulePrivileges(new HashSet<>())
                        .build());
            }
        }
    }

    /**
     * Syncs Module Privileges for User Applications.
     */
    private void syncUserPrivileges(User user, List<PrivilegeAssignRequest> privilegeMappings) {
        if (privilegeMappings == null || user.getUserApplications() == null) return;

        for (PrivilegeAssignRequest pm : privilegeMappings) {
            UserApplication ua = user.getUserApplications().stream()
                    .filter(x -> x.getApplication().getId().equals(pm.getApplicationId()))
                    .findFirst()
                    .orElseThrow(() -> new CommonException("Application mismatch for ID: " + pm.getApplicationId(), HttpStatus.BAD_REQUEST));

            if (ua.getModulePrivileges() == null) ua.setModulePrivileges(new HashSet<>());

            Set<Long> targetPrivilegeIds = new HashSet<>(pm.getPrivilegeIds());

            // ✅ derive module via privilege.getModule()
            ua.getModulePrivileges().removeIf(ump ->
                    ump.getPrivilege().getModule().getId().equals(pm.getModuleId()) &&
                            !targetPrivilegeIds.contains(ump.getPrivilege().getId())
            );

            // ✅ derive module via privilege.getModule()
            Set<Long> existingPrivilegeIds = ua.getModulePrivileges().stream()
                    .filter(ump -> ump.getPrivilege().getModule().getId().equals(pm.getModuleId()))
                    .map(ump -> ump.getPrivilege().getId())
                    .collect(Collectors.toSet());

            // ✅ module fetch no longer needed — remove moduleRepository call entirely
            List<Privilege> privilegesToAdd = privilegeRepository.findAllById(pm.getPrivilegeIds());

            for (Privilege priv : privilegesToAdd) {
                if (!existingPrivilegeIds.contains(priv.getId())) {
                    ua.getModulePrivileges().add(UserModulePrivilege.builder()
                            .userApplication(ua)
                            // ✅ no .module() here
                            .privilege(priv)
                            .isActive(true)
                            .build());
                }
            }
        }
    }

    /**
     * Smart Update for Addresses (Orphan Removal, Update, Insert).
     */
    private void syncUserAddresses(User user, Set<AddressDto> incomingAddresses) {
        if (incomingAddresses == null) return;
        if (user.getAddresses() == null) user.setAddresses(new HashSet<>());

        Set<Long> incomingIds = incomingAddresses.stream()
                .map(AddressDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        user.getAddresses().removeIf(existingAddr ->
                existingAddr.getId() != null && !incomingIds.contains(existingAddr.getId())
        );

        for (AddressDto dto : incomingAddresses) {
            if (dto.getId() == null) {
                user.getAddresses().add(mapToAddressEntity(dto, user.getId()));
            } else {
                user.getAddresses().stream()
                        .filter(a -> a.getId().equals(dto.getId()))
                        .findFirst()
                        .ifPresent(existingAddr -> updateAddressEntity(existingAddr, dto));
            }
        }
    }


    private Address mapToAddressEntity(AddressDto dto, Long userId) {
        return Address.builder()
                .entityType(EntityType.USER)
                .entityId(userId)
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .route(dto.getRoute())
                .area(dto.getArea())
                .city(dto.getCity())
                .state(dto.getState())
                .country(dto.getCountry())
                .pinCode(dto.getPinCode())
                .addressType(dto.getType())
                .build();
    }

    private void updateAddressEntity(Address entity, AddressDto dto) {
        entity.setAddressLine1(dto.getAddressLine1());
        entity.setAddressLine2(dto.getAddressLine2());
        entity.setRoute(dto.getRoute());
        entity.setArea(dto.getArea());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setCountry(dto.getCountry());
        entity.setPinCode(dto.getPinCode());
        entity.setAddressType(dto.getType());
    }

    private AddressDto mapToAddressDto(Address entity) {
        return AddressDto.builder()
                .id(entity.getId())
                .addressLine1(entity.getAddressLine1())
                .addressLine2(entity.getAddressLine2())
                .route(entity.getRoute())
                .area(entity.getArea())
                .city(entity.getCity())
                .state(entity.getState())
                .country(entity.getCountry())
                .pinCode(entity.getPinCode())
                .type(entity.getAddressType())
                .isPrimary(entity.getIsPrimary())
                .build();
    }

    private UserDto constructUserDto(User user, boolean sendAddressDetails) {
        if (user == null) return null;

        Set<AddressDto> userAddresses = null;

        if (sendAddressDetails) {
            userAddresses = user.getAddresses()
                    .stream()
                    .map(this::mapToAddressDto)
                    .collect(Collectors.toSet());
        }

        return UserDto.builder()
                .id(user.getId())
                .userUuid(user.getUserUuid())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .userType(user.getUserType().toString())
                .addresses(userAddresses)
                .roles(user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getRoleKey())
                        .collect(Collectors.toSet()))
                .build();
    }

    private Map<String, Set<String>> groupModulePrivileges(UserApplication ua) {
        return ua.getModulePrivileges().stream()
                .filter(UserModulePrivilege::getIsActive)
                .collect(Collectors.groupingBy(
                        ump -> ump.getPrivilege().getModule().getModuleKey(), // ✅ via privilege
                        Collectors.mapping(
                                ump -> ump.getPrivilege().getPrivilegeKey(),
                                Collectors.toSet()
                        )
                ));
    }
    /**
     * Apply user type-specific defaults to the request.
     * If enforceDefaults is true, always applies defaults regardless of request data.
     * Otherwise, merges request data with defaults (request data takes precedence).
     */
    private void applyUserTypeDefaults(User user, CreateUserRequest request, Tenant tenant) {
        UserType userType = request.getUserType();

        // Only apply defaults if user type has configured defaults
        if (!userTypeConfigService.hasDefaults(userType)) {
            log.debug("No defaults configured for user type: {}", userType);
            return;
        }

        boolean enforceDefaults = userTypeConfigService.shouldEnforceDefaults(userType);
        log.info("Applying default configuration for user type: {} (enforceDefaults={})", userType, enforceDefaults);

        // Apply role defaults
        if (enforceDefaults || request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
            Set<String> defaultRoleKeys = userTypeConfigService.getDefaultRoleKeys(userType);
            Set<Long> roleIds = resolveRoleIds(defaultRoleKeys, tenant);
            request.setRoleIds(roleIds);
            log.info("Applied default roles for {}: {} (enforced={})", userType, defaultRoleKeys, enforceDefaults);
        }

        // Apply application defaults
        if (enforceDefaults || request.getApplicationIds() == null || request.getApplicationIds().isEmpty()) {
            Set<String> defaultAppKeys = userTypeConfigService.getDefaultApplicationKeys(userType);
            Set<Long> appIds = resolveApplicationIds(defaultAppKeys);
            request.setApplicationIds(appIds);
            log.info("Applied default applications for {}: {} (enforced={})", userType, defaultAppKeys, enforceDefaults);
        }

        // Apply privilege defaults
        if (enforceDefaults || request.getPrivilegeMapping() == null || request.getPrivilegeMapping().isEmpty()) {
            List<PrivilegeAssignRequest> privilegeMappings = resolvePrivilegeMappings(userType);
            request.setPrivilegeMapping(privilegeMappings);
            log.info("Applied default privileges for {} (enforced={})", userType, enforceDefaults);
        }
    }

    /**
     * Resolve role keys to role IDs. Creates ADMIN role if it doesn't exist.
     */
    private Set<Long> resolveRoleIds(Set<String> roleKeys, Tenant tenant) {
        Set<Long> roleIds = new HashSet<>();

        for (String roleKey : roleKeys) {
            Role role = roleRepository.findByRoleKeyAndTenantId(roleKey, tenant.getId())
                    .orElseGet(() -> {
                        // Create ADMIN role if it doesn't exist
                        if ("ADMIN".equals(roleKey)) {
                            log.info("ADMIN role not found for tenant {}. Creating it.", tenant.getId());
                            Role newRole = Role.builder()
                                    .roleKey("ADMIN")
                                    .roleName("Administrator")
                                    .description("Default administrator role")
                                    .tenant(tenant)
                                    .isActive(true)
                                    .isSystemRole(false)
                                    .build();
                            return roleRepository.save(newRole);
                        } else {
                            log.warn("Role with key '{}' not found for tenant {}", roleKey, tenant.getId());
                            return null;
                        }
                    });

            if (role != null) {
                roleIds.add(role.getId());
            }
        }

        return roleIds;
    }

    /**
     * Resolve application keys to application IDs
     */
    private Set<Long> resolveApplicationIds(Set<String> appKeys) {
        Set<Long> appIds = new HashSet<>();

        for (String appKey : appKeys) {
            Application app = applicationRepository.findByAppKey(appKey)
                    .orElse(null);

            if (app != null) {
                appIds.add(app.getId());
            } else {
                log.warn("Application with key '{}' not found", appKey);
            }
        }

        return appIds;
    }

    /**
     * Resolve privilege configurations to PrivilegeAssignRequest list
     */
    private List<PrivilegeAssignRequest> resolvePrivilegeMappings(UserType userType) {
        List<PrivilegeAssignRequest> mappings = new ArrayList<>();
        List<UserTypeConfigService.PrivilegeConfig> configs = userTypeConfigService.getDefaultPrivilegeConfigs(userType);

        for (UserTypeConfigService.PrivilegeConfig config : configs) {
            // Find module by key
            Module module = moduleRepository.findByModuleKey(config.getModuleKey())
                    .orElse(null);

            if (module == null) {
                log.warn("Module with key '{}' not found", config.getModuleKey());
                continue;
            }

            // Find privileges by keys
            List<Long> privilegeIds = new ArrayList<>();
            for (String privKey : config.getPrivilegeKeys()) {
                Privilege privilege = privilegeRepository.findByPrivilegeKeyAndModuleId(privKey, module.getId())
                        .orElse(null);

                if (privilege != null) {
                    privilegeIds.add(privilege.getId());
                } else {
                    log.warn("Privilege with key '{}' not found for module {}", privKey, module.getModuleKey());
                }
            }

            if (!privilegeIds.isEmpty()) {
                PrivilegeAssignRequest request = new PrivilegeAssignRequest();
                request.setApplicationId(module.getApplication().getId());
                request.setModuleId(module.getId());
                request.setPrivilegeIds(new HashSet<>(privilegeIds));
                mappings.add(request);
            }
        }

        return mappings;
    }

    public Page<UserDto> searchUsers(UserFilter filter, Integer page, Integer size) {
        int safePage = page != null ? page : 0;
        int safeSize = (size != null) ? Math.min(size, 500) : 50;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").descending());

        String email = (filter != null && StringUtils.hasText(filter.getEmail())) ? filter.getEmail().trim().toLowerCase() : null;
        String search = (filter != null && StringUtils.hasText(filter.getSearchQuery())) ? filter.getSearchQuery().trim().toLowerCase() : null;
        Long tenantId = (filter != null) ? filter.getTenantId() : null;
        Long userId = (filter != null) ? filter.getUserId() : null;
        String userUuid = (filter != null) ? filter.getUserUuid() : null;
        String phone = (filter != null) ? filter.getPhone() : null;
        List<UserType> userTypes = (filter != null) ? filter.getUserType() : null;
        Boolean isActive = (filter != null) ? filter.getIsActive() : null;

        return userRepository
                .findUsersWithAllFilters(tenantId, userId, userUuid, email, phone, search, userTypes, isActive, pageable)
                .map(dto -> constructUserDto(dto, true));
    }
}