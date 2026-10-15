package com.empleord.domain.repository;

import com.empleord.domain.entity.CompanyVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyVerificationRepository extends JpaRepository<CompanyVerification, UUID> {
}
