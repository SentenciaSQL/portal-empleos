package com.empleord.domain.repository;

import com.empleord.domain.entity.JobAlertDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAlertDeliveryRepository extends JpaRepository<JobAlertDelivery, JobAlertDelivery.Key> {
}
