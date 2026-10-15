package com.empleord.domain.repository;

import com.empleord.domain.entity.JobCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Integer> {
    List<JobCategory> findAllByOrderByNameAsc();
    Optional<JobCategory> findBySlug(String slug);
}
