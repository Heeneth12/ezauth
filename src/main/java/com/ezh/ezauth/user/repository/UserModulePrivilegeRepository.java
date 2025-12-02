package com.ezh.ezauth.user.repository;

import com.ezh.ezauth.user.entity.UserModulePrivilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserModulePrivilegeRepository extends JpaRepository<UserModulePrivilege, Long> {
}
