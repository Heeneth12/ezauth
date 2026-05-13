package com.ezh.ezauth.branch.service;

import com.ezh.ezauth.branch.dto.BranchDto;
import com.ezh.ezauth.branch.entity.Branch;
import com.ezh.ezauth.branch.repository.BranchRepository;
import com.ezh.ezauth.common.dto.AddressDto;
import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.common.entity.EntityType;
import com.ezh.ezauth.tenant.entity.Tenant;
import com.ezh.ezauth.tenant.repository.TenantRepository;
import com.ezh.ezauth.user.entity.User;
import com.ezh.ezauth.user.repository.UserRepository;
import com.ezh.ezauth.utils.UserContextUtil;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommonResponse createBranch(BranchDto.CreateRequest request) {
        Long tenantId = UserContextUtil.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new CommonException("Tenant not found", HttpStatus.NOT_FOUND));

        if (branchRepository.existsByBranchCodeAndTenantId(request.getBranchCode(), tenantId)) {
            throw new CommonException("Branch code already exists for this tenant", HttpStatus.CONFLICT);
        }

        if (Boolean.TRUE.equals(request.getIsHeadOffice())) {
            clearHeadOffice(tenantId);
        }

        Branch branch = Branch.builder()
                .tenant(tenant)
                .branchName(request.getBranchName())
                .branchCode(request.getBranchCode())
                .isHeadOffice(Boolean.TRUE.equals(request.getIsHeadOffice()))
                .isActive(true)
                .build();

        Branch saved = branchRepository.save(branch);

        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Branch created successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse updateBranch(Long branchId, BranchDto.UpdateRequest request) {
        Long tenantId = UserContextUtil.getTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        if (request.getBranchName() != null) {
            branch.setBranchName(request.getBranchName());
        }
        if (Boolean.TRUE.equals(request.getIsHeadOffice()) && !Boolean.TRUE.equals(branch.getIsHeadOffice())) {
            clearHeadOffice(tenantId);
            branch.setIsHeadOffice(true);
        } else if (request.getIsHeadOffice() != null) {
            branch.setIsHeadOffice(request.getIsHeadOffice());
        }
        if (request.getIsActive() != null) {
            branch.setIsActive(request.getIsActive());
        }

        branchRepository.save(branch);

        return CommonResponse.builder()
                .id(branchId.toString())
                .message("Branch updated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse deleteBranch(Long branchId) {
        Long tenantId = UserContextUtil.getTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        branch.setIsActive(false);
        branchRepository.save(branch);

        return CommonResponse.builder()
                .id(branchId.toString())
                .message("Branch deactivated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public BranchDto.Response getBranchById(Long branchId) {
        Long tenantId = UserContextUtil.getTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        return toResponse(branch);
    }

    @Transactional(readOnly = true)
    public List<BranchDto.Response> getBranchesByTenant() {
        Long tenantId = UserContextUtil.getTenantId();
        return getBranchesByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<BranchDto.Response> getBranchesByTenantId(Long tenantId) {
        return branchRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BranchDto.Summary> getActiveBranchSummaries() {
        Long tenantId = UserContextUtil.getTenantId();
        return getActiveBranchSummariesByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<BranchDto.Summary> getActiveBranchSummariesByTenantId(Long tenantId) {
        return branchRepository.findByTenantIdAndIsActive(tenantId, true).stream()
                .map(b -> BranchDto.Summary.builder()
                        .id(b.getId())
                        .branchName(b.getBranchName())
                        .branchCode(b.getBranchCode())
                        .isHeadOffice(b.getIsHeadOffice())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public CommonResponse addBranchAddress(Long branchId, AddressDto dto) {
        Long tenantId = UserContextUtil.getTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        if (branch.getAddresses() == null) {
            branch.setAddresses(new HashSet<>());
        }

        boolean typeExists = branch.getAddresses().stream()
                .anyMatch(a -> a.getAddressType() == dto.getType());
        if (typeExists) {
            throw new CommonException("Address already exists for type: " + dto.getType(), HttpStatus.CONFLICT);
        }

        Address address = Address.builder()
                .entityType(EntityType.BRANCH)
                .entityId(branchId)
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .route(dto.getRoute())
                .area(dto.getArea())
                .city(dto.getCity())
                .state(dto.getState())
                .country(dto.getCountry())
                .pinCode(dto.getPinCode())
                .addressType(dto.getType())
                .build();

        branch.getAddresses().add(address);
        branchRepository.save(branch);

        return CommonResponse.builder()
                .id(branchId.toString())
                .message("Branch address added successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse deleteBranchAddress(Long branchId, Long addressId) {
        Long tenantId = UserContextUtil.getTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        if (branch.getAddresses() == null || branch.getAddresses().isEmpty()) {
            throw new CommonException("No addresses found for this branch", HttpStatus.NOT_FOUND);
        }

        Address toDelete = branch.getAddresses().stream()
                .filter(a -> a.getId() != null && a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));

        branch.getAddresses().remove(toDelete);
        branchRepository.save(branch);

        return CommonResponse.builder()
                .id(addressId.toString())
                .message("Branch address deleted successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BranchDto.UserItem> getBranchUsers(Long branchId) {
        Long tenantId = UserContextUtil.getTenantId();
        if (!branchRepository.findByIdAndTenantId(branchId, tenantId).isPresent()) {
            throw new CommonException("Branch not found", HttpStatus.NOT_FOUND);
        }
        return userRepository.findByBranch_IdAndTenant_Id(branchId, tenantId).stream()
                .map(this::toUserItem)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommonResponse assignUserToBranch(Long branchId, Long userId) {
        Long tenantId = UserContextUtil.getTenantId();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new CommonException("Branch not found", HttpStatus.NOT_FOUND));

        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        user.setBranch(branch);
        userRepository.save(user);

        return CommonResponse.builder()
                .id(userId.toString())
                .message("User assigned to branch successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse removeUserFromBranch(Long branchId, Long userId) {
        Long tenantId = UserContextUtil.getTenantId();
        if (!branchRepository.findByIdAndTenantId(branchId, tenantId).isPresent()) {
            throw new CommonException("Branch not found", HttpStatus.NOT_FOUND);
        }

        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (user.getBranch() == null || !user.getBranch().getId().equals(branchId)) {
            throw new CommonException("User is not assigned to this branch", HttpStatus.BAD_REQUEST);
        }

        user.setBranch(null);
        userRepository.save(user);

        return CommonResponse.builder()
                .id(userId.toString())
                .message("User removed from branch successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional(readOnly = true)
    public BranchDto.Response getMyBranch() {
        Long userId = UserContextUtil.getUserIdOrThrow();
        Long tenantId = UserContextUtil.getTenantId();
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new CommonException("User not found", HttpStatus.NOT_FOUND));

        if (user.getBranch() == null) {
            throw new CommonException("No branch assigned to current user", HttpStatus.NOT_FOUND);
        }
        return toResponse(user.getBranch());
    }

    private void clearHeadOffice(Long tenantId) {
        branchRepository.findByTenantIdAndIsHeadOfficeTrue(tenantId).ifPresent(existing -> {
            existing.setIsHeadOffice(false);
            branchRepository.save(existing);
        });
    }

    private BranchDto.Response toResponse(Branch branch) {
        int userCount = (int) userRepository.countByBranch_Id(branch.getId());
        List<AddressDto> addresses = branch.getAddresses() == null ? List.of() :
                branch.getAddresses().stream()
                        .map(a -> AddressDto.builder()
                                .id(a.getId())
                                .addressLine1(a.getAddressLine1())
                                .addressLine2(a.getAddressLine2())
                                .route(a.getRoute())
                                .area(a.getArea())
                                .city(a.getCity())
                                .state(a.getState())
                                .country(a.getCountry())
                                .pinCode(a.getPinCode())
                                .type(a.getAddressType())
                                .isPrimary(a.getIsPrimary())
                                .build())
                        .collect(Collectors.toList());

        return BranchDto.Response.builder()
                .id(branch.getId())
                .branchName(branch.getBranchName())
                .branchCode(branch.getBranchCode())
                .isHeadOffice(branch.getIsHeadOffice())
                .isActive(branch.getIsActive())
                .tenantId(branch.getTenant().getId())
                .tenantName(branch.getTenant().getTenantName())
                .userCount(userCount)
                .addresses(addresses)
                .build();
    }

    private BranchDto.UserItem toUserItem(User user) {
        return BranchDto.UserItem.builder()
                .id(user.getId())
                .userUuid(user.getUserUuid())
                .name(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .userType(user.getUserType() != null ? user.getUserType().toString() : null)
                .build();
    }
}
