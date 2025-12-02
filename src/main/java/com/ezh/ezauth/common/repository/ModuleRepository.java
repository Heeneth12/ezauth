package com.ezh.ezauth.common.repository;

import com.ezh.ezauth.common.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuleRepository extends JpaRepository<Module, Long> {

    List<Module> findByApplicationId (Long applicationId);
}
