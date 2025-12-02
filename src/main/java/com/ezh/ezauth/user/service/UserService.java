package com.ezh.ezauth.user.service;


import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.repository.PrivilegeRepository;
import com.ezh.ezauth.user.dto.UserApplicationDto;
import com.ezh.ezauth.user.dto.UserInitResponse;
import com.ezh.ezauth.user.dto.UserModulePrivilegeDto;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserModulePrivilegeRepository userModulePrivilegeRepository;
    private final PrivilegeRepository privilegeRepository;

    public UserInitResponse getUserInitDetails(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // map userApplications → DTO
        Set<UserApplicationDto> applicationDtos = user.getUserApplications().stream()
                .map(ua -> UserApplicationDto.builder()
                        .id(ua.getId())
                        .appKey(ua.getApplication().getAppKey())
                        .appName(ua.getApplication().getAppName())
                        .assignedAt(ua.getAssignedAt())
                        .isActive(ua.getIsActive())
                        .modulePrivileges(
                                ua.getModulePrivileges().stream()
                                        .map(ump -> UserModulePrivilegeDto.builder()
                                                .moduleKey(ump.getModule().getModuleKey())
//                                                .moduleName(ump.getModule().getModuleName())
                                                .privilegeKey(getPrivileges(ump.getModule().getId(), ump.getUserApplication().getId()))
                                                .build()
                                        ).collect(Collectors.toSet())
                        )
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

    private Set<String> getPrivileges(Long moduleId, Long userApplicationId) {

        return userModulePrivilegeRepository.findPrivilegesOfUser(moduleId, userApplicationId)
                .stream()
                .map(Privilege::getPrivilegeKey)
                .collect(Collectors.toSet());
    }


}
