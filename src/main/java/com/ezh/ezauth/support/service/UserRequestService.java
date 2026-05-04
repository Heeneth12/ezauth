package com.ezh.ezauth.support.service;


import com.ezh.ezauth.support.dto.CreateAppRequestDto;
import com.ezh.ezauth.support.dto.CreateMarketingRequestDto;
import com.ezh.ezauth.support.dto.UpdateRequestDto;
import com.ezh.ezauth.support.dto.UserRequestDto;
import com.ezh.ezauth.support.entity.SupportCategory;
import com.ezh.ezauth.support.entity.SupportPriority;
import com.ezh.ezauth.support.entity.SupportStatus;
import com.ezh.ezauth.support.entity.UserRequest;
import com.ezh.ezauth.support.repository.UserRequestRepository;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.utils.EmailService;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRequestService {

    private final UserRequestRepository userRequestRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;


    @Transactional
    public CommonResponse createMarketingRequest(CreateMarketingRequestDto requestDto) throws CommonException {
        log.info("Creating new marketing request for email: {}", requestDto.getContactEmail());

        UserRequest userRequest = UserRequest.builder()
                .tenantUuid(null)
                .userUuid(null)
                .assignedUuid(null)
                .contactEmail(requestDto.getContactEmail())
                .contactName(requestDto.getContactName())
                .subject(requestDto.getSubject())
                .description(requestDto.getDescription())
                .sourceUrl(requestDto.getSourceUrl())
                .sourceName(requestDto.getSourceName())
                .category(SupportCategory.GENERAL_INQUIRY)
                .status(SupportStatus.NEW)
                .priority(SupportPriority.MEDIUM)
                .metadata(requestDto.getMetadata())
                .build();

        userRequest = userRequestRepository.save(userRequest);

        // Send Acknowledgement mail asynchronously
        emailService.sendSupportAcknowledgmentEmail(
                userRequest.getContactEmail(),
                userRequest.getContactName(),
                userRequest.getUserReqUuid(),
                userRequest.getSubject()
        );

        return CommonResponse.builder()
                .id(userRequest.getUserReqUuid())
                .status(Status.SUCCESS)
                .message("Marketing request created successfully")
                .build();
    }

    @Transactional
    public CommonResponse createRequest(CreateAppRequestDto requestDto) throws CommonException {

        log.info("Creating new user request from source: {}", requestDto.getSourceUrl());

        Long tenantId = UserContextUtil.getTenantIdOrThrow();
        Long userId = UserContextUtil.getUserIdOrThrow();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found with id: " + tenantId, HttpStatus.BAD_REQUEST));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException("User not found with id: " + userId, HttpStatus.BAD_REQUEST));

        UserRequest userRequest = UserRequest.builder()
                .tenantUuid(tenant.getTenantUuid())
                .userUuid(user.getUserUuid())
                .contactEmail(user.getEmail())
                .contactName(user.getFullName())
                .subject(requestDto.getSubject())
                .description(requestDto.getDescription())
                .sourceUrl(requestDto.getSourceUrl())
                .sourceName(requestDto.getSourceName())
                .category(requestDto.getCategory())
                .status(SupportStatus.NEW)
                .priority(requestDto.getPriority())
                .metadata(requestDto.getMetadata())
                .build();

        userRequest = userRequestRepository.save(userRequest);

        return CommonResponse.builder()
                .id(userRequest.getUserReqUuid())
                .status(Status.SUCCESS)
                .message("User request created successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public UserRequestDto getRequestById(String userReqUuid) throws CommonException {
        UserRequest request = userRequestRepository.findByUserReqUuid(userReqUuid)
                .orElseThrow(() -> new CommonException("Request not found with UUID: " + userReqUuid, HttpStatus.NOT_FOUND));
        return mapToDto(request);
    }

    @Transactional
    public CommonResponse updateRequest(String userReqUuid, UpdateRequestDto updateDto) throws CommonException {
        log.info("Updating user request: {}", userReqUuid);

        UserRequest existingRequest = userRequestRepository.findByUserReqUuid(userReqUuid)
                .orElseThrow(() -> new CommonException("Request not found with UUID: " + userReqUuid, HttpStatus.NOT_FOUND));

        // 1. Update Status & Resolution Timestamp
        if (updateDto.getStatus() != null) {
            existingRequest.setStatus(updateDto.getStatus());

            // Using Enum equality is much safer than String "equalsIgnoreCase"
            if (updateDto.getStatus() == SupportStatus.RESOLVED || updateDto.getStatus() == SupportStatus.CLOSED) {
                existingRequest.setResolvedAt(LocalDateTime.now());
            } else {
                // If the ticket is moved back to IN_PROGRESS or NEW, clear the resolved timestamp
                existingRequest.setResolvedAt(null);
            }
        }

        // 2. Update Priority
        if (updateDto.getPriority() != null) {
            existingRequest.setPriority(updateDto.getPriority());
        }

        // 3. Update Category
        if (updateDto.getCategory() != null) {
            existingRequest.setCategory(updateDto.getCategory());
        }

        // 4. Update Assignee
        if (updateDto.getAssignedUuid() != null) {
            existingRequest.setAssignedUuid(updateDto.getAssignedUuid());
        }

        userRequestRepository.save(existingRequest);

        return CommonResponse.builder()
                .id(existingRequest.getUserReqUuid())
                .status(Status.SUCCESS)
                .message("User request updated successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public Page<UserRequestDto> getRequestsWithPagination(String tenantUuid, Pageable pageable) {
        log.info("Fetching paginated requests for tenant id: {}", tenantUuid);
        Page<UserRequest> requestsPage;

        if (tenantUuid != null) {
            requestsPage = userRequestRepository.findByTenantUuid(tenantUuid, pageable);
        } else {
            requestsPage = userRequestRepository.findAll(pageable);
        }

        return requestsPage.map(this::mapToDto);
    }

    private UserRequestDto mapToDto(UserRequest entity) {
        return UserRequestDto.builder()
                .userReqUuid(entity.getUserReqUuid())
                .userUuid(entity.getUserUuid())
                .assignedUuid(entity.getAssignedUuid())
                .contactEmail(entity.getContactEmail())
                .contactName(entity.getContactName())
                .subject(entity.getSubject())
                .description(entity.getDescription())
                .sourceUrl(entity.getSourceUrl())
                .sourceName(entity.getSourceName())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}