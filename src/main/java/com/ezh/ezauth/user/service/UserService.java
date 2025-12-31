package com.ezh.ezauth.user.service;


import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import com.ezh.ezauth.common.repository.ModuleRepository;
import com.ezh.ezauth.common.repository.PrivilegeRepository;
import com.ezh.ezauth.common.repository.RoleRepository;
import com.ezh.ezauth.tenant.dto.TenantDto;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.dto.*;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserApplication;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import com.ezh.ezauth.user.entity.UserRole;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final ModuleRepository moduleRepository;
    private final UserModulePrivilegeRepository userModulePrivilegeRepository;
    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;
    private final PasswordEncoder passwordEncoder;

    public UserInitResponse getUserInitDetails(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Validate user is active
        if (!user.getIsActive()) {
            throw new IllegalStateException("User account is inactive");
        }

        // Map userApplications → DTO
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

        // Map userRoles → role names (get from actual database)
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
                .isActive(user.getIsActive())
                .tenantName(user.getTenant().getTenantName())
                .tenantId(user.getTenant().getId())
                .userApplications(applicationDtos)
                .userRoles(roles)
                .build();
    }


    @Transactional()
    public CommonResponse createUser(CreateUserRequest request) throws CommonException {

        Long tenantId = UserContextUtil.getTenantId();
        if (tenantId == null) {
            throw new CommonException("Tenant id missing in request", HttpStatus.UNAUTHORIZED);
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Invalid tenant", HttpStatus.BAD_REQUEST));

        User user = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .isActive(true)
                .tenant(tenant)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        // TODO: Assign Roles
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<UserRole> roles = request.getRoleIds().stream()
                    .map(roleId -> {
                        Role role = roleRepository.findById(roleId)
                                .orElseThrow(() -> new CommonException("Invalid role ID: " + roleId, HttpStatus.BAD_REQUEST));
                        return UserRole.builder()
                                .role(role)
                                .user(user)
                                .isActive(true)
                                .build();
                    })
                    .collect(Collectors.toSet());

            user.setUserRoles(roles);
        }

        // TODO: Assign Applications
        Set<UserApplication> userApplications = new HashSet<>();
        if (request.getApplicationIds() != null) {
            List<Application> apps = applicationRepository.findAllById(request.getApplicationIds());

            for (Application app : apps) {
                UserApplication ua = UserApplication.builder()
                        .user(user)
                        .application(app)
                        .isActive(true)
                        .build();

                userApplications.add(ua);
            }
        }
        user.setUserApplications(userApplications);

        // TODO: ASSIGN PRIVILEGES (MODULE WISE)
        if (request.getPrivilegeMapping() != null) {

            for (PrivilegeAssignRequest pm : request.getPrivilegeMapping()) {
                // 1. Find the application assigned to user
                UserApplication ua = userApplications.stream()
                        .filter(x -> x.getApplication().getId().equals(pm.getApplicationId()))
                        .findFirst()
                        .orElseThrow(() -> new CommonException("Application not assigned to user", HttpStatus.BAD_REQUEST));

                // 2. Find module
                Module module = moduleRepository.findById(pm.getModuleId())
                        .orElseThrow(() -> new CommonException("Invalid module", HttpStatus.BAD_REQUEST));

                // 3. Fetch privilege entities
                List<Privilege> privileges = privilegeRepository.findAllById(pm.getPrivilegeIds());

                // 4. Create the mapping entities
                Set<UserModulePrivilege> newModulePrivileges = privileges.stream()
                        .map(p -> UserModulePrivilege.builder()
                                .userApplication(ua)
                                .module(module)
                                .privilege(p)
                                .isActive(true)
                                .build()
                        ).collect(Collectors.toSet());

                // Initialize the list if it's null (Lombok @Builder leaves collections null by default)
                if (ua.getModulePrivileges() == null) {
                    ua.setModulePrivileges(new HashSet<>());
                }

                // Add to the existing set instead of overwriting it
                ua.getModulePrivileges().addAll(newModulePrivileges);
            }
        }

        User savedUser = userRepository.save(user);
        return CommonResponse
                .builder()
                .id(savedUser.getId().toString())
                .message("User successfully creates")
                .status(Status.SUCCESS)
                .build();
    }


    @Transactional
    public CommonResponse updateUser(Long userId, CreateUserRequest request) throws CommonException {

        // 1. Fetch Existing User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        // 2. Update Basic Fields
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        // Note: Usually we don't update Email or Password here unless specific logic allows it.
        // If you want to update password:
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // 3. Update Roles (Smart Update: Remove unselected, Add new selected)
        if (request.getRoleIds() != null) {
            if (user.getUserRoles() == null) {
                user.setUserRoles(new HashSet<>());
            }

            Set<Long> newRoleIds = new HashSet<>(request.getRoleIds());

            // A. Remove roles that are NOT in the new request
            // This physically deletes the row from DB due to orphanRemoval=true
            user.getUserRoles().removeIf(existingUserRole ->
                    !newRoleIds.contains(existingUserRole.getRole().getId()));

            // B. Add roles that are in the request but NOT yet in the user's list
            // First, get list of IDs currently assigned (after the removal step above)
            Set<Long> existingRoleIds = user.getUserRoles().stream()
                    .map(ur -> ur.getRole().getId())
                    .collect(Collectors.toSet());

            // Filter the request for IDs that we don't have yet
            Set<UserRole> rolesToAdd = newRoleIds.stream()
                    .filter(roleId -> !existingRoleIds.contains(roleId))
                    .map(roleId -> {
                        Role role = roleRepository.findById(roleId)
                                .orElseThrow(() -> new CommonException("Invalid role ID: " + roleId, HttpStatus.BAD_REQUEST));
                        return UserRole.builder()
                                .role(role)
                                .user(user)
                                .isActive(true)
                                .build();
                    })
                    .collect(Collectors.toSet());

            user.getUserRoles().addAll(rolesToAdd);
        }

        // 4. Update Applications & Privileges
        // Strategy: We clear all existing applications and rebuild them based on the request.
        // This ensures that any application NOT in the request is removed.

        // 4. Update Applications (Smart Update)
        if (request.getApplicationIds() != null) {
            if (user.getUserApplications() == null) {
                user.setUserApplications(new HashSet<>());
            }

            Set<Long> newAppIds = new HashSet<>(request.getApplicationIds());

            // A. Remove apps not in request
            user.getUserApplications().removeIf(ua ->
                    !newAppIds.contains(ua.getApplication().getId()));

            // B. Add new apps
            Set<Long> existingAppIds = user.getUserApplications().stream()
                    .map(ua -> ua.getApplication().getId())
                    .collect(Collectors.toSet());

            for (Long appId : newAppIds) {
                if (!existingAppIds.contains(appId)) {
                    Application app = applicationRepository.findById(appId)
                            .orElseThrow(() -> new CommonException("App not found", HttpStatus.BAD_REQUEST));

                    UserApplication ua = UserApplication.builder()
                            .user(user)
                            .application(app)
                            .isActive(true)
                            .modulePrivileges(new HashSet<>())
                            .build();

                    user.getUserApplications().add(ua);
                }
            }
        }

        // 5. Update Privileges (Module Wise)
        // We iterate the mapping request and attach privileges to the UserApplications we just created above.
        // 5. Update Privileges (Module Wise)
        if (request.getPrivilegeMapping() != null && user.getUserApplications() != null) {

            for (PrivilegeAssignRequest pm : request.getPrivilegeMapping()) {

                // A. Find the UserApplication
                UserApplication ua = user.getUserApplications().stream()
                        .filter(x -> x.getApplication().getId().equals(pm.getApplicationId()))
                        .findFirst()
                        .orElseThrow(() -> new CommonException("Application ID mismatch", HttpStatus.BAD_REQUEST));

                // Ensure the set is initialized
                if (ua.getModulePrivileges() == null) {
                    ua.setModulePrivileges(new HashSet<>());
                }

                // B. Identify the target privilege IDs for this specific Module
                Set<Long> requestPrivilegeIds = new HashSet<>(pm.getPrivilegeIds());

                // C. REMOVE privileges for this module that are NOT in the request
                // (We filter by Module ID first, so we don't accidentally delete privileges from OTHER modules)
                ua.getModulePrivileges().removeIf(ump ->
                        ump.getModule().getId().equals(pm.getModuleId()) &&
                                !requestPrivilegeIds.contains(ump.getPrivilege().getId())
                );

                // D. ADD privileges that are in the request but NOT in the DB
                // Get list of currently assigned privilege IDs for this module
                Set<Long> existingPrivilegeIds = ua.getModulePrivileges().stream()
                        .filter(ump -> ump.getModule().getId().equals(pm.getModuleId()))
                        .map(ump -> ump.getPrivilege().getId())
                        .collect(Collectors.toSet());

                // Find Module Entity (Fetch once)
                Module module = moduleRepository.findById(pm.getModuleId())
                        .orElseThrow(() -> new CommonException("Invalid module", HttpStatus.BAD_REQUEST));

                // Find Privilege Entities
                List<Privilege> allPrivileges = privilegeRepository.findAllById(pm.getPrivilegeIds());

                for (Privilege priv : allPrivileges) {
                    // Only add if it doesn't exist
                    if (!existingPrivilegeIds.contains(priv.getId())) {
                        UserModulePrivilege newUmp = UserModulePrivilege.builder()
                                .userApplication(ua)
                                .module(module)
                                .privilege(priv)
                                .isActive(true)
                                .build();

                        ua.getModulePrivileges().add(newUmp);
                    }
                }
            }
        }

        User savedUser = userRepository.save(user);

        return CommonResponse.builder()
                .id(savedUser.getId().toString())
                .message("User successfully updated")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() throws CommonException {
        log.info("Fetching all users");

        // Get tenant id from context
        Long tenantId = UserContextUtil.getTenantId();
        if (tenantId == null) {
            throw new CommonException("Tenant id missing in request", HttpStatus.UNAUTHORIZED);
        }

        List<User> users = userRepository.findByTenant_Id(tenantId);
        if (users.isEmpty()) {
            log.warn("No users found");
            return List.of();
        }

        return users.stream()
                .map(this::constructUserDto)
                .filter(Objects::nonNull)
                .toList();
    }


    @Transactional(readOnly = true)
    public UserDto getUser(Long userId) throws CommonException {
        log.info("Fetching  user by id : {}", userId);

        // Get tenant id from context
        Long tenantId = UserContextUtil.getTenantId();
        if (tenantId == null) {
            throw new CommonException("Tenant id missing in request", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("", HttpStatus.BAD_REQUEST));

        return constructUserDto(user);

    }

    @Transactional(readOnly = true)
    public UserEditResponse getUserForEdit(Long userId) throws CommonException {

        Long tenantId = UserContextUtil.getTenantId();
        if (tenantId == null) {
            throw new CommonException("Tenant id missing in request", HttpStatus.UNAUTHORIZED);
        }
        // 1. Fetch User with associations
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        // 2. Map Roles to List of IDs
        List<Long> roleIds = user.getUserRoles().stream()
                .filter(UserRole::getIsActive) // Optional: only active roles
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toList());

        // 3. Map Applications and their Nested Privileges
        List<UserEditResponse.UserAppEditDto> appDtos = user.getUserApplications().stream()
                .filter(UserApplication::getIsActive) // Only active apps
                .map(ua -> {

                    // Map nested Module Privileges
                    List<UserEditResponse.UserModulePrivilegeDto> privDtos = ua.getModulePrivileges().stream()
                            .filter(UserModulePrivilege::getIsActive)
                            .map(ump -> UserEditResponse.UserModulePrivilegeDto.builder()
                                    .moduleId(ump.getModule().getId())
                                    .privilegeId(ump.getPrivilege().getId())
                                    .build())
                            .collect(Collectors.toList());

                    return UserEditResponse.UserAppEditDto.builder()
                            .applicationId(ua.getApplication().getId())
                            .isActive(ua.getIsActive())
                            .modulePrivileges(privDtos)
                            .build();
                })
                .collect(Collectors.toList());

        // 4. Build Final Response
        return UserEditResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .roleIds(roleIds)
                .userApplications(appDtos)
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
                .roles(userRoles(user.getUserRoles()))
                .build();
    }

    private Set<String> userRoles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) return Set.of();

        return roles.stream()
                .map(ur -> ur.getRole().getRoleKey())
                .collect(Collectors.toSet());
    }

    private TenantDto constructTenantDto(Tenant tenant) {
        if (tenant == null) return null;

        return TenantDto.builder()
                .id(tenant.getId())
                .tenantUuid(tenant.getTenantUuid())
                .tenantName(tenant.getTenantName())
                .tenantCode(tenant.getTenantCode())
                .isActive(tenant.getIsActive())
                .build();
    }

    private Set<String> getPrivileges(Long moduleId, Long userApplicationId) {

        return userModulePrivilegeRepository.findPrivilegesOfUser(moduleId, userApplicationId)
                .stream()
                .map(Privilege::getPrivilegeKey)
                .collect(Collectors.toSet());
    }

    /**
     * Groups privileges by module key for a user application
     * Uses existing loaded data to avoid N+1 queries
     */
    private Map<String, Set<String>> groupModulePrivileges(UserApplication ua) {

        return ua.getModulePrivileges().stream()
                .filter(UserModulePrivilege::getIsActive) // Only active privileges
                .collect(Collectors.groupingBy(
                        ump -> ump.getModule().getModuleKey(), // Group by module key
                        Collectors.mapping(
                                ump -> ump.getPrivilege().getPrivilegeKey(), // Get privilege key
                                Collectors.toSet() // Collect as Set (automatically handles duplicates)
                        )
                ));
    }

}
