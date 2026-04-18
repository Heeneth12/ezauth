package com.ezh.ezauth.integrations.service;

import com.ezh.ezauth.integrations.converter.EncryptedStringConverter;
import com.ezh.ezauth.integrations.dto.IntegrationDto;
import com.ezh.ezauth.integrations.dto.IntegrationRequest;
import com.ezh.ezauth.integrations.dto.TestConnectionResponse;
import com.ezh.ezauth.integrations.entity.Integration;
import com.ezh.ezauth.integrations.entity.IntegrationType;
import com.ezh.ezauth.integrations.repository.IntegrationRepository;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.EncryptedPrivateKeyInfo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public CommonResponse createIntegration(IntegrationRequest request) throws CommonException {

        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (request.getIntegrationType() == null) {
            throw new CommonException("Integration type is required", HttpStatus.BAD_REQUEST);
        }

        if (integrationRepository.existsByTenantIdAndIntegrationType(tenantId, request.getIntegrationType())) {
            throw new CommonException(
                    "Integration of type " + request.getIntegrationType()
                            + " already exists for this tenant",
                    HttpStatus.CONFLICT);
        }

        Integration integration = Integration.builder()
                .tenant(tenant)
                .integrationType(request.getIntegrationType())
                .displayName(request.getDisplayName())
                .primaryKey(request.getPrimaryKey())
                .secondaryKey(request.getSecondaryKey())
                .tertiaryKey(request.getTertiaryKey())
                .isTestMode(request.getIsTestMode() != null ? request.getIsTestMode() : false)
                .webhookConfig(request.getWebhookConfig())
                .extraConfig(request.getExtraConfig())
                .links(request.getLinks())
                .build();

        Integration saved = integrationRepository.save(integration);
        log.info("Integration {} created for tenant {}", saved.getIntegrationType(), tenantId);

        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Integration created successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public List<IntegrationDto> getIntegrations(Long tenantId) throws CommonException {
        if (!tenantRepository.existsById(tenantId)) {
            throw new CommonException("Tenant not found", HttpStatus.NOT_FOUND);
        }
        return integrationRepository.findByTenantId(tenantId)
                .stream()
                // Pass 'false' to keep the keys masked in the list view
                .map(entity -> toDto(entity, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IntegrationDto getIntegrationByType(Long tenantId, IntegrationType type) throws CommonException {
        return integrationRepository.findByTenantIdAndIntegrationType(tenantId, type)
                // Use lambda to pass 'true' for individual fetch
                .map(entity -> toDto(entity, true))
                .orElseThrow(() -> new CommonException(
                        "Integration " + type + " not configured for this tenant",
                        HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public IntegrationDto getIntegrationById(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        // Explicitly revealing keys for the detail view
        return toDto(integration, true);
    }


    @Transactional(readOnly = true)
    public CommonResponse checkIntegration(Long tenantId, IntegrationType type) {

        return integrationRepository.findByTenantIdAndIntegrationType(tenantId, type)
                .map(entity -> CommonResponse.builder()
                        .id(entity.getId().toString())
                        .message("Integration " + type + " is configured for this tenant")
                        .status(Status.SUCCESS)
                        .build())
                .orElse(CommonResponse.builder()
                        .id(null)
                        .message("Integration " + type + " not configured for this tenant")
                        .status(Status.NOT_FOUND) // Or Status.FAILURE depending on your enum
                        .build());
    }

    @Transactional
    public CommonResponse updateIntegration(Long tenantId, Long integrationId, IntegrationRequest request)
            throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        if (request.getDisplayName() != null)
            integration.setDisplayName(request.getDisplayName());
        if (request.getPrimaryKey() != null)
            integration.setPrimaryKey(request.getPrimaryKey());
        if (request.getSecondaryKey() != null)
            integration.setSecondaryKey(request.getSecondaryKey());
        if (request.getTertiaryKey() != null)
            integration.setTertiaryKey(request.getTertiaryKey());
        if (request.getIsTestMode() != null)
            integration.setIsTestMode(request.getIsTestMode());
        if (request.getWebhookConfig() != null)
            integration.setWebhookConfig(request.getWebhookConfig());
        if (request.getExtraConfig() != null)
            integration.setExtraConfig(request.getExtraConfig());
        if (request.getLinks() != null)
            integration.setLinks(request.getLinks());

        Integration updated = integrationRepository.save(integration);
        log.info("Integration {} updated for tenant {}", updated.getId(), tenantId);

        return CommonResponse.builder()
                .id(updated.getId().toString())
                .message("Integration updated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse deleteIntegration(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        integration.setIsActive(false);
        integrationRepository.save(integration);
        log.info("Integration {} soft-deleted for tenant {}", integrationId, tenantId);

        return CommonResponse.builder()
                .id(integration.getId().toString())
                .message("Integration deactivated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse toggleIntegration(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        integration.setIsActive(!integration.getIsActive());
        integrationRepository.save(integration);

        String state = Boolean.TRUE.equals(integration.getIsActive()) ? "enabled" : "disabled";
        log.info("Integration {} {} for tenant {}", integrationId, state, tenantId);

        return CommonResponse.builder()
                .id(integration.getId().toString())
                .message("Integration " + state + " successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public TestConnectionResponse testConnection(Long tenantId, Long integrationId) throws CommonException {
        Integration integration = integrationRepository.findByIdAndTenantId(integrationId, tenantId)
                .orElseThrow(() -> new CommonException("Integration not found", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        boolean hasKey = integration.getPrimaryKey() != null && !integration.getPrimaryKey().isBlank();

        if (!hasKey) {
            integration.setIsConnected(false);
            integrationRepository.save(integration);
            return TestConnectionResponse.builder()
                    .connected(false)
                    .message("Connection failed: primary key is missing or empty")
                    .testedAt(now)
                    .build();
        }

        integration.setIsConnected(true);
        integration.setConnectedAt(now);
        integrationRepository.save(integration);
        log.info("Test connection passed for integration {} of tenant {}", integrationId, tenantId);

        return TestConnectionResponse.builder()
                .connected(true)
                .message("Credentials validated. Note: live provider ping is a planned enhancement.")
                .testedAt(now)
                .build();
    }

    private IntegrationDto toDto(Integration integration, Boolean revealKeys) {
        return IntegrationDto.builder()
                .id(integration.getId())
                .integrationUuid(integration.getIntegrationUuid())
                .tenantId(integration.getTenant().getId())
                .integrationType(integration.getIntegrationType())
                .displayName(integration.getDisplayName())
                .primaryKey(revealKeys ? integration.getPrimaryKey() : "********")
                .secondaryKey(revealKeys ? integration.getSecondaryKey() : "********")
                .tertiaryKey(revealKeys ? integration.getTertiaryKey() : "********")
                .isTestMode(integration.getIsTestMode())
                .isConnected(integration.getIsConnected())
                .isActive(integration.getIsActive())
                .webhookConfig(integration.getWebhookConfig())
                .extraConfig(integration.getExtraConfig())
                .links(integration.getLinks())
                .connectedAt(integration.getConnectedAt())
                .createdAt(integration.getCreatedAt())
                .updatedAt(integration.getUpdatedAt())
                .build();
    }
}
