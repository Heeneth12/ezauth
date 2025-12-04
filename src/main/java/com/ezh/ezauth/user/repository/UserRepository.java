package com.ezh.ezauth.user.repository;

import com.ezh.ezauth.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByTenant_Id(Long tenantId);

    Boolean existsByEmail(String email);
}
