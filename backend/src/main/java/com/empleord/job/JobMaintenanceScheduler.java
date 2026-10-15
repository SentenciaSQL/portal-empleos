package com.empleord.job;

import com.empleord.domain.entity.Job;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.NotificationType;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.notification.EmailService;
import com.empleord.notification.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JobMaintenanceScheduler {

    private final JobRepository jobs;
    private final CompanyMemberRepository members;
    private final NotificationService notifications;
    private final EmailService email;

    public JobMaintenanceScheduler(JobRepository jobs, CompanyMemberRepository members, NotificationService notifications, EmailService email) {
        this.jobs = jobs;
        this.members = members;
        this.notifications = notifications;
        this.email = email;
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "America/Santo_Domingo")
    @Transactional
    public void maintain() {
        Instant now = Instant.now();
        for (Job job : jobs.findExpiredPublished(now)) {
            job.setStatus(JobStatus.EXPIRED);
        }
        Instant until = now.plus(3, ChronoUnit.DAYS);
        for (Job job : jobs.findExpiringBetween(now, until)) {
            String link = "/empresa/ofertas";
            members.findByCompanyId(job.getCompany().getId()).forEach(member -> {
                if (!notifications.alreadySent(member.getUser().getId(), NotificationType.JOB_EXPIRING, link + "?job=" + job.getId())) {
                    String locale = member.getUser().getLocale();
                    notifications.notify(member.getUser(), NotificationType.JOB_EXPIRING,
                            com.empleord.common.Texts.forUser(locale, "Tu oferta está por vencer", "Your job post is about to expire"),
                            com.empleord.common.Texts.forUser(locale, job.getTitle() + " deja de estar publicada en los próximos días.", job.getTitle() + " stops being published in the next few days."),
                            link);
                    email.sendJobExpiring(member.getUser(), job.getTitle());
                }
            });
        }
    }
}
