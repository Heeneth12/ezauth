package com.ezh.ezauth.tenant.service;


import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import com.ezh.ezauth.common.repository.ModuleRepository;
import com.ezh.ezauth.common.repository.RoleRepository;
import com.ezh.ezauth.subscription.entity.Subscription;
import com.ezh.ezauth.subscription.entity.SubscriptionPlan;
import com.ezh.ezauth.subscription.entity.SubscriptionStatus;
import com.ezh.ezauth.subscription.repository.SubscriptionPlanRepository;
import com.ezh.ezauth.subscription.repository.SubscriptionRepository;
import com.ezh.ezauth.tenant.dto.*;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.entity.TenantAddress;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.dto.UserDto;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserApplication;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import com.ezh.ezauth.user.entity.UserRole;
import com.ezh.ezauth.user.repository.UserApplicationRepository;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.repository.UserRoleRepository;
import com.ezh.ezauth.utils.EmailService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final EmailService emailService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;


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

        SubscriptionPlan defaultPlan = subscriptionPlanRepository.findByName("Free Trial")
                .orElseThrow(() -> new RuntimeException("Default subscription plan not found"));

        LocalDateTime now = LocalDateTime.now();

        //Create the Subscription object
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(defaultPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(now.plusDays(defaultPlan.getDurationDays()))
                .autoRenew(false)
                .build();

        subscription = subscriptionRepository.save(subscription);
        tenant.setCurrentSubscription(subscription);
        tenantRepository.save(tenant);

        //ADD ADDRESS (NEW LOGIC)
        if (request.getAddress() != null) {
            TenantAddress tenantAddress = TenantAddress.builder()
                    .tenant(tenant)
                    .addressLine1(request.getAddress().getAddressLine1())
                    .addressLine2(request.getAddress().getAddressLine2())
                    .route(request.getAddress().getRoute())
                    .area(request.getAddress().getArea())
                    .city(request.getAddress().getCity())
                    .state(request.getAddress().getState())
                    .country(request.getAddress().getCountry())
                    .pinCode(request.getAddress().getPinCode())
                    .addressType(request.getAddress().getType())
                    .build();

            // Initialize set if null and add address
            if (tenant.getAddresses() == null) {
                tenant.setAddresses(new HashSet<>());
            }
            tenant.getAddresses().add(tenantAddress);

            // Save tenant again to cascade the new address
            tenant = tenantRepository.save(tenant);
        }

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
        emailService.sendWelcomeEmail(request.getAdminEmail(), request.getTenantName());

        // 9. Return response
        return TenantRegistrationResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getTenantName())
                .tenantCode(tenant.getTenantCode())
                .adminUserId(adminUser.getId())
                .adminEmail(adminUser.getEmail())
                .message("Tenant registered successfully")
                .build();
    }

    @Transactional
    public CommonResponse updateTenant(Long tenantId, TenantRegistrationRequest request) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));

        if (request.getAdminPhone() != null && !request.getAdminPhone().isBlank()) {
            User admin = tenant.getTenantAdmin();
            if (admin != null) {
                admin.setPhone(request.getAdminPhone());
                userRepository.save(admin);
            }
        }

        if (request.getAddress() != null) {
            handleAddressUpdate(tenant, request.getAddress());
        }

        tenantRepository.save(tenant);

        return CommonResponse
                .builder()
                .status(Status.SUCCESS)
                .message("Tenant successfully updated")
                .build();
    }

    @Transactional(readOnly = true)
    public Page<TenantDto> getTenants(Integer page, Integer size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Tenant> tenants = tenantRepository.findAll(pageable);

        return tenants.map(this::dtoConstructor);
    }


    @Transactional(readOnly = true)
    public TenantDto getTenantById(Long tenantId) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found with ID: " + tenantId));

        return dtoConstructor(tenant);
    }

    @Transactional(readOnly = true)
    public Map<Long, TenantDto> getTenantsByIds(List<Long> tenantIds) {

        if (tenantIds == null || tenantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> uniqueIds = new HashSet<>(tenantIds);

        log.info("Fetching bulk data for {} unique tenant IDs", uniqueIds.size());

        List<Tenant> tenants = tenantRepository.findByIdIn(uniqueIds.stream().toList());

        return tenants.stream()
                .map(this::dtoConstructor)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        TenantDto::getId,
                        Function.identity(),
                        (existing, duplicate) -> existing
                ));
    }

    @Transactional
    public User registerGoogleTenant(String email, String fullName, String pictureUrl, String appKey) {

        if (userRepository.existsByEmail(email)) {
            return userRepository.findByEmail(email).orElseThrow();
        }

        //Default App Key if missing (Adjust "EZH_CORE" to your actual default app key)
        String targetAppKey = (appKey != null && !appKey.isEmpty()) ? appKey : "EZH_INV_001";

        Application app = applicationRepository.findByAppKey(targetAppKey)
                .orElseThrow(() -> new RuntimeException("Invalid application key for registration"));

        // 3. Generate Codes
        String tenantCode = generateTenantCode(fullName);
        if (tenantRepository.existsByTenantCode(tenantCode)) {
            tenantCode = tenantCode + "-" + System.currentTimeMillis();
        }

        // 4. Create Tenant
        Tenant tenant = Tenant.builder()
                .tenantName(fullName + "'s Workspace")
                .tenantCode(tenantCode)
                .applications(Set.of(app))
                .isPersonal(true)
                .isActive(true)
                .build();
        tenant = tenantRepository.save(tenant);

        SubscriptionPlan defaultPlan = subscriptionPlanRepository.findByName("Free Trial")
                .orElseThrow(() -> new RuntimeException("Default subscription plan not found"));

        LocalDateTime now = LocalDateTime.now();
        //Create the Subscription object
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(defaultPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(now.plusDays(defaultPlan.getDurationDays()))
                .autoRenew(false)
                .build();

        subscription = subscriptionRepository.save(subscription);
        tenant.setCurrentSubscription(subscription);

        // 5. Create User (No Password)
        User adminUser = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode("GOOGLE_AUTH_USER"))
                .isActive(true)
                .tenant(tenant)
                .build();
        adminUser = userRepository.save(adminUser);

        //Setup Access (UserApplication)
        UserApplication userApplication = UserApplication.builder()
                .user(adminUser)
                .application(app)
                .isActive(true)
                .build();
        userApplicationRepository.save(userApplication);
        adminUser.setUserApplications(Set.of(userApplication));

        //Assign Tenant Admin
        tenant.setTenantAdmin(adminUser);
        tenantRepository.save(tenant);

        //Assign Privileges (Full Access for Personal Tenant)
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

        //Create & Assign Super Admin Role
        Role superAdminRole = Role.builder()
                .roleName("Super Admin")
                .roleKey("SUPER_ADMIN")
                .description("System generated admin role")
                .tenant(tenant)
                .isActive(true)
                .isSystemRole(true)
                .build();
        superAdminRole = roleRepository.save(superAdminRole);

        UserRole userRole = UserRole.builder()
                .user(adminUser)
                .role(superAdminRole)
                .isActive(true)
                .build();
        userRoleRepository.save(userRole);
        emailService.sendWelcomeEmail(email, fullName);
        log.info("Successfully auto-registered Google user: {}", email);
        return adminUser;
    }

    // Helper method to generate tenant code
    private String generateTenantCode(String tenantName) {
        return tenantName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "")
                .substring(0, Math.min(tenantName.length(), 6));
    }


    private TenantDto dtoConstructor(Tenant tenant) {

        if (tenant == null) {
            return null;
        }

        // Map TenantAddress Entity -> TenantAddressDto
        Set<TenantAddressDto> addressDtos = null;
        if (tenant.getAddresses() != null && !tenant.getAddresses().isEmpty()) {
            addressDtos = tenant.getAddresses().stream()
                    .map(addr -> TenantAddressDto.builder()
                            .id(addr.getId())
                            .addressLine1(addr.getAddressLine1())
                            .addressLine2(addr.getAddressLine2())
                            .route(addr.getRoute())
                            .area(addr.getArea())
                            .city(addr.getCity())
                            .state(addr.getState())
                            .country(addr.getCountry())
                            .pinCode(addr.getPinCode())
                            .type(addr.getAddressType())
                            .build())
                    .collect(Collectors.toSet());
        }

        // Map Tenant Admin (User Entity -> UserDto)
        UserDto adminDto = null;
        if (tenant.getTenantAdmin() != null) {
            adminDto = UserDto.builder()
                    .id(tenant.getTenantAdmin().getId())
                    .userUuid(tenant.getTenantAdmin().getUserUuid())
                    .fullName(tenant.getTenantAdmin().getFullName())
                    .email(tenant.getTenantAdmin().getEmail())
                    .phone(tenant.getTenantAdmin().getPhone())
                    .isActive(tenant.getTenantAdmin().getIsActive())
                    .build();
        }

        // Map Applications (Application Entity -> ApplicationDto)
        Set<ApplicationDto> applicationDtos = null;
        if (tenant.getApplications() != null && !tenant.getApplications().isEmpty()) {
            applicationDtos = tenant.getApplications().stream()
                    .map(app -> ApplicationDto.builder()
                            .id(app.getId())
                            .appName(app.getAppName())
                            .appKey(app.getAppKey())
                            .description(app.getDescription())
                            .isActive(app.getIsActive())
                            .build())
                    .collect(Collectors.toSet());
        }

        // Build and Return TenantDto
        return TenantDto.builder()
                .id(tenant.getId())
                .tenantUuid(tenant.getTenantUuid())
                .tenantName(tenant.getTenantName())
                .tenantCode(tenant.getTenantCode())
                .email(adminDto != null ? adminDto.getEmail() : null)
                .phone(adminDto != null ? adminDto.getPhone() : null)
                .isActive(tenant.getIsActive())
                .tenantAdmin(adminDto)
                .applications(applicationDtos)
                .tenantAddress(addressDtos)
                .build();
    }


    /**
     * Helper to handle Address Upsert (Update if exists, Insert if new)
     */
    private void handleAddressUpdate(Tenant tenant, TenantAddressDto addressDto) {
        if (tenant.getAddresses() == null) {
            tenant.setAddresses(new HashSet<>());
        }

        // Logic: Try to find an address to update
        // Priority 1: Find by ID (if provided)
        // Priority 2: Find by Address Type (if no ID provided, e.g., update the "OFFICE" address)
        TenantAddress existingAddress = tenant.getAddresses().stream()
                .filter(a -> (addressDto.getId() != null && a.getId().equals(addressDto.getId())) ||
                        (a.getAddressType() == addressDto.getType()))
                .findFirst()
                .orElse(null);

        if (existingAddress != null) {
            // UPDATE existing address
            existingAddress.setAddressLine1(addressDto.getAddressLine1());
            existingAddress.setAddressLine2(addressDto.getAddressLine2());
            existingAddress.setCity(addressDto.getCity());
            existingAddress.setState(addressDto.getState());
            existingAddress.setCountry(addressDto.getCountry());
            existingAddress.setPinCode(addressDto.getPinCode());
            // We don't change the Type if we found it by Type, but safe to set it
            existingAddress.setAddressType(addressDto.getType());
        } else {
            // CREATE new address
            TenantAddress newAddress = TenantAddress.builder()
                    .tenant(tenant)
                    .addressLine1(addressDto.getAddressLine1())
                    .addressLine2(addressDto.getAddressLine2())
                    .city(addressDto.getCity())
                    .state(addressDto.getState())
                    .country(addressDto.getCountry())
                    .pinCode(addressDto.getPinCode())
                    .addressType(addressDto.getType())
                    .build();

            tenant.getAddresses().add(newAddress);
        }
    }
}