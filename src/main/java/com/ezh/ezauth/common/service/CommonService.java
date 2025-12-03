package com.ezh.ezauth.common.service;

import com.ezh.ezauth.common.dto.ApplicationDto;
import com.ezh.ezauth.common.dto.ModuleDto;
import com.ezh.ezauth.common.dto.PrivilegeDto;
import com.ezh.ezauth.common.dto.RoleDto;
import com.ezh.ezauth.common.entity.Application;
import com.ezh.ezauth.common.entity.Module;
import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.entity.Role;
import com.ezh.ezauth.common.repository.ApplicationRepository;
import com.ezh.ezauth.common.repository.ModuleRepository;
import com.ezh.ezauth.common.repository.PrivilegeRepository;
import com.ezh.ezauth.common.repository.RoleRepository;
import com.ezh.ezauth.security.JwtTokenProvider;
import com.ezh.ezauth.tenant.dto.TenantDto;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.dto.UserDto;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CommonService {

    private final ApplicationRepository applicationRepository;
    private final ModuleRepository moduleRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;


    @Transactional(readOnly = true)
    public List<ApplicationDto> getAllApplications(String token) throws CommonException {
        log.info("Fetching all applications");
        Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
        Set<Application> applications = tenantRepository.findApplicationsByTenantId(tenantId);

        if (applications.isEmpty()) {
            log.warn("No applications found");
            return List.of();
        }

        return applications.stream()
                .map(this::constructDtoFromEntity)
                .filter(Objects::nonNull)  // ensure safe mapping
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> getAllRoles() throws CommonException{
        log.info("");
        List<Role> roles = roleRepository.findAll();

        return roles.stream().map(this::constructRoleDto).toList();
    }


    public Object getModulesByApplication(Long appId) {
        return moduleRepository.findByApplicationId(appId);
    }



//
//    // ===============================
//    // 3. GET PRIVILEGES OF MODULE
//    // ===============================
//    public Object getPrivilegesByModule(Long moduleId) {
//        return privilegeRepository.findByModuleId(moduleId);
//    }
//
//    public Object updateUserAccess(Long userId, UserAccessUpdateRequest request) {
//
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User Not Found"));
//
//        // Delete existing access
//        userModulePrivilegeRepository.deleteByUserId(userId);
//
//        // Re-create access cleanly
//        request.getApplications().forEach(appReq -> {
//            appReq.getModules().forEach(modReq -> {
//                UserModulePrivilege ump = new UserModulePrivilege();
//                ump.setUser(user);
//                ump.setModule(moduleRepository.findById(modReq.getModuleId())
//                        .orElseThrow(() -> new RuntimeException("Module not found")));
//
//                Set<String> privilegeKeys = modReq.getPrivilegeKeys();
//
//                ump.setPrivileges(
//                        privilegeRepository.findByPrivilegeKeyIn(privilegeKeys)
//                );
//
//                userModulePrivilegeRepository.save(ump);
//            });
//        });
//
//        return "User Access Updated Successfully";
//    }


    private UserDto constructUserDto(User user) {
        if (user == null) return null;

        return UserDto.builder()
                .id(user.getId())
                .userUuid(user.getUserUuid())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .tenant( constructTenantDto(user.getTenant()) )
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



    private ApplicationDto constructDtoFromEntity(Application application) {

        if (application == null) return null;

        return ApplicationDto.builder()
                .id(application.getId())
                .appName(application.getAppName())
                .appKey(application.getAppKey())
                .description(application.getDescription())
                .isActive(application.getIsActive())
                .modules(
                        application.getModules() != null
                                ? application.getModules()
                                .stream()
                                .map(this::constructModuleDto)
                                .collect(Collectors.toSet())
                                : null
                )
                .build();
    }


    private ModuleDto constructModuleDto(Module module) {

        if (module == null) return null;

        return ModuleDto.builder()
                .id(module.getId())
                .moduleName(module.getModuleName())
                .moduleKey(module.getModuleKey())
                .description(module.getDescription())
                .isActive(module.getIsActive())
                .privileges(
                        module.getPrivileges() != null
                                ? module.getPrivileges()
                                .stream()
                                .map(this::constructPrivilegeDto)
                                .collect(Collectors.toSet())
                                : null
                )
                .build();
    }

    private PrivilegeDto constructPrivilegeDto(Privilege privilege) {

        if (privilege == null) return null;

        return PrivilegeDto.builder()
                .id(privilege.getId())
                .privilegeName(privilege.getPrivilegeName())
                .privilegeKey(privilege.getPrivilegeKey())
                .description(privilege.getDescription())
                .build();
    }

    private RoleDto constructRoleDto(Role role) {
        if (role == null) return null;
        return RoleDto.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleKey(role.getRoleKey())
                .build();
    }


}
