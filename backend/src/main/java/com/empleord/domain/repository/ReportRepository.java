package com.empleord.domain.repository;

import com.empleord.domain.entity.Report;
import com.empleord.domain.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    long countByStatus(ReportStatus status);
    long countByTargetTypeAndTargetId(String targetType, UUID targetId);
}
