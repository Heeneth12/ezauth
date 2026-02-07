package com.ezh.ezauth.common.repository;

import com.ezh.ezauth.common.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleRepository extends JpaRepository<Module, Long> {

    List<Module> findByApplicationId(Long applicationId);

    Optional<Module> findByModuleKey(String moduleKey);
}
