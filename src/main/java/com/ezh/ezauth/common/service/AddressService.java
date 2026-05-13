package com.ezh.ezauth.common.service;

import com.ezh.ezauth.common.dto.AddressDto;
import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.common.entity.AddressType;
import com.ezh.ezauth.common.entity.EntityType;
import com.ezh.ezauth.common.repository.AddressRepository;
import com.ezh.ezauth.utils.common.CommonResponse;
import com.ezh.ezauth.utils.common.Status;
import com.ezh.ezauth.utils.exception.CommonException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<AddressDto> getAddresses(EntityType entityType, Long entityId) {
        return addressRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AddressDto getAddress(Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));
        return toDto(address);
    }

    @Transactional
    public CommonResponse createAddress(EntityType entityType, Long entityId, AddressDto dto) {
        boolean typeExists = addressRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .anyMatch(a -> a.getAddressType() == dto.getType());

        if (typeExists) {
            throw new CommonException(
                    "Address of type " + dto.getType() + " already exists for this " + entityType.name().toLowerCase(),
                    HttpStatus.CONFLICT);
        }

        Address address = Address.builder()
                .entityType(entityType)
                .entityId(entityId)
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .route(dto.getRoute())
                .area(dto.getArea())
                .city(dto.getCity())
                .state(dto.getState())
                .country(dto.getCountry())
                .pinCode(dto.getPinCode())
                .addressType(dto.getType())
                .isPrimary(Boolean.TRUE.equals(dto.getIsPrimary()))
                .build();

        Address saved = addressRepository.save(address);

        return CommonResponse.builder()
                .id(saved.getId().toString())
                .message("Address created successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse updateAddress(Long addressId, AddressDto dto) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));

        if (dto.getType() != address.getAddressType()) {
            boolean typeConflict = addressRepository
                    .findByEntityTypeAndEntityId(address.getEntityType(), address.getEntityId())
                    .stream()
                    .anyMatch(a -> !a.getId().equals(addressId) && a.getAddressType() == dto.getType());

            if (typeConflict) {
                throw new CommonException(
                        "Address of type " + dto.getType() + " already exists",
                        HttpStatus.CONFLICT);
            }
        }

        address.setAddressLine1(dto.getAddressLine1());
        address.setAddressLine2(dto.getAddressLine2());
        address.setRoute(dto.getRoute());
        address.setArea(dto.getArea());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setCountry(dto.getCountry());
        address.setPinCode(dto.getPinCode());
        address.setAddressType(dto.getType());
        if (dto.getIsPrimary() != null) {
            address.setIsPrimary(dto.getIsPrimary());
        }

        addressRepository.save(address);

        return CommonResponse.builder()
                .id(addressId.toString())
                .message("Address updated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse deleteAddress(Long addressId) {
        if (!addressRepository.existsById(addressId)) {
            throw new CommonException("Address not found", HttpStatus.NOT_FOUND);
        }
        addressRepository.deleteById(addressId);

        return CommonResponse.builder()
                .id(addressId.toString())
                .message("Address deleted successfully")
                .status(Status.SUCCESS)
                .build();
    }

    @Transactional
    public CommonResponse setPrimaryAddress(Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new CommonException("Address not found", HttpStatus.NOT_FOUND));

        // Clear primary from all other addresses of the same entity + type
        addressRepository.findByEntityTypeAndEntityId(address.getEntityType(), address.getEntityId())
                .stream()
                .filter(a -> a.getAddressType() == address.getAddressType() && !a.getId().equals(addressId))
                .forEach(a -> {
                    a.setIsPrimary(false);
                    addressRepository.save(a);
                });

        address.setIsPrimary(true);
        addressRepository.save(address);

        return CommonResponse.builder()
                .id(addressId.toString())
                .message("Primary address updated successfully")
                .status(Status.SUCCESS)
                .build();
    }

    public List<String> getAddressTypes() {
        return Arrays.stream(AddressType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    private AddressDto toDto(Address address) {
        return AddressDto.builder()
                .id(address.getId())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .route(address.getRoute())
                .area(address.getArea())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .pinCode(address.getPinCode())
                .type(address.getAddressType())
                .isPrimary(address.getIsPrimary())
                .build();
    }
}
