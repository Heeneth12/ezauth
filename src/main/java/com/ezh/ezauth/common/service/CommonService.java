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

import java.util.Collections;
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
    public List<RoleDto> getAllRoles() throws CommonException {
        log.info("Fetching all roles");
        List<Role> roles = roleRepository.findAll();

        if (roles.isEmpty()) {
            log.warn("No roles found in the system");
            return Collections.emptyList();
        }

        return roles.stream().map(this::constructRoleDto).toList();
    }


    @Transactional(readOnly = true)
    public List<ModuleDto> getModulesByApplication(Long appId) throws CommonException {

        log.info("Fetching modules for applicationId={}", appId);

        List<Module> modules = moduleRepository.findByApplicationId(appId);

        if (modules.isEmpty()) {
            log.warn("No modules found for applicationId={}", appId);
            return Collections.emptyList();
        }

        return modules.stream()
                .map(this::constructModuleDto)
                .toList();
    }

    private ApplicationDto constructDtoFromEntity(Application application) {

        if (application == null) return null;

        return ApplicationDto.builder()
                .id(application.getId())
                .appName(application.getAppName())
                .appKey(application.getAppKey())
                .description(application.getDescription())
                .isActive(application.getIsActive())
//                .modules(
//                        application.getModules() != null
//                                ? application.getModules()
//                                .stream()
//                                .map(this::constructModuleDto)
//                                .collect(Collectors.toSet())
//                                : null
//                )
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
