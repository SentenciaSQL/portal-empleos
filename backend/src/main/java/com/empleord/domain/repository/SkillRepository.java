package com.empleord.domain.repository;

import com.empleord.domain.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Integer> {
    List<Skill> findAllByOrderByNameAsc();
    List<Skill> findByIdIn(Collection<Integer> ids);
}
