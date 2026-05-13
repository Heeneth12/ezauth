package com.ezh.ezauth.common.repository;

import com.ezh.ezauth.common.entity.Address;
import com.ezh.ezauth.common.entity.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByEntityTypeAndEntityId(EntityType entityType, Long entityId);
}
