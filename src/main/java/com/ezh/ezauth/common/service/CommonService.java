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
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.entity.UserType;
import com.ezh.ezauth.user.repository.UserRoleRepository;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    private final PrivilegeRepository privilegeRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final UserRoleRepository userRoleRepository;


    @Transactional(readOnly = true)
    public List<ApplicationDto> getAllApplications() throws CommonException {
        log.info("Fetching all applications");

        // Get tenant id from context
        Long tenantId = UserContextUtil.getTenantId();
        if (tenantId == null) {
            throw new CommonException("Tenant id missing in request", HttpStatus.UNAUTHORIZED);
        }

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
        Long tenantId = UserContextUtil.getTenantId();
        List<Role> roles = roleRepository.findByTenantId(tenantId);

        if (roles.isEmpty()) {
            log.warn("No roles found in the system");
            return Collections.emptyList();
        }

        return roles.stream().map(this::constructRoleDto).toList();
    }

    @Transactional
    public CommonResponse createRole(RoleDto roleDto) throws CommonException {
        log.info("Create role with : {}", roleDto);

        // Get tenant id from context
        Long tenantId = UserContextUtil.getTenantId();
        if (tenantId == null) {
            throw new CommonException("Tenant id missing in request", HttpStatus.UNAUTHORIZED);
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        // Create role
        Role role = Role.builder()
                .roleKey(roleDto.getRoleKey())
                .roleName(roleDto.getRoleName())
                .description(roleDto.getDescription())
                .tenant(tenant)
                .isActive(true)
                .isSystemRole(false)
                .build();

        roleRepository.save(role);

        return CommonResponse.builder()
                .status(Status.SUCCESS)
                .message("Role created successfully")
                .build();
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

    @Transactional
    public CommonResponse createApplication(ApplicationDto dto) throws CommonException {
        log.info("Creating application: {}", dto.getAppKey());
        Long tenantId = UserContextUtil.getTenantIdOrThrow();

        if (applicationRepository.findByAppKey(dto.getAppKey()).isPresent()) {
            throw new CommonException("Application with key '" + dto.getAppKey() + "' already exists", HttpStatus.CONFLICT);
        }

        Application app = Application.builder()
                .appName(dto.getAppName())
                .appKey(dto.getAppKey())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() == null ? Boolean.TRUE : dto.getIsActive())
                .build();
        app = applicationRepository.save(app);
        saveApplicationModules(app, dto.getModules());
        tenantRepository.linkApplicationToTenant(tenantId, app.getId());

        return CommonResponse.builder().status(Status.SUCCESS).message("Application created successfully").build();
    }

    @Transactional
    public CommonResponse updateApplication(Long appId, ApplicationDto dto) throws CommonException {
        log.info("Updating application id={}", appId);
        Long tenantId = UserContextUtil.getTenantIdOrThrow();

        if (!tenantRepository.existsTenantApplication(tenantId, appId)) {
            throw new CommonException("Application not found", HttpStatus.NOT_FOUND);
        }

        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new CommonException("Application not found", HttpStatus.NOT_FOUND));

        app.setAppName(dto.getAppName());
        app.setDescription(dto.getDescription());
        if (dto.getAppKey() != null && !dto.getAppKey().equals(app.getAppKey())) {
            if (applicationRepository.findByAppKey(dto.getAppKey())
                    .filter(existing -> !existing.getId().equals(appId))
                    .isPresent()) {
                throw new CommonException("Application with key '" + dto.getAppKey() + "' already exists", HttpStatus.CONFLICT);
            }
            app.setAppKey(dto.getAppKey());
        }
        if (dto.getIsActive() != null) {
            app.setIsActive(dto.getIsActive());
        }
        applicationRepository.save(app);
        saveApplicationModules(app, dto.getModules());

        return CommonResponse.builder().status(Status.SUCCESS).message("Application updated successfully").build();
    }

    @Transactional
    public CommonResponse deleteApplication(Long appId) throws CommonException {
        log.info("Deleting application id={}", appId);
        Long tenantId = UserContextUtil.getTenantIdOrThrow();

        if (!tenantRepository.existsTenantApplication(tenantId, appId)) {
            throw new CommonException("Application not found", HttpStatus.NOT_FOUND);
        }

        tenantRepository.unlinkApplicationFromTenant(tenantId, appId);

        return CommonResponse.builder().status(Status.SUCCESS).message("Application removed successfully").build();
    }

    @Transactional
    public CommonResponse updateRole(Long roleId, RoleDto dto) throws CommonException {
        log.info("Updating role id={}", roleId);
        Long tenantId = UserContextUtil.getTenantIdOrThrow();

        Role role = roleRepository.findByIdAndTenant_Id(roleId, tenantId)
                .orElseThrow(() -> new CommonException("Role not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(role.getIsSystemRole())) {
            throw new CommonException("System roles cannot be modified", HttpStatus.BAD_REQUEST);
        }

        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setDescription(dto.getDescription());
        roleRepository.save(role);

        return CommonResponse.builder().status(Status.SUCCESS).message("Role updated successfully").build();
    }

    @Transactional
    public CommonResponse deleteRole(Long roleId) throws CommonException {
        log.info("Deleting role id={}", roleId);
        Long tenantId = UserContextUtil.getTenantIdOrThrow();

        Role role = roleRepository.findByIdAndTenant_Id(roleId, tenantId)
                .orElseThrow(() -> new CommonException("Role not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(role.getIsSystemRole())) {
            throw new CommonException("System roles cannot be deleted", HttpStatus.BAD_REQUEST);
        }

        if (userRoleRepository.existsByRole_Id(roleId)) {
            throw new CommonException("Role is assigned to users and cannot be deleted", HttpStatus.CONFLICT);
        }

        roleRepository.delete(role);

        return CommonResponse.builder().status(Status.SUCCESS).message("Role deleted successfully").build();
    }

    @Transactional(readOnly = true)
    public List<PrivilegeDto> getPrivilegesByModule(Long moduleId) throws CommonException {
        log.info("Fetching privileges for moduleId={}", moduleId);

        if (!moduleRepository.existsById(moduleId)) {
            throw new CommonException("Module not found", HttpStatus.NOT_FOUND);
        }

        List<Privilege> privileges = privilegeRepository.findByModuleId(moduleId);

        if (privileges.isEmpty()) {
            log.warn("No privileges found for moduleId={}", moduleId);
            return Collections.emptyList();
        }

        return privileges.stream()
                .map(this::constructPrivilegeDto)
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
                .modules(
                        application.getModules() != null
                                ? application.getModules()
                                .stream()
                                .map(this::constructModuleDto)
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                                : Collections.emptySet()
                )
                .build();
    }


    private void saveApplicationModules(Application app, Set<ModuleDto> moduleDtos) throws CommonException {
        if (moduleDtos == null) {
            return;
        }

        for (ModuleDto moduleDto : moduleDtos) {
            Module module = resolveModule(app, moduleDto);
            module.setModuleName(moduleDto.getModuleName());
            module.setModuleKey(moduleDto.getModuleKey());
            module.setDescription(moduleDto.getDescription());
            module.setIsActive(moduleDto.getIsActive() == null ? Boolean.TRUE : moduleDto.getIsActive());
            module.setApplication(app);
            module = moduleRepository.save(module);

            saveModulePrivileges(module, moduleDto.getPrivileges());
        }
    }

    private Module resolveModule(Application app, ModuleDto moduleDto) throws CommonException {
        if (moduleDto.getId() != null) {
            Module module = moduleRepository.findById(moduleDto.getId())
                    .orElseThrow(() -> new CommonException("Module not found", HttpStatus.NOT_FOUND));

            if (!module.getApplication().getId().equals(app.getId())) {
                throw new CommonException("Module does not belong to application", HttpStatus.BAD_REQUEST);
            }

            return module;
        }

        return moduleRepository.findByApplicationIdAndModuleKey(app.getId(), moduleDto.getModuleKey())
                .orElseGet(Module::new);
    }

    private void saveModulePrivileges(Module module, Set<PrivilegeDto> privilegeDtos) throws CommonException {
        if (privilegeDtos == null) {
            return;
        }

        for (PrivilegeDto privilegeDto : privilegeDtos) {
            Privilege privilege = resolvePrivilege(module, privilegeDto);
            privilege.setPrivilegeName(privilegeDto.getPrivilegeName());
            privilege.setPrivilegeKey(privilegeDto.getPrivilegeKey());
            privilege.setDescription(privilegeDto.getDescription());
            privilege.setModule(module);
            privilegeRepository.save(privilege);
        }
    }

    private Privilege resolvePrivilege(Module module, PrivilegeDto privilegeDto) throws CommonException {
        if (privilegeDto.getId() != null) {
            return privilegeRepository.findByModule_IdAndId(module.getId(), privilegeDto.getId())
                    .orElseThrow(() -> new CommonException("Privilege not found", HttpStatus.NOT_FOUND));
        }

        return privilegeRepository.findByPrivilegeKeyAndModuleId(privilegeDto.getPrivilegeKey(), module.getId())
                .orElseGet(Privilege::new);
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

    public List<String> getUserTypes() {
        return Arrays.stream(UserType.values())
                .map(Enum::name)
                .toList();
    }
}
