package com.empleord.moderation;

import com.empleord.common.ApiException;
import com.empleord.common.Text;
import com.empleord.domain.entity.Report;
import com.empleord.domain.enums.ReportStatus;
import com.empleord.domain.repository.JobRepository;
import com.empleord.domain.repository.ReportRepository;
import com.empleord.domain.repository.UserRepository;
import com.empleord.security.SecurityUtils;
import com.empleord.security.UserPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReportService {

    private final ReportRepository reports;
    private final JobRepository jobs;
    private final UserRepository users;

    public ReportService(ReportRepository reports, JobRepository jobs, UserRepository users) {
        this.reports = reports;
        this.jobs = jobs;
        this.users = users;
    }

    @Transactional
    public void reportJob(UUID jobId, ReportRequest request) {
        if (jobs.findByIdAndDeletedAtIsNull(jobId).isEmpty()) {
            throw ApiException.notFound("La oferta no existe.");
        }
        Report report = new Report();
        UserPrincipal principal = SecurityUtils.currentUser();
        if (principal != null) {
            report.setReporter(users.findActiveById(principal.getId()).orElse(null));
            report.setReporterEmail(principal.getUsername());
        } else {
            report.setReporterEmail(Text.clean(request.email()));
        }
        report.setTargetType("JOB");
        report.setTargetId(jobId);
        report.setReason(Text.required(request.reason(), "El motivo"));
        report.setDetails(Text.clean(request.details()));
        report.setStatus(ReportStatus.OPEN);
        reports.save(report);
    }

    public record ReportRequest(
            @NotBlank @Size(max = 80) String reason,
            @Size(max = 2000) String details,
            @Email @Size(max = 255) String email
    ) {
    }
}
