package com.ezh.ezauth.common.repository;

import com.ezh.ezauth.common.entity.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    Optional<Privilege> findByModule_IdAndId(Long moduleId, Long privilegeId);

}
