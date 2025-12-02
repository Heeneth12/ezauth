package com.ezh.ezauth.tenant.service;


import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import com.ezh.ezauth.common.repository.ModuleRepository;
import com.ezh.ezauth.common.repository.RoleRepository;
import com.ezh.ezauth.security.JwtTokenProvider;
import com.ezh.ezauth.tenant.dto.TenantRegistrationRequest;
import com.ezh.ezauth.tenant.dto.TenantRegistrationResponse;
import com.ezh.ezauth.tenant.dto.TenantSignInRequest;
import com.ezh.ezauth.tenant.dto.TenantSignInResponse;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserApplication;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import com.ezh.ezauth.user.entity.UserRole;
import com.ezh.ezauth.user.repository.UserApplicationRepository;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationRepository applicationRepository;
    private final UserApplicationRepository userApplicationRepository;
    private final UserModulePrivilegeRepository userModulePrivilegeRepository;
    private final ModuleRepository moduleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;


    @Transactional
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {

        // 1. Validate email doesn't already exist
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // 2. Generate unique tenant code
        String tenantCode = generateTenantCode(request.getTenantName());
        if (tenantRepository.existsByTenantCode(tenantCode)) {
            tenantCode = tenantCode + "-" + System.currentTimeMillis();
        }

        Application app = applicationRepository.findByAppKey(request.getAppKey())
                .orElseThrow(() -> new RuntimeException("Invalid application"));

        // 3. Create Tenant
        Tenant tenant = Tenant.builder()
                .tenantName(request.getTenantName())
                .tenantCode(tenantCode)
                .applications(Set.of(app))
                .isPersonal(request.getIsPersonal())
                .isActive(true)
                .build();

        tenant = tenantRepository.save(tenant);

        // 4. Create Admin User
        User adminUser = User.builder()
                .fullName(request.getAdminFullName())
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getAdminPhone())
                .isActive(true)
                .tenant(tenant)
                .build();

        adminUser = userRepository.save(adminUser);

        // setting user application where it give access modules and preivlages
        UserApplication userApplication = UserApplication.builder()
                .user(adminUser)
                .application(app)
                .isActive(true)
                .build();
        adminUser.setUserApplications(Set.of(userApplication));

        userApplicationRepository.save(userApplication);

        // 5. Set tenant admin
        tenant.setTenantAdmin(adminUser);
        tenant = tenantRepository.save(tenant);


        // Give Super Admin full access to all modules & privileges of this application
        List<Module> modules = moduleRepository.findByApplicationId(app.getId());
        for (Module module : modules) {
            for (Privilege privilege : module.getPrivileges()) {
                UserModulePrivilege ump = UserModulePrivilege.builder()
                        .userApplication(userApplication)
                        .module(module)
                        .privilege(privilege)
                        .isActive(true)
                        .build();
                userModulePrivilegeRepository.save(ump);
            }
        }


        // 6. Create SUPER_ADMIN role for this tenant
        Role superAdminRole = Role.builder()
                .roleName("Super Admin")
                .roleKey("SUPER_ADMIN")
                .description("Full access to all applications and modules")
                .tenant(tenant)
                .isActive(true)
                .isSystemRole(true)
                .build();

        superAdminRole = roleRepository.save(superAdminRole);


        // 7. Assign SUPER_ADMIN role to admin user
        UserRole userRole = UserRole.builder()
                .user(adminUser)
                .role(superAdminRole)
                .isActive(true)
                .build();

        userRoleRepository.save(userRole);

        // 8. Generate JWT token for immediate login
        String token = jwtTokenProvider.generateToken(adminUser.getUserUuid(),
                adminUser.getEmail(),
                tenant.getTenantUuid());

        // 9. Return response
        return TenantRegistrationResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getTenantName())
                .tenantCode(tenant.getTenantCode())
                .adminUserId(adminUser.getId())
                .adminEmail(adminUser.getEmail())
                .message("Tenant registered successfully")
                .token(token)
                .build();
    }

    @Transactional(readOnly = true)
    public TenantSignInResponse signIn(TenantSignInRequest request) {

        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // 2. Check if user is active
        if (!user.getIsActive()) {
            throw new RuntimeException("User account is inactive");
        }

        // 3. Check if tenant is active
        if (!user.getTenant().getIsActive()) {
            throw new RuntimeException("Tenant account is inactive");
        }

        // 4. Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // 5. If tenantCode is provided, validate it
        if (request.getTenantCode() != null &&
                !request.getTenantCode().isEmpty() &&
                !user.getTenant().getTenantCode().equals(request.getTenantCode())) {
            throw new RuntimeException("Invalid tenant code");
        }

        // 6. Check if user is tenant admin
        Boolean isAdmin = user.getTenant().getTenantAdmin() != null &&
                user.getTenant().getTenantAdmin().getId().equals(user.getId());

        // 7. Generate JWT token
        String token = jwtTokenProvider.generateToken(user.getUserUuid(),
                user.getEmail(),
                user.getTenant().getTenantUuid());

        // 8. Return response
        return TenantSignInResponse.builder()
                .token(token)
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .tenantId(user.getTenant().getId())
                .tenantName(user.getTenant().getTenantName())
                .tenantCode(user.getTenant().getTenantCode())
                .isAdmin(isAdmin)
                .message("Sign in successful")
                .build();
    }

    // Helper method to generate tenant code
    private String generateTenantCode(String tenantName) {
        return tenantName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "")
                .substring(0, Math.min(tenantName.length(), 6));
    }

}
