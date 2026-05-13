package com.ezh.ezauth.branch.controller;

import com.ezh.ezauth.branch.dto.BranchDto;
import com.ezh.ezauth.branch.service.BranchService;
import com.ezh.ezauth.common.dto.AddressDto;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.ResponseResource;
import com.ezh.ezauth.utils.exception.CommonException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/branch")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> createBranch(
            @Valid @RequestBody BranchDto.CreateRequest request) throws CommonException {
        log.info("Creating branch: {}", request.getBranchCode());
        return ResponseResource.success(HttpStatus.CREATED, branchService.createBranch(request), "Branch created successfully");
    }

    @PutMapping(value = "/{branchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> updateBranch(
            @PathVariable Long branchId,
            @RequestBody BranchDto.UpdateRequest request) throws CommonException {
        log.info("Updating branch ID: {}", branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.updateBranch(branchId, request), "Branch updated successfully");
    }

    @DeleteMapping(value = "/{branchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteBranch(@PathVariable Long branchId) throws CommonException {
        log.info("Deactivating branch ID: {}", branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.deleteBranch(branchId), "Branch deactivated successfully");
    }

    @GetMapping(value = "/{branchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<BranchDto.Response> getBranchById(@PathVariable Long branchId) throws CommonException {
        log.info("Fetching branch ID: {}", branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.getBranchById(branchId), "Branch fetched successfully");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<BranchDto.Response>> getBranches() throws CommonException {
        log.info("Fetching all branches for tenant");
        return ResponseResource.success(HttpStatus.OK, branchService.getBranchesByTenant(), "Branches fetched successfully");
    }

    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<BranchDto.Summary>> getBranchSummaries() throws CommonException {
        log.info("Fetching active branch summaries for tenant");
        return ResponseResource.success(HttpStatus.OK, branchService.getActiveBranchSummaries(), "Branch summaries fetched successfully");
    }

    @GetMapping(value = "/my", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<BranchDto.Response> getMyBranch() throws CommonException {
        log.info("Fetching branch for current user");
        return ResponseResource.success(HttpStatus.OK, branchService.getMyBranch(), "Branch fetched successfully");
    }

    @GetMapping(value = "/{branchId}/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<List<BranchDto.UserItem>> getBranchUsers(@PathVariable Long branchId) throws CommonException {
        log.info("Fetching users for branch ID: {}", branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.getBranchUsers(branchId), "Branch users fetched successfully");
    }

    @PatchMapping(value = "/{branchId}/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> assignUserToBranch(
            @PathVariable Long branchId,
            @PathVariable Long userId) throws CommonException {
        log.info("Assigning user {} to branch ID: {}", userId, branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.assignUserToBranch(branchId, userId), "User assigned to branch successfully");
    }

    @DeleteMapping(value = "/{branchId}/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> removeUserFromBranch(
            @PathVariable Long branchId,
            @PathVariable Long userId) throws CommonException {
        log.info("Removing user {} from branch ID: {}", userId, branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.removeUserFromBranch(branchId, userId), "User removed from branch successfully");
    }

    @PostMapping(value = "/{branchId}/address", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> addBranchAddress(
            @PathVariable Long branchId,
            @Valid @RequestBody AddressDto request) throws CommonException {
        log.info("Adding address to branch ID: {}", branchId);
        return ResponseResource.success(HttpStatus.CREATED, branchService.addBranchAddress(branchId, request), "Branch address added successfully");
    }

    @DeleteMapping(value = "/{branchId}/address/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseResource<CommonResponse> deleteBranchAddress(
            @PathVariable Long branchId,
            @PathVariable Long addressId) throws CommonException {
        log.info("Deleting address {} from branch ID: {}", addressId, branchId);
        return ResponseResource.success(HttpStatus.OK, branchService.deleteBranchAddress(branchId, addressId), "Branch address deleted successfully");
    }
}
