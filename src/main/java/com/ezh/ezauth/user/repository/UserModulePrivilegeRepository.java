package com.ezh.ezauth.user.repository;

import com.ezh.ezauth.common.entity.Privilege;
import com.ezh.ezauth.user.entity.UserModulePrivilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface UserModulePrivilegeRepository extends JpaRepository<UserModulePrivilege, Long> {

    @Query(
            value = """
        SELECT p.*
        FROM ezauth.user_module_privileges u
        JOIN ezauth.privileges p ON u.privilege_id = p.id
        WHERE u.module_id = :moduleId
        AND u.user_application_id = :userApplicationId
    """,
            nativeQuery = true
    )
    List<Privilege> findPrivilegesOfUser(
            @Param("moduleId") Long moduleId,
            @Param("userApplicationId") Long userApplicationId
    );
}
