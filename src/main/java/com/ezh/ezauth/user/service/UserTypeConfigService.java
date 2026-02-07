package com.ezh.ezauth.user.service;

import com.ezh.ezauth.user.entity.UserType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service to manage default configurations for different user types.
 * Provides default roles, applications, and privileges based on user type.
 */
@Slf4j
@Service
public class UserTypeConfigService {

    /**
     * Configuration holder for user type defaults
     */
    @Getter
    private static class UserTypeConfig {
        private final Set<String> roleKeys;
        private final Set<String> applicationKeys;
        private final List<PrivilegeConfig> privilegeConfigs;
        private final boolean enforceDefaults;  // If true, always apply defaults even if request has values

        public UserTypeConfig(Set<String> roleKeys, Set<String> applicationKeys, 
                              List<PrivilegeConfig> privilegeConfigs, boolean enforceDefaults) {
            this.roleKeys = roleKeys;
            this.applicationKeys = applicationKeys;
            this.privilegeConfigs = privilegeConfigs;
            this.enforceDefaults = enforceDefaults;
        }
    }

    /**
     * Privilege configuration for a specific module
     */
    @Getter
    public static class PrivilegeConfig {
        private final String moduleKey;
        private final Set<String> privilegeKeys;

        public PrivilegeConfig(String moduleKey, Set<String> privilegeKeys) {
            this.moduleKey = moduleKey;
            this.privilegeKeys = privilegeKeys;
        }
    }

    // Default configurations for each user type
    private static final Map<UserType, UserTypeConfig> DEFAULTS = Map.of(
            UserType.VENDOR, new UserTypeConfig(
                    Set.of("ADMIN"),                    // Default role
                    Set.of("EZH_INV_APP"),              // Default application
                    List.of(
                            new PrivilegeConfig("EZH_INV_VENDOR", Set.of("EZH_INV_PRQ_VIEW"))
                    ),
                    true  // enforceDefaults = true (always apply defaults for vendors)
            )
            // Future: Add EMPLOYEE and CUSTOMER defaults here
    );

    /**
     * Get default role keys for a user type
     */
    public Set<String> getDefaultRoleKeys(UserType userType) {
        UserTypeConfig config = DEFAULTS.get(userType);
        if (config == null) {
            log.debug("No default role configuration for user type: {}", userType);
            return Collections.emptySet();
        }
        return config.getRoleKeys();
    }

    /**
     * Get default application keys for a user type
     */
    public Set<String> getDefaultApplicationKeys(UserType userType) {
        UserTypeConfig config = DEFAULTS.get(userType);
        if (config == null) {
            log.debug("No default application configuration for user type: {}", userType);
            return Collections.emptySet();
        }
        return config.getApplicationKeys();
    }

    /**
     * Get default privilege configurations for a user type
     */
    public List<PrivilegeConfig> getDefaultPrivilegeConfigs(UserType userType) {
        UserTypeConfig config = DEFAULTS.get(userType);
        if (config == null) {
            log.debug("No default privilege configuration for user type: {}", userType);
            return Collections.emptyList();
        }
        return config.getPrivilegeConfigs();
    }

    /**
     * Check if a user type has default configurations
     */
    public boolean hasDefaults(UserType userType) {
        return DEFAULTS.containsKey(userType);
    }

    /**
     * Check if defaults should be enforced (always applied) for a user type
     */
    public boolean shouldEnforceDefaults(UserType userType) {
        UserTypeConfig config = DEFAULTS.get(userType);
        return config != null && config.isEnforceDefaults();
    }
}
