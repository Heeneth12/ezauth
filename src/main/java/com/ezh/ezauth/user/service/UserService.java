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
import com.ezh.ezauth.tenant.dto.TenantRegistrationRequest;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.dto.*;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserApplication;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import com.ezh.ezauth.user.entity.UserRole;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
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

        Tenant tenant = tenantRepository.findById(request.getTenantId())
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

                // Find the application assigned to user
                UserApplication ua = userApplications.stream()
                        .filter(x -> x.getApplication().getId().equals(pm.getApplicationId()))
                        .findFirst()
                        .orElseThrow(() -> new CommonException("Application not assigned to user", HttpStatus.BAD_REQUEST));

                // Find module
                Module module = moduleRepository.findById(pm.getModuleId())
                        .orElseThrow(() -> new CommonException("Invalid module", HttpStatus.BAD_REQUEST));

                // Fetch privilege entities
                List<Privilege> privileges = privilegeRepository.findAllById(pm.getPrivilegeIds());

                Set<UserModulePrivilege> modulePrivileges = privileges.stream()
                        .map(p -> UserModulePrivilege.builder()
                                .userApplication(ua)
                                .module(module)
                                .privilege(p)
                                .isActive(true)
                                .build()
                        ).collect(Collectors.toSet());

                ua.setModulePrivileges(modulePrivileges);
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


    public List<UserDto> getAllUsers() throws CommonException {
        log.info("Fetching all users");

        List<User> users = userRepository.findAll();
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
                .tenant(constructTenantDto(user.getTenant()))
                .build();
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
