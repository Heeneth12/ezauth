package com.ezh.ezauth.user.controller;

import com.ezh.ezauth.user.dto.CreateUserRequest;
import com.ezh.ezauth.user.dto.UserDto;
import com.ezh.ezauth.user.dto.UserFilter;
import com.ezh.ezauth.user.dto.UserMiniDto;
import com.ezh.ezauth.user.service.UserService;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createUser(@RequestBody CreateUserRequest request) throws CommonException {
        log.info("Entered Creating new user with email: {}", request.getEmail());
        CommonResponse response = userService.createUser(request);
        return ResponseResource.success(HttpStatus.CREATED, response, "User created successfully");
    }

    @PostMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Page<UserDto>> getAllUsers(@RequestParam Integer page, @RequestParam Integer size,
                                                       @RequestBody UserFilter filter) throws CommonException {
        log.info("Entered get all users details");
        Page<UserDto> response = userService.getAllUsers(filter, page, size);
        return ResponseResource.success(HttpStatus.OK, response, "All users fetched successfully");
    }

    @GetMapping(value = "/bulk", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Map<Long, UserMiniDto>> getBulkUsers(@RequestParam("ids") List<Long> ids) throws CommonException {
        log.info("Entered get bulk User details");
        Map<Long, UserMiniDto> response = userService.getUsersMiniByIds(ids);
        return ResponseResource.success(HttpStatus.OK, response, "Bulk user fetched successfully");
    }

    @GetMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<UserDto> getUserById(@PathVariable Long userId) throws CommonException {
        log.info("Fetching user with ID: {}", userId);
        UserDto response = userService.getUserById(userId, true);
        return ResponseResource.success(HttpStatus.OK, response, "User fetched successfully");
    }

    @PostMapping(value = "/{userId}/update", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateUser(@PathVariable Long userId, @RequestBody CreateUserRequest request) throws CommonException {
        log.info("Updating user with ID: {}", userId);
        CommonResponse response = userService.updateUser(userId, request);
        return ResponseResource.success(HttpStatus.OK, response, "User updated successfully");
    }

    @PutMapping(value = "/{userId}/toggle-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> toggleUserStatus(@PathVariable Long userId) throws CommonException {
        log.info("Deleting user with ID: {}", userId);
        CommonResponse response = userService.toggleUserStatus(userId);
        return ResponseResource.success(HttpStatus.OK, response, "User deleted successfully");
    }

    @PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<Page<UserDto>> searchUsers(@RequestBody UserFilter filter) throws CommonException {
        log.info("Entered get all users details");
        Page<UserDto> response = userService.searchUsers(filter);
        return ResponseResource.success(HttpStatus.OK, response, "All users fetched successfully");
    }
}
