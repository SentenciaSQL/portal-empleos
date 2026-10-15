package com.empleord.candidate;

import com.empleord.billing.BillingService;
import com.empleord.domain.enums.AlertFrequency;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobAlertScheduler {

    private final JobAlertService alerts;
    private final BillingService billing;

    public JobAlertScheduler(JobAlertService alerts, BillingService billing) {
        this.alerts = alerts;
        this.billing = billing;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "America/Santo_Domingo")
    public void immediate() {
        alerts.deliver(AlertFrequency.IMMEDIATE);
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "America/Santo_Domingo")
    public void daily() {
        alerts.deliver(AlertFrequency.DAILY);
    }

    @Scheduled(cron = "0 45 8 * * MON", zone = "America/Santo_Domingo")
    public void weekly() {
        alerts.deliver(AlertFrequency.WEEKLY);
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "America/Santo_Domingo")
    public void expireSubscriptions() {
        billing.expireDueSubscriptions();
    }
}
