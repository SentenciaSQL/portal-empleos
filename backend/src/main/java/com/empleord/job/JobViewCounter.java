package com.empleord.job;

import com.empleord.domain.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobViewCounter {

    private final JobRepository jobs;

    public JobViewCounter(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(UUID id) {
        jobs.incrementViews(id);
    }
}
