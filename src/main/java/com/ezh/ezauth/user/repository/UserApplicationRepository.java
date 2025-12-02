package com.ezh.ezauth.user.repository;


import com.ezh.ezauth.user.entity.UserApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserApplicationRepository extends JpaRepository<UserApplication, Long> {
}
