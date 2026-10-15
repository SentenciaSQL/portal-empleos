package com.empleord.candidate;

import com.empleord.common.ApiException;
import com.empleord.domain.entity.Job;
import com.empleord.domain.entity.SavedJob;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.repository.JobRepository;
import com.empleord.domain.repository.SavedJobRepository;
import com.empleord.job.JobDtos;
import com.empleord.job.JobMapper;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SavedJobService {

    private final SavedJobRepository savedJobs;
    private final JobRepository jobs;
    private final CandidateProfileService profiles;
    private final JobMapper mapper;

    public SavedJobService(SavedJobRepository savedJobs, JobRepository jobs, CandidateProfileService profiles, JobMapper mapper) {
        this.savedJobs = savedJobs;
        this.jobs = jobs;
        this.profiles = profiles;
        this.mapper = mapper;
    }

    @Transactional
    public void save(UUID jobId) {
        Job job = jobs.findByIdAndDeletedAtIsNull(jobId).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        if (job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.REJECTED) {
            throw ApiException.notFound("La oferta no existe.");
        }
        UUID candidateId = SecurityUtils.requireUser().getId();
        if (savedJobs.existsByIdCandidateIdAndIdJobId(candidateId, jobId)) {
            return;
        }
        SavedJob saved = new SavedJob();
        saved.setId(new SavedJob.SavedJobId(candidateId, jobId));
        saved.setCandidate(profiles.requireProfile());
        saved.setJob(job);
        savedJobs.save(saved);
    }

    @Transactional
    public void remove(UUID jobId) {
        savedJobs.deleteByIdCandidateIdAndIdJobId(SecurityUtils.requireUser().getId(), jobId);
    }

    @Transactional(readOnly = true)
    public List<JobDtos.JobCard> list() {
        Instant now = Instant.now();
        return savedJobs.findByCandidateUserIdOrderByCreatedAtDesc(SecurityUtils.requireUser().getId()).stream()
                .map(saved -> mapper.toCard(saved.getJob(), false, now))
                .toList();
    }
}
