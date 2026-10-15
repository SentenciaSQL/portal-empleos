package com.empleord.job;

import com.empleord.common.ApiException;
import com.empleord.common.PageResponse;
import com.empleord.domain.entity.FeaturedJob;
import com.empleord.domain.entity.Job;
import com.empleord.domain.enums.ContractType;
import com.empleord.domain.enums.ExperienceLevel;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.UserRole;
import com.empleord.domain.enums.WorkMode;
import com.empleord.domain.enums.WorkSchedule;
import com.empleord.domain.repository.FeaturedJobRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.domain.repository.SavedJobRepository;
import com.empleord.security.SecurityUtils;
import com.empleord.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobQueryService {

    private final JobRepository jobs;
    private final FeaturedJobRepository featuredJobs;
    private final SavedJobRepository savedJobs;
    private final JobApplicationRepository applications;
    private final JobMapper mapper;
    private final JobViewCounter views;

    public JobQueryService(JobRepository jobs, FeaturedJobRepository featuredJobs, SavedJobRepository savedJobs,
                           JobApplicationRepository applications, JobMapper mapper, JobViewCounter views) {
        this.jobs = jobs;
        this.featuredJobs = featuredJobs;
        this.savedJobs = savedJobs;
        this.applications = applications;
        this.mapper = mapper;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDtos.JobCard> search(String q, Integer provinceId, Integer municipalityId, Integer categoryId,
                                                ContractType contractType, WorkMode workMode, WorkSchedule workSchedule,
                                                ExperienceLevel experienceLevel, BigDecimal salaryMin, BigDecimal salaryMax,
                                                Integer publishedWithinDays, Boolean acceptsNoExperience, String sort, int page, int size) {
        Instant now = Instant.now();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Job> result = jobs.findAll(openJobs(q, provinceId, municipalityId, categoryId, contractType, workMode, workSchedule,
                experienceLevel, salaryMin, salaryMax, publishedWithinDays, acceptsNoExperience, null, sort, now), PageRequest.of(safePage, safeSize));
        return toCards(result, now);
    }

    @Transactional(readOnly = true)
    public List<Job> findPublishedForAlert(String q, Integer provinceId, Integer municipalityId, Integer categoryId,
                                           ContractType contractType, WorkMode workMode, BigDecimal salaryMin, BigDecimal salaryMax,
                                           Instant publishedAfter, int limit) {
        Instant now = Instant.now();
        return jobs.findAll(openJobs(q, provinceId, municipalityId, categoryId, contractType, workMode, null, null,
                salaryMin, salaryMax, null, null, publishedAfter, "recent", now), PageRequest.of(0, Math.min(Math.max(limit, 1), 20))).getContent();
    }

    private PageResponse<JobDtos.JobCard> toCards(Page<Job> result, Instant now) {
        List<java.util.UUID> ids = result.getContent().stream().map(Job::getId).toList();
        List<java.util.UUID> featuredIds = ids.isEmpty() ? List.of() : featuredJobs.findActiveJobIds(ids, now);
        List<JobDtos.JobCard> cards = result.getContent().stream()
                .map(job -> mapper.toCard(job, featuredIds.contains(job.getId()), now))
                .toList();
        return PageResponse.of(cards, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private Specification<Job> openJobs(String q, Integer provinceId, Integer municipalityId, Integer categoryId,
                                        ContractType contractType, WorkMode workMode, WorkSchedule workSchedule,
                                        ExperienceLevel experienceLevel, BigDecimal salaryMin, BigDecimal salaryMax,
                                        Integer publishedWithinDays, Boolean acceptsNoExperience, Instant publishedAfter,
                                        String sort, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), now)));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.join("company", JoinType.LEFT).get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (provinceId != null) {
                predicates.add(cb.equal(root.get("province").get("id"), provinceId));
            }
            if (municipalityId != null) {
                predicates.add(cb.equal(root.get("municipality").get("id"), municipalityId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (contractType != null) {
                predicates.add(cb.equal(root.get("contractType"), contractType));
            }
            if (workMode != null) {
                predicates.add(cb.equal(root.get("workMode"), workMode));
            }
            if (workSchedule != null) {
                predicates.add(cb.equal(root.get("workSchedule"), workSchedule));
            }
            if (experienceLevel != null) {
                predicates.add(cb.equal(root.get("experienceLevel"), experienceLevel));
            }
            if (Boolean.TRUE.equals(acceptsNoExperience)) {
                predicates.add(cb.isTrue(root.get("acceptsNoExperience")));
            }
            if (publishedWithinDays != null && publishedWithinDays > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), now.minus(publishedWithinDays, ChronoUnit.DAYS)));
            }
            if (publishedAfter != null) {
                predicates.add(cb.greaterThan(root.get("publishedAt"), publishedAfter));
            }
            if (salaryMin != null) {
                predicates.add(cb.or(
                        cb.isTrue(root.get("salaryNegotiable")),
                        cb.and(cb.isNull(root.get("salaryMax")), cb.greaterThanOrEqualTo(root.get("salaryMin"), salaryMin)),
                        cb.greaterThanOrEqualTo(root.get("salaryMax"), salaryMin)));
            }
            if (salaryMax != null) {
                predicates.add(cb.or(
                        cb.isTrue(root.get("salaryNegotiable")),
                        cb.isNull(root.get("salaryMin")),
                        cb.lessThanOrEqualTo(root.get("salaryMin"), salaryMax)));
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if ("salary".equals(sort)) {
                    query.orderBy(cb.desc(root.get("salaryMax")), cb.desc(root.get("publishedAt")));
                } else {
                    Subquery<Integer> featured = query.subquery(Integer.class);
                    Root<FeaturedJob> featuredRoot = featured.from(FeaturedJob.class);
                    featured.select(cb.literal(1));
                    featured.where(
                            cb.equal(featuredRoot.get("job"), root),
                            cb.lessThanOrEqualTo(featuredRoot.get("startsAt"), now),
                            cb.greaterThan(featuredRoot.get("endsAt"), now));
                    query.orderBy(cb.desc(cb.selectCase().when(cb.exists(featured), 1).otherwise(0)), cb.desc(root.get("publishedAt")));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public JobDtos.JobDetail detail(String slug) {
        Job job = jobs.findBySlugAndDeletedAtIsNull(slug).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        if (job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.REJECTED) {
            throw ApiException.notFound("La oferta no existe.");
        }
        Instant now = Instant.now();
        if (job.isOpen(now)) {
            views.increment(job.getId());
        }
        boolean featured = featuredJobs.countActive(job.getId(), now) > 0;
        UserPrincipal user = SecurityUtils.currentUser();
        boolean saved = user != null && user.getRole() == UserRole.CANDIDATE && savedJobs.existsByIdCandidateIdAndIdJobId(user.getId(), job.getId());
        boolean applied = user != null && user.getRole() == UserRole.CANDIDATE && applications.findByJobIdAndCandidateUserId(job.getId(), user.getId()).isPresent();
        List<String> skills = job.getSkills().stream().map(skill -> skill.getName()).sorted().toList();
        return new JobDtos.JobDetail(mapper.toCard(job, featured, now), job.getDescription(), job.getRequirements(), job.getResponsibilities(),
                job.getStatus().name(), job.getExpiresAt(), job.getViewCount() + (job.isOpen(now) ? 1 : 0), skills, saved, applied);
    }

}
