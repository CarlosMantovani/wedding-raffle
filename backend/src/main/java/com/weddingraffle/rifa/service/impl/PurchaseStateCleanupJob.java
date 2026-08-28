package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.config.PurchaseStateCleanupProperties;
import com.weddingraffle.rifa.entity.PurchaseIntentAction;
import com.weddingraffle.rifa.entity.PurchaseIntentStatus;
import com.weddingraffle.rifa.repository.PurchaseIntentRepository;
import com.weddingraffle.rifa.service.CapacityReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PurchaseStateCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseStateCleanupJob.class);

    private final PurchaseStateCleanupProperties properties;
    private final CapacityReservationService capacityReservationService;
    private final PurchaseIntentRepository purchaseIntentRepository;
    private final Clock clock;

    public PurchaseStateCleanupJob(
            PurchaseStateCleanupProperties properties,
            CapacityReservationService capacityReservationService,
            PurchaseIntentRepository purchaseIntentRepository,
            Clock clock) {
        this.properties = properties;
        this.capacityReservationService = capacityReservationService;
        this.purchaseIntentRepository = purchaseIntentRepository;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.purchase-state-cleanup.initial-delay-millis:300000}",
            fixedDelayString = "${app.purchase-state-cleanup.fixed-delay-millis:300000}")
    @Transactional
    public void cleanUpStalePurchaseState() {
        if (!properties.enabled()) {
            return;
        }

        int expiredReservations = capacityReservationService.expireActiveReservations();
        int deletedIntents = purchaseIntentRepository.deleteAbandonedPendingIntents(
                PurchaseIntentAction.MERCADO_PAGO_CHECKOUT,
                PurchaseIntentStatus.PENDING,
                OffsetDateTime.now(clock).minus(Duration.ofMillis(properties.abandonedIntentAgeMillis())));

        if (expiredReservations > 0 || deletedIntents > 0) {
            LOGGER.info(
                    "Cleaned stale purchase state: expiredReservations={}, deletedPendingPurchaseIntents={}",
                    expiredReservations,
                    deletedIntents);
        }
    }
}
