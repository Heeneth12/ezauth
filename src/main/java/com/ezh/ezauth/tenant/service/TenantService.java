package com.ezh.ezauth.tenant.service;


import com.ezh.ezauth.auth.dto.AuthResponse;
import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import com.ezh.ezauth.common.repository.ModuleRepository;
import com.ezh.ezauth.common.repository.RoleRepository;
import com.ezh.ezauth.security.JwtTokenProvider;
import com.ezh.ezauth.subscription.entity.Subscription;
import com.ezh.ezauth.subscription.entity.SubscriptionPlan;
import com.ezh.ezauth.subscription.entity.SubscriptionStatus;
import com.ezh.ezauth.subscription.repository.SubscriptionPlanRepository;
import com.ezh.ezauth.subscription.repository.SubscriptionRepository;
import com.ezh.ezauth.tenant.dto.*;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.entity.TenantAddress;
import com.ezh.ezauth.tenant.entity.TenantBranch;
import com.ezh.ezauth.tenant.entity.TenantDetails;
import com.ezh.ezauth.tenant.repository.TenantBranchRepository;
import com.ezh.ezauth.tenant.repository.TenantDetailsRepository;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.dto.UserDto;
import com.ezh.ezauth.user.entity.*;
import com.ezh.ezauth.user.repository.UserApplicationRepository;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.user.repository.UserRoleRepository;
import com.ezh.ezauth.utils.EmailService;
import com.ezh.ezauth.utils.common.CommonResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
    private final TenantDetailsRepository detailsRepository;
    private final TenantBranchRepository tenantBranchRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final CacheManager cacheManager;


    @Transactional
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {

        // 1. Validate email doesn't already exist
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new CommonException("Email already registered", HttpStatus.CONFLICT);
        }

        // 2. Generate unique tenant code
        String tenantCode = generateTenantCode(request.getTenantName());
        if (tenantRepository.existsByTenantCode(tenantCode)) {
            tenantCode = tenantCode + "-" + System.currentTimeMillis();
        }

        Application app = applicationRepository.findByAppKey(request.getAppKey())
                .orElseThrow(() -> new CommonException("Invalid application key", HttpStatus.BAD_REQUEST));

        // 3. Create Tenant
        Tenant tenant = Tenant.builder()
                .tenantName(request.getTenantName())
                .tenantCode(tenantCode)
                .applications(Set.of(app))
                .isPersonal(request.getIsPersonal())
                .isActive(true)
                .isVerify(false)
                .build();

        tenant = tenantRepository.save(tenant);

        TenantDetails tenantDetails = TenantDetails.builder()
                .tenant(tenant)
                .businessType(request.getBusinessType())
                .baseCurrency("INR")
                .timeZone("Asia/Kolkata")
                .legalName(request.getTenantName())
                .build();

        tenant.setTenantDetails(tenantDetails);

        SubscriptionPlan defaultPlan = subscriptionPlanRepository.findByName("Free Trial")
                .orElseThrow(() -> new CommonException("Default subscription plan not found. Contact support.", HttpStatus.INTERNAL_SERVER_ERROR));

        LocalDateTime now = LocalDateTime.now();

        //Create the Subscription object
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(defaultPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .isPrimary(true)
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

        TenantBranch defaultBranch = createDefaultBranch(tenant, request.getAddress());

        // 4. Create Admin User
        User adminUser = User.builder()
                .fullName(request.getAdminFullName())
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getAdminPhone())
                .userType(UserType.SUPER_ADMIN)
                .isActive(true)
                .tenant(tenant)
                .branch(defaultBranch)
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

        String otp = String.format("%06d", new Random().nextInt(999999));
        Cache otpCacheRef = cacheManager.getCache("otpCache");
        if (otpCacheRef != null) {
            otpCacheRef.put("otp:tenant:" + tenant.getId(), otp);
        }
        emailService.sendOtpEmail(adminUser.getEmail(), otp);

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
    public AuthResponse verifyTenantEmail(Long tenantId, String otp) throws CommonException {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(tenant.getIsVerify())) {
            throw new CommonException("Tenant already verified", HttpStatus.CONFLICT);
        }

        Cache otpCacheRef = cacheManager.getCache("otpCache");
        String cachedOtp = otpCacheRef != null
                ? otpCacheRef.get("otp:tenant:" + tenantId, String.class)
                : null;

        if (cachedOtp == null) {
            throw new CommonException("OTP has expired or was not found. Please request a new OTP.", HttpStatus.GONE);
        }

        if (!cachedOtp.equals(otp)) {
            throw new CommonException("Invalid OTP", HttpStatus.BAD_REQUEST);
        }

        if (otpCacheRef != null) {
            otpCacheRef.evict("otp:tenant:" + tenantId);
        }

        tenant.setIsVerify(true);
        tenantRepository.save(tenant);

        User user = userRepository.findByEmail(tenant.getTenantAdmin().getEmail())
                .orElseThrow(() -> new CommonException("Admin user not found", HttpStatus.NOT_FOUND));

        String roles = extractUserRoles(user);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUserUuid(),
                user.getEmail(),
                user.getTenant().getId(),
                user.getTenant().getTenantUuid(),
                user.getBranch() != null ? user.getBranch().getId() : null,
                user.getUserType().name(),
                roles
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .message("Email verified successfully")
                .build();
    }

    /**
     * Extract active user roles as comma-separated string
     *
     * @param user User entity with roles
     * @return Comma-separated role keys (e.g., "ADMIN,VIEWER") or empty string
     */
    private String extractUserRoles(User user) {
        if (user.getUserRoles() == null) {
            return "";
        }
        return user.getUserRoles().stream()
                .filter(UserRole::getIsActive)
                .map(ur -> ur.getRole().getRoleKey())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    @Transactional(readOnly = true)
    public CommonResponse resendOtp(Long tenantId) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(tenant.getIsVerify())) {
            throw new CommonException("Tenant is already verified", HttpStatus.CONFLICT);
        }

        User admin = userRepository.findByEmail(tenant.getTenantAdmin().getEmail())
                .orElseThrow(() -> new CommonException("Admin user not found", HttpStatus.NOT_FOUND));

        String otp = String.format("%06d", new Random().nextInt(999999));
        Cache otpCacheRef = cacheManager.getCache("otpCache");
        if (otpCacheRef != null) {
            otpCacheRef.put("otp:tenant:" + tenantId, otp);
        }
        emailService.sendOtpEmail(admin.getEmail(), otp);

        return CommonResponse.builder()
                .status(Status.SUCCESS)
                .message("OTP resent successfully")
                .build();
    }

    @Transactional
    public CommonResponse toggleTenantStatus(Long tenantId) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        boolean deactivating = Boolean.TRUE.equals(tenant.getIsActive());
        if (deactivating) {
            long activeUserCount = userRepository.countByTenant_IdAndIsActive(tenantId, true);
            if (activeUserCount > 0) {
                throw new CommonException(
                        "Cannot deactivate tenant with " + activeUserCount + " active user(s). Deactivate all users first.",
                        HttpStatus.CONFLICT);
            }
        }

        tenant.setIsActive(!tenant.getIsActive());
        tenantRepository.save(tenant);

        String statusLabel = Boolean.TRUE.equals(tenant.getIsActive()) ? "Active" : "Inactive";
        return CommonResponse.builder()
                .id(tenantId.toString())
                .status(Status.SUCCESS)
                .message("Tenant status toggled. Current status: " + statusLabel)
                .build();
    }

    @Transactional
    public CommonResponse deleteTenantAddress(Long tenantId, Long addressId) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (tenant.getAddresses() == null || tenant.getAddresses().isEmpty()) {
            throw new CommonException("No addresses found for this tenant", HttpStatus.NOT_FOUND);
        }

        TenantAddress addressToDelete = tenant.getAddresses().stream()
                .filter(a -> a.getId() != null && a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new CommonException("Address not found for this tenant", HttpStatus.NOT_FOUND));

        tenant.getAddresses().remove(addressToDelete);
        tenantRepository.save(tenant);

        return CommonResponse.builder()
                .id(addressId.toString())
                .status(Status.SUCCESS)
                .message("Tenant address deleted successfully")
                .build();
    }

    @Transactional
    public CommonResponse createTenantBranch(Long tenantId, TenantBranchDto dto) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        String branchCode = generateBranchCode(tenant, dto);
        if (tenantBranchRepository.existsByTenant_IdAndBranchCode(tenantId, branchCode)) {
            throw new CommonException("Branch code already exists for this tenant", HttpStatus.CONFLICT);
        }
        if (tenantBranchRepository.existsByTenant_IdAndBranchNameIgnoreCase(tenantId, dto.getBranchName())) {
            throw new CommonException("Branch name already exists for this tenant", HttpStatus.CONFLICT);
        }

        TenantBranch branch = mapDtoToBranch(dto);
        branch.setTenant(tenant);
        branch.setBranchCode(branchCode);
        if (branch.getIsActive() == null) {
            branch.setIsActive(true);
        }

        TenantBranch saved = tenantBranchRepository.save(branch);
        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Tenant branch successfully created")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TenantBranchDto> getTenantBranches(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new CommonException("Tenant not found", HttpStatus.NOT_FOUND);
        }
        return tenantBranchRepository.findByTenant_IdOrderByBranchNameAsc(tenantId).stream()
                .map(this::mapBranchToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantBranchDto getTenantBranch(Long tenantId, Long branchId) {
        return tenantBranchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .map(this::mapBranchToDto)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public CommonResponse updateTenantBranch(Long tenantId, Long branchId, TenantBranchDto dto) {
        TenantBranch branch = tenantBranchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        if (!branch.getBranchName().equalsIgnoreCase(dto.getBranchName())
                && tenantBranchRepository.existsByTenant_IdAndBranchNameIgnoreCase(tenantId, dto.getBranchName())) {
            throw new CommonException("Branch name already exists for this tenant", HttpStatus.CONFLICT);
        }

        updateBranchFromDto(branch, dto);
        TenantBranch saved = tenantBranchRepository.save(branch);
        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Tenant branch successfully updated")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse toggleTenantBranchStatus(Long tenantId, Long branchId) {
        TenantBranch branch = tenantBranchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));
        branch.setIsActive(!Boolean.TRUE.equals(branch.getIsActive()));
        tenantBranchRepository.save(branch);
        return CommonResponse.builder()
                .id(branch.getId().toString())
                .message("Tenant branch status toggled successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse updateTenant(Long tenantId, TenantRegistrationRequest request) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

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
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

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
                .orElseThrow(() -> new CommonException("Invalid application key", HttpStatus.BAD_REQUEST));

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
                .orElseThrow(() -> new CommonException("Default subscription plan not found. Contact support.", HttpStatus.INTERNAL_SERVER_ERROR));

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

        TenantBranch defaultBranch = createDefaultBranch(tenant, null);

        // 5. Create User (No Password)
        User adminUser = User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash(passwordEncoder.encode("GOOGLE_AUTH_USER"))
                .isActive(true)
                .tenant(tenant)
                .branch(defaultBranch)
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


    @Transactional
    public CommonResponse createTenantDetails(Long tenantId, TenantDetailsDto dto) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        // Check if details already exist to prevent duplicates
        if (tenant.getTenantDetails() != null) {
            throw new CommonException("Business details already exist for tenant: " + tenant.getTenantName(), HttpStatus.CONFLICT);
        }

        TenantDetails details = mapDtoToEntity(dto);
        details.setTenant(tenant);

        TenantDetails saved = detailsRepository.save(details);

        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Business details successfully initialized for tenant: " + tenant.getTenantName())
                .status(Status.SUCCESS)
                .build();
    }


    @Transactional
    public CommonResponse updateTenantDetails(Long tenantId, TenantDetailsDto dto) throws CommonException {
        TenantDetails details = detailsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new CommonException("Business details not found for the requested tenant", HttpStatus.NOT_FOUND));

        // Update fields
        details.setLegalName(dto.getLegalName());
        details.setBusinessType(dto.getBusinessType());
        details.setBaseCurrency(dto.getBaseCurrency());
        details.setTimeZone(dto.getTimeZone());
        details.setGstNumber(dto.getGstNumber());
        details.setPanNumber(dto.getPanNumber());
        details.setSupportEmail(dto.getSupportEmail());
        details.setContactPhone(dto.getContactPhone());
        details.setWebsite(dto.getWebsite());
        details.setLogoUrl(dto.getLogoUrl());

        TenantDetails updated = detailsRepository.save(details);

        return CommonResponse.builder()
                .id(updated.getId().toString())
                .message("Business profile for '" + updated.getLegalName() + "' has been updated successfully.")
                .status(Status.SUCCESS)
                .build();
    }


    @Transactional(readOnly = true)
    public TenantDetailsDto getTenantDetailsByTenantId(Long tenantId) throws CommonException {
        // We fetch by tenantId directly using the repository method
        return detailsRepository.findByTenantId(tenantId)
                .map(this::mapEntityToDto)
                .orElseThrow(() -> new CommonException("No business details found for Tenant ID: " + tenantId, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public CommonResponse createTenantAddress(Long tenantId, TenantAddressDto dto) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (tenant.getAddresses() == null) {
            tenant.setAddresses(new HashSet<>());
        }

        boolean addressTypeExists = tenant.getAddresses().stream()
                .anyMatch(address -> address.getAddressType() == dto.getType());

        if (addressTypeExists) {
            throw new CommonException("Address already exists for type: " + dto.getType(), HttpStatus.CONFLICT);
        }

        TenantAddress address = TenantAddress.builder()
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

        tenant.getAddresses().add(address);
        tenantRepository.save(tenant);

        return CommonResponse.builder()
                .id(String.valueOf(address.getId()))
                .message("Tenant address created successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse updateTenantAddress(Long tenantId, Long addressId, TenantAddressDto dto) throws CommonException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (tenant.getAddresses() == null || tenant.getAddresses().isEmpty()) {
            throw new CommonException("No addresses found for this tenant", HttpStatus.NOT_FOUND);
        }

        TenantAddress address = tenant.getAddresses().stream()
                .filter(item -> item.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new CommonException("Address not found for this tenant", HttpStatus.NOT_FOUND));

        boolean duplicateType = tenant.getAddresses().stream()
                .filter(item -> !item.getId().equals(addressId))
                .anyMatch(item -> item.getAddressType() == dto.getType());

        if (duplicateType) {
            throw new CommonException("Address already exists for type: " + dto.getType(), HttpStatus.CONFLICT);
        }

        address.setAddressLine1(dto.getAddressLine1());
        address.setAddressLine2(dto.getAddressLine2());
        address.setRoute(dto.getRoute());
        address.setArea(dto.getArea());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setCountry(dto.getCountry());
        address.setPinCode(dto.getPinCode());
        address.setAddressType(dto.getType());

        tenantRepository.save(tenant);

        return CommonResponse.builder()
                .id(String.valueOf(address.getId()))
                .message("Tenant address updated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    // Helper method to generate tenant code
    private String generateTenantCode(String tenantName) {
        String sanitized = tenantName.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (sanitized.isEmpty()) sanitized = "TENANT";
        return sanitized.substring(0, Math.min(sanitized.length(), 6));
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

        Set<TenantBranchDto> branchDtos = null;
        if (tenant.getBranches() != null && !tenant.getBranches().isEmpty()) {
            branchDtos = tenant.getBranches().stream()
                    .map(this::mapBranchToDto)
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
                .branches(branchDtos)
                .tenantDetails(tenant.getTenantDetails() != null ? mapEntityToDto(tenant.getTenantDetails()) : null)
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


    private TenantBranch createDefaultBranch(Tenant tenant, TenantAddressDto addressDto) {
        TenantBranch branch = TenantBranch.builder()
                .tenant(tenant)
                .branchName("Main Branch")
                .branchCode(tenant.getTenantCode() + "-MAIN")
                .contactPhone(tenant.getTenantAdmin() != null ? tenant.getTenantAdmin().getPhone() : null)
                .isActive(true)
                .build();
        if (addressDto != null) {
            branch.setAddressLine1(addressDto.getAddressLine1());
            branch.setAddressLine2(addressDto.getAddressLine2());
            branch.setRoute(addressDto.getRoute());
            branch.setArea(addressDto.getArea());
            branch.setCity(addressDto.getCity());
            branch.setState(addressDto.getState());
            branch.setCountry(addressDto.getCountry());
            branch.setPinCode(addressDto.getPinCode());
        }
        return tenantBranchRepository.save(branch);
    }

    private TenantBranch mapDtoToBranch(TenantBranchDto dto) {
        TenantBranch branch = TenantBranch.builder().build();
        updateBranchFromDto(branch, dto);
        return branch;
    }

    private void updateBranchFromDto(TenantBranch branch, TenantBranchDto dto) {
        branch.setBranchName(dto.getBranchName());
        branch.setDescription(dto.getDescription());
        branch.setContactEmail(dto.getContactEmail());
        branch.setContactPhone(dto.getContactPhone());
        branch.setAddressLine1(dto.getAddressLine1());
        branch.setAddressLine2(dto.getAddressLine2());
        branch.setRoute(dto.getRoute());
        branch.setArea(dto.getArea());
        branch.setCity(dto.getCity());
        branch.setState(dto.getState());
        branch.setCountry(dto.getCountry());
        branch.setPinCode(dto.getPinCode());
        if (dto.getIsActive() != null) {
            branch.setIsActive(dto.getIsActive());
        }
    }

    private TenantBranchDto mapBranchToDto(TenantBranch branch) {
        return TenantBranchDto.builder()
                .id(branch.getId())
                .branchUuid(branch.getBranchUuid())
                .branchName(branch.getBranchName())
                .branchCode(branch.getBranchCode())
                .description(branch.getDescription())
                .contactEmail(branch.getContactEmail())
                .contactPhone(branch.getContactPhone())
                .addressLine1(branch.getAddressLine1())
                .addressLine2(branch.getAddressLine2())
                .route(branch.getRoute())
                .area(branch.getArea())
                .city(branch.getCity())
                .state(branch.getState())
                .country(branch.getCountry())
                .pinCode(branch.getPinCode())
                .isActive(branch.getIsActive())
                .build();
    }

    private String generateBranchCode(Tenant tenant, TenantBranchDto dto) {
        String source = dto.getBranchCode() != null && !dto.getBranchCode().isBlank()
                ? dto.getBranchCode()
                : dto.getBranchName();
        String code = source.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (code.isBlank()) {
            code = "BRANCH";
        }
        if (code.length() > 40) {
            code = code.substring(0, 40);
        }
        return tenant.getTenantCode() + "-" + code;
    }

    private TenantDetails mapDtoToEntity(TenantDetailsDto dto) {
        return TenantDetails.builder()
                .legalName(dto.getLegalName())
                .businessType(dto.getBusinessType())
                .baseCurrency(dto.getBaseCurrency())
                .timeZone(dto.getTimeZone())
                .gstNumber(dto.getGstNumber())
                .panNumber(dto.getPanNumber())
                .supportEmail(dto.getSupportEmail())
                .contactPhone(dto.getContactPhone())
                .website(dto.getWebsite())
                .logoUrl(dto.getLogoUrl())
                .build();
    }

    private TenantDetailsDto mapEntityToDto(TenantDetails entity) {
        return TenantDetailsDto.builder()
                .legalName(entity.getLegalName())
                .businessType(entity.getBusinessType())
                .baseCurrency(entity.getBaseCurrency())
                .timeZone(entity.getTimeZone())
                .gstNumber(entity.getGstNumber())
                .panNumber(entity.getPanNumber())
                .supportEmail(entity.getSupportEmail())
                .contactPhone(entity.getContactPhone())
                .website(entity.getWebsite())
                .logoUrl(entity.getLogoUrl())
                .build();
    }
}
