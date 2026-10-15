package com.empleord.domain.repository;

import com.empleord.domain.entity.EmployerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployerProfileRepository extends JpaRepository<EmployerProfile, UUID> {
}
