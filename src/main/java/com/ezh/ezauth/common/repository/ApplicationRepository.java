package com.ezh.ezauth.common.repository;

import com.ezh.ezauth.common.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface ApplicationRepository  extends JpaRepository<Application, Long> {

    Optional<Application> findByAppKey(String appKey);
}
