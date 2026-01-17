package com.ezh.ezauth.user.repository;

import com.ezh.ezauth.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndTenant_Id(Long id, Long tenantId);

    List<User> findByTenant_Id(Long tenantId);

    Boolean existsByEmail(String email);

    @Query("""
                SELECT u FROM User u
                WHERE (:tenantId IS NULL OR u.tenant.id = :tenantId)
                AND (:userId IS NULL OR u.id = :userId)
                AND (:userUuid IS NULL OR u.userUuid = :userUuid)
                AND (:email IS NULL OR LOWER(u.email) = :email)
                AND (:phone IS NULL OR u.phone = :phone)
                AND (
                    :search IS NULL OR
                    LOWER(u.fullName) LIKE CONCAT('%', CAST(:search AS string), '%') OR
                    LOWER(u.email)    LIKE CONCAT('%', CAST(:search AS string), '%') OR
                    u.phone           LIKE CONCAT('%', CAST(:search AS string), '%')
                )
            """)
    Page<User> findUsersWithAllFilters(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("userUuid") String userUuid,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("search") String search,
            Pageable pageable
    );
}
