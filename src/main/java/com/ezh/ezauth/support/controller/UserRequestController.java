package com.ezh.ezauth.support.controller;


import com.ezh.ezauth.support.dto.CreateAppRequestDto;
import com.ezh.ezauth.support.dto.CreateMarketingRequestDto;
import com.ezh.ezauth.support.dto.UpdateRequestDto;
import com.ezh.ezauth.support.dto.UserRequestDto;
import com.ezh.ezauth.support.service.UserRequestService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/userrequests")
@RequiredArgsConstructor
public class UserRequestController {

    private final UserRequestService userRequestService;

    @PostMapping(value = "/mkt", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createMarketingRequest(@RequestBody CreateMarketingRequestDto requestDto) throws CommonException {
        log.info("Entered create marketing user request");
        CommonResponse response = userRequestService.createMarketingRequest(requestDto);
        return ResponseResource.success(HttpStatus.CREATED, response, "Marketing request submitted successfully");
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createRequest(@RequestBody CreateAppRequestDto requestDto) throws CommonException {
        log.info("Entered create user request");
        CommonResponse response = userRequestService.createRequest(requestDto);
        return ResponseResource.success(HttpStatus.CREATED, response, "Request submitted successfully");
    }

    @GetMapping(value = "/{userReqUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<UserRequestDto> getRequestById(@PathVariable String userReqUuid) throws CommonException {
        log.info("Entered get user request by uuid: {}", userReqUuid);
        UserRequestDto response = userRequestService.getRequestById(userReqUuid);
        return ResponseResource.success(HttpStatus.OK, response, "Request fetched successfully");
    }

    @PutMapping(value = "/{userReqUuid}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateRequest(
            @PathVariable String userReqUuid,
            @RequestBody UpdateRequestDto requestDto) throws CommonException {
        log.info("Entered update user request for uuid: {}", userReqUuid);
        CommonResponse response = userRequestService.updateRequest(userReqUuid, requestDto);
        return ResponseResource.success(HttpStatus.OK, response, "Request updated successfully");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Page<UserRequestDto>> getRequestsByPagination(
            @RequestParam(required = false) String tenantUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws CommonException {
        log.info("Entered get user requests with pagination");
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<UserRequestDto> response = userRequestService.getRequestsWithPagination(tenantUuid, pageable);
        return ResponseResource.success(HttpStatus.OK, response, "Requests fetched successfully");
    }
}