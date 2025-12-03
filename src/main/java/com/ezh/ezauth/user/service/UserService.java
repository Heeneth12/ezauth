package com.ezh.ezauth.user.service;


import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.common.repository.PrivilegeRepository;
import com.ezh.ezauth.user.dto.UserApplicationDto;
import com.ezh.ezauth.user.dto.UserInitResponse;
import com.ezh.ezauth.user.dto.UserModulePrivilegeDto;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.entity.UserApplication;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import com.ezh.ezauth.user.repository.UserModulePrivilegeRepository;
import com.ezh.ezauth.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
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
