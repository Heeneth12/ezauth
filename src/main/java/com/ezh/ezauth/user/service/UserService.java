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

    @Transactional(readOnly = true)
    public UserInitResponse getUserInitDetails(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // map userApplications → DTO
        Set<UserApplicationDto> applicationDtos = user.getUserApplications().stream()
                .map(ua -> UserApplicationDto.builder()
                        .id(ua.getId())
                        .appKey(ua.getApplication().getAppKey())
                        .appName(ua.getApplication().getAppName())
                        .isActive(ua.getIsActive())
                        .modulePrivileges(groupModulePrivileges(ua))   // ✔ FIXED HERE
                        .build()
                ).collect(Collectors.toSet());

        // map userRoles → DTO
        Set<String> roles = new HashSet<>(Set.of("TEST", "TEST_1"));

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

        // 3. Update Roles (Clear existing -> Add new)
        if (request.getRoleIds() != null) {
            // Clear existing roles. Thanks to orphanRemoval=true, these are deleted from DB.
            if (user.getUserRoles() != null) {
                user.getUserRoles().clear();
            } else {
                user.setUserRoles(new HashSet<>());
            }

            if (!request.getRoleIds().isEmpty()) {
                Set<UserRole> newRoles = request.getRoleIds().stream()
                        .map(roleId -> {
                            Role role = roleRepository.findById(roleId)
                                    .orElseThrow(() -> new CommonException("Invalid role ID: " + roleId, HttpStatus.BAD_REQUEST));
                            return UserRole.builder()
                                    .role(role)
                                    .user(user) // Link back to existing user
                                    .build();
                        })
                        .collect(Collectors.toSet());
                user.getUserRoles().addAll(newRoles);
            }
        }

        // 4. Update Applications & Privileges
        // Strategy: We clear all existing applications and rebuild them based on the request.
        // This ensures that any application NOT in the request is removed.

        if (request.getApplicationIds() != null) {
            // Clear existing apps (and their child privileges due to cascade)
            if (user.getUserApplications() != null) {
                user.getUserApplications().clear();
            } else {
                user.setUserApplications(new HashSet<>());
            }

            List<Application> apps = applicationRepository.findAllById(request.getApplicationIds());

            // Create the new UserApplication objects
            for (Application app : apps) {
                UserApplication ua = UserApplication.builder()
                        .user(user)
                        .application(app)
                        .isActive(true)
                        .modulePrivileges(new HashSet<>()) // Initialize the Set immediately
                        .build();

                user.getUserApplications().add(ua);
            }
        }

        // 5. Update Privileges (Module Wise)
        // We iterate the mapping request and attach privileges to the UserApplications we just created above.
        if (request.getPrivilegeMapping() != null && user.getUserApplications() != null) {

            for (PrivilegeAssignRequest pm : request.getPrivilegeMapping()) {

                // Find the UserApplication object we just added to the user list above
                UserApplication ua = user.getUserApplications().stream()
                        .filter(x -> x.getApplication().getId().equals(pm.getApplicationId()))
                        .findFirst()
                        .orElseThrow(() -> new CommonException("Application ID " + pm.getApplicationId() + " is in privilege mapping but not in assigned application list", HttpStatus.BAD_REQUEST));

                // Find module
                Module module = moduleRepository.findById(pm.getModuleId())
                        .orElseThrow(() -> new CommonException("Invalid module", HttpStatus.BAD_REQUEST));

                // Fetch privilege entities
                List<Privilege> privileges = privilegeRepository.findAllById(pm.getPrivilegeIds());

                // Build the new privilege links
                Set<UserModulePrivilege> newModulePrivileges = privileges.stream()
                        .map(p -> UserModulePrivilege.builder()
                                .userApplication(ua)
                                .module(module)
                                .privilege(p)
                                .isActive(true)
                                .build()
                        ).collect(Collectors.toSet());

                // Add to the existing set (This is the fix from previous step)
                ua.getModulePrivileges().addAll(newModulePrivileges);
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

    private Map<String, Set<String>> groupModulePrivileges(UserApplication ua) {

        Map<String, Set<String>> result = new HashMap<>();

        ua.getModulePrivileges().forEach(ump -> {
            String moduleKey = ump.getModule().getModuleKey();

            // Fetch all privileges for this module & userApplication
            Set<String> privileges = getPrivileges(
                    ump.getModule().getId(),
                    ump.getUserApplication().getId()
            );

            // Add to map (merge duplicates automatically)
            result.computeIfAbsent(moduleKey, k -> new HashSet<>())
                    .addAll(privileges);
        });

        return result;
    }


}
