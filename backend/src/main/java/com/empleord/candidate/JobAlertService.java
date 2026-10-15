package com.empleord.candidate;

import com.empleord.catalog.CatalogService;
import com.empleord.common.ApiException;
import com.empleord.domain.entity.CandidateProfile;
import com.empleord.domain.entity.Job;
import com.empleord.domain.entity.JobAlert;
import com.empleord.domain.entity.JobAlertDelivery;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.AlertFrequency;
import com.empleord.domain.enums.ContractType;
import com.empleord.domain.enums.NotificationType;
import com.empleord.domain.enums.WorkMode;
import com.empleord.domain.repository.CandidateProfileRepository;
import com.empleord.domain.repository.FeaturedJobRepository;
import com.empleord.domain.repository.JobAlertDeliveryRepository;
import com.empleord.domain.repository.JobAlertRepository;
import com.empleord.job.JobDtos;
import com.empleord.job.JobMapper;
import com.empleord.job.JobQueryService;
import com.empleord.notification.EmailService;
import com.empleord.notification.NotificationService;
import com.empleord.security.SecurityUtils;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JobAlertService {

    private static final int MAX_ALERTS = 10;

    private final JobAlertRepository alerts;
    private final JobAlertDeliveryRepository deliveries;
    private final CandidateProfileRepository profiles;
    private final CatalogService catalog;
    private final JobQueryService jobs;
    private final JobMapper mapper;
    private final FeaturedJobRepository featuredJobs;
    private final NotificationService notifications;
    private final EmailService email;

    public JobAlertService(JobAlertRepository alerts, JobAlertDeliveryRepository deliveries, CandidateProfileRepository profiles,
                           CatalogService catalog, JobQueryService jobs, JobMapper mapper, FeaturedJobRepository featuredJobs,
                           NotificationService notifications, EmailService email) {
        this.alerts = alerts;
        this.deliveries = deliveries;
        this.profiles = profiles;
        this.catalog = catalog;
        this.jobs = jobs;
        this.mapper = mapper;
        this.featuredJobs = featuredJobs;
        this.notifications = notifications;
        this.email = email;
    }

    @Transactional(readOnly = true)
    public List<AlertItem> list() {
        return alerts.findByCandidateUserIdOrderByCreatedAtDesc(SecurityUtils.requireUser().getId()).stream().map(this::toItem).toList();
    }

    @Transactional
    public AlertItem create(AlertRequest request) {
        CandidateProfile profile = profiles.findById(SecurityUtils.requireUser().getId())
                .orElseThrow(() -> ApiException.notFound("El perfil de candidato no existe."));
        if (alerts.countByCandidateUserId(profile.getUserId()) >= MAX_ALERTS) {
            throw ApiException.conflict("Puedes tener hasta 10 alertas.");
        }
        JobAlert alert = new JobAlert();
        alert.setCandidate(profile);
        apply(alert, request);
        return toItem(alerts.save(alert));
    }

    @Transactional
    public AlertItem update(UUID id, AlertRequest request) {
        JobAlert alert = owned(id);
        apply(alert, request);
        return toItem(alert);
    }

    @Transactional
    public AlertItem setActive(UUID id, boolean active) {
        JobAlert alert = owned(id);
        alert.setActive(active);
        return toItem(alert);
    }

    @Transactional
    public void delete(UUID id) {
        alerts.delete(owned(id));
    }

    @Transactional(readOnly = true)
    public List<JobDtos.JobCard> matches(UUID id) {
        JobAlert alert = owned(id);
        return cards(jobs.findPublishedForAlert(alert.getKeyword(), id(alert.getProvince()), id(alert.getMunicipality()), id(alert.getCategory()),
                alert.getContractType(), alert.getWorkMode(), alert.getSalaryMin(), alert.getSalaryMax(), null, 20));
    }

    @Transactional
    public void deliver(AlertFrequency frequency) {
        Instant now = Instant.now();
        for (JobAlert alert : alerts.findByActiveTrueAndFrequency(frequency)) {
            if (tooSoon(alert, now)) {
                continue;
            }
            Instant started = Instant.now();
            Instant cutoff = alert.getLastNotifiedAt() == null ? alert.getCreatedAt() : alert.getLastNotifiedAt();
            List<Job> found = jobs.findPublishedForAlert(alert.getKeyword(), id(alert.getProvince()), id(alert.getMunicipality()), id(alert.getCategory()),
                    alert.getContractType(), alert.getWorkMode(), alert.getSalaryMin(), alert.getSalaryMax(), cutoff, 20);
            List<Job> fresh = new ArrayList<>();
            for (Job job : found) {
                if (!deliveries.existsById(new JobAlertDelivery.Key(alert.getId(), job.getId()))) {
                    fresh.add(job);
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }
            User user = alert.getCandidate().getUser();
            boolean english = "en".equalsIgnoreCase(user.getLocale());
            String title = english ? "New jobs for your alert" : "Nuevas ofertas para tu alerta";
            String summary = english
                    ? fresh.size() + " new job" + (fresh.size() == 1 ? "" : "s") + " match your alert."
                    : fresh.size() + (fresh.size() == 1 ? " oferta nueva coincide" : " ofertas nuevas coinciden") + " con tu alerta.";
            String link = "/candidato/alertas?alerta=" + alert.getId();
            notifications.notify(user, NotificationType.JOB_ALERT, title, summary + " " + fresh.getFirst().getTitle(), link);
            email.sendJobAlert(user, summary, fresh.stream().map(Job::getTitle).toList(), link);
            Instant stamp = Instant.now();
            for (Job job : fresh) {
                JobAlertDelivery delivery = new JobAlertDelivery();
                delivery.setId(new JobAlertDelivery.Key(alert.getId(), job.getId()));
                delivery.setNotifiedAt(stamp);
                deliveries.save(delivery);
            }
            alert.setLastNotifiedAt(started);
        }
    }

    private boolean tooSoon(JobAlert alert, Instant now) {
        if (alert.getLastNotifiedAt() == null || alert.getFrequency() == AlertFrequency.IMMEDIATE) {
            return false;
        }
        long hours = alert.getFrequency() == AlertFrequency.WEEKLY ? 24L * 7L : 24L;
        return alert.getLastNotifiedAt().isAfter(now.minus(hours, ChronoUnit.HOURS));
    }

    private void apply(JobAlert alert, AlertRequest request) {
        String keyword = request.keyword() == null ? null : request.keyword().trim();
        if (keyword != null && keyword.isBlank()) {
            keyword = null;
        }
        if (keyword == null && request.provinceId() == null && request.municipalityId() == null && request.categoryId() == null
                && request.workMode() == null && request.contractType() == null && request.salaryMin() == null && request.salaryMax() == null) {
            throw ApiException.badRequest("Indica al menos un criterio: palabra clave, lugar, categoría, modalidad, contrato o salario.");
        }
        if (request.salaryMin() != null && request.salaryMin().signum() < 0 || request.salaryMax() != null && request.salaryMax().signum() < 0) {
            throw ApiException.badRequest("El salario no puede ser negativo.");
        }
        if (request.salaryMin() != null && request.salaryMax() != null && request.salaryMin().compareTo(request.salaryMax()) > 0) {
            throw ApiException.badRequest("El salario mínimo no puede ser mayor que el máximo.");
        }
        if (request.frequency() == null) {
            throw ApiException.badRequest("La frecuencia no es válida.");
        }
        alert.setKeyword(keyword);
        alert.setProvince(catalog.province(request.provinceId()));
        alert.setMunicipality(catalog.municipality(request.municipalityId(), alert.getProvince()));
        alert.setCategory(request.categoryId() == null ? null : catalog.category(request.categoryId()));
        alert.setWorkMode(request.workMode());
        alert.setContractType(request.contractType());
        alert.setSalaryMin(request.salaryMin());
        alert.setSalaryMax(request.salaryMax());
        alert.setSalaryCurrency("DOP");
        alert.setFrequency(request.frequency());
        if (request.active() != null) {
            alert.setActive(request.active());
        }
    }

    private JobAlert owned(UUID id) {
        return alerts.findByIdAndCandidateUserId(id, SecurityUtils.requireUser().getId())
                .orElseThrow(() -> ApiException.notFound("La alerta no existe."));
    }

    private List<JobDtos.JobCard> cards(List<Job> found) {
        Instant now = Instant.now();
        List<UUID> ids = found.stream().map(Job::getId).toList();
        List<UUID> featured = ids.isEmpty() ? List.of() : featuredJobs.findActiveJobIds(ids, now);
        return found.stream().map(job -> mapper.toCard(job, featured.contains(job.getId()), now)).toList();
    }

    private AlertItem toItem(JobAlert alert) {
        return new AlertItem(alert.getId(), alert.getKeyword(), id(alert.getProvince()), alert.getProvince() == null ? null : alert.getProvince().getName(),
                id(alert.getMunicipality()), alert.getMunicipality() == null ? null : alert.getMunicipality().getName(),
                id(alert.getCategory()), alert.getCategory() == null ? null : alert.getCategory().getName(),
                alert.getWorkMode() == null ? null : alert.getWorkMode().name(),
                alert.getContractType() == null ? null : alert.getContractType().name(),
                alert.getSalaryMin(), alert.getSalaryMax(), alert.getSalaryCurrency(), alert.getFrequency().name(), alert.isActive(), alert.getLastNotifiedAt());
    }

    private Integer id(com.empleord.domain.entity.Province province) {
        return province == null ? null : province.getId();
    }

    private Integer id(com.empleord.domain.entity.Municipality municipality) {
        return municipality == null ? null : municipality.getId();
    }

    private Integer id(com.empleord.domain.entity.JobCategory category) {
        return category == null ? null : category.getId();
    }

    public record AlertRequest(
            @Size(max = 160) String keyword,
            Integer provinceId,
            Integer municipalityId,
            Integer categoryId,
            WorkMode workMode,
            ContractType contractType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            @NotNull AlertFrequency frequency,
            Boolean active
    ) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record AlertItem(
            UUID id,
            String keyword,
            Integer provinceId,
            String provinceName,
            Integer municipalityId,
            String municipalityName,
            Integer categoryId,
            String categoryName,
            String workMode,
            String contractType,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String frequency,
            boolean active,
            Instant lastNotifiedAt
    ) {
    }
}
