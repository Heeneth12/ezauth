package com.ezh.ezauth.user.service;

import com.ezh.ezauth.common.entity.Application;
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
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public UserInitResponse getUserInitDetails(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (!user.getIsActive()) {
            throw new IllegalStateException("User account is inactive");
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

        User user = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .isActive(true)
                .userType(request.getUserType())
                .tenant(tenant)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .userRoles(new HashSet<>())
                .userApplications(new HashSet<>())
                .addresses(new HashSet<>())
                .build();

        // Delegate to sync methods
        syncUserRoles(user, request.getRoleIds());
        syncUserApplications(user, request.getApplicationIds());
        syncUserPrivileges(user, request.getPrivilegeMapping());
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
                dtoBuilder.userAddress(user.getAddresses().stream()
                        .map(this::mapToAddressDto) // Assuming mapToAddressDto exists in your service
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
                                            .moduleId(ump.getModule().getId())
                                            .privilegeId(ump.getPrivilege().getId())
                                            // MAPPING NEW FIELDS
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

        return userRepository.findUsersWithAllFilters(tenantId, userId, userUuid, email, phone, search, pageable)
                .map(this::constructUserDto);
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

            // Target privileges for this specific module
            Set<Long> targetPrivilegeIds = new HashSet<>(pm.getPrivilegeIds());

            // 1. Remove privileges for this module that are NOT in the request
            ua.getModulePrivileges().removeIf(ump ->
                    ump.getModule().getId().equals(pm.getModuleId()) &&
                            !targetPrivilegeIds.contains(ump.getPrivilege().getId())
            );

            // 2. Add new privileges
            Set<Long> existingPrivilegeIds = ua.getModulePrivileges().stream()
                    .filter(ump -> ump.getModule().getId().equals(pm.getModuleId()))
                    .map(ump -> ump.getPrivilege().getId())
                    .collect(Collectors.toSet());

            Module module = moduleRepository.findById(pm.getModuleId())
                    .orElseThrow(() -> new CommonException("Module not found: " + pm.getModuleId(), HttpStatus.BAD_REQUEST));

            List<Privilege> privilegesToAdd = privilegeRepository.findAllById(pm.getPrivilegeIds());

            for (Privilege priv : privilegesToAdd) {
                if (!existingPrivilegeIds.contains(priv.getId())) {
                    ua.getModulePrivileges().add(UserModulePrivilege.builder()
                            .userApplication(ua)
                            .module(module)
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
    private void syncUserAddresses(User user, Set<UserAddressDto> incomingAddresses) {
        if (incomingAddresses == null) return;
        if (user.getAddresses() == null) user.setAddresses(new HashSet<>());

        // 1. Identify IDs to keep
        Set<Long> incomingIds = incomingAddresses.stream()
                .map(UserAddressDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. Remove addresses not in the request (Orphan Removal)
        user.getAddresses().removeIf(existingAddr ->
                existingAddr.getId() != null && !incomingIds.contains(existingAddr.getId())
        );

        // 3. Upsert (Update or Insert)
        for (UserAddressDto dto : incomingAddresses) {
            if (dto.getId() == null) {
                // Insert New
                user.getAddresses().add(mapToAddressEntity(dto, user, user.getTenant()));
            } else {
                // Update Existing
                user.getAddresses().stream()
                        .filter(a -> a.getId().equals(dto.getId()))
                        .findFirst()
                        .ifPresent(existingAddr -> updateAddressEntity(existingAddr, dto));
            }
        }
    }


    private UserAddress mapToAddressEntity(UserAddressDto dto, User user, Tenant tenant) {
        return UserAddress.builder()
                .user(user)
                .tenant(tenant)
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

    private void updateAddressEntity(UserAddress entity, UserAddressDto dto) {
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

    private UserAddressDto mapToAddressDto(UserAddress entity) {
        return UserAddressDto.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .addressLine1(entity.getAddressLine1())
                .addressLine2(entity.getAddressLine2())
                .route(entity.getRoute())
                .area(entity.getArea())
                .city(entity.getCity())
                .state(entity.getState())
                .country(entity.getCountry())
                .pinCode(entity.getPinCode())
                .type(entity.getAddressType())
                .build();
    }

    private UserDto constructUserDto(User user) {
        if (user == null) return null;
        return UserDto.builder()
                .id(user.getId())
                .userUuid(user.getUserUuid())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .roles(user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getRoleKey())
                        .collect(Collectors.toSet()))
                .build();
    }

    private Map<String, Set<String>> groupModulePrivileges(UserApplication ua) {
        return ua.getModulePrivileges().stream()
                .filter(UserModulePrivilege::getIsActive)
                .collect(Collectors.groupingBy(
                        ump -> ump.getModule().getModuleKey(),
                        Collectors.mapping(
                                ump -> ump.getPrivilege().getPrivilegeKey(),
                                Collectors.toSet()
                        )
                ));
    }
}