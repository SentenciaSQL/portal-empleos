package com.empleord.domain.repository;

import com.empleord.domain.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Integer> {
    List<Skill> findAllByOrderByNameAsc();
    List<Skill> findByIdIn(Collection<Integer> ids);
    Optional<Skill> findByNormalizedName(String normalizedName);
    List<Skill> findTop8ByNormalizedNameContainingOrderByNameAsc(String fragment);
}
