package com.empleord.domain.repository;

import com.empleord.domain.entity.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MunicipalityRepository extends JpaRepository<Municipality, Integer> {
    List<Municipality> findByProvinceIdOrderByNameAsc(Integer provinceId);
}
