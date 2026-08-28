package com.weddingraffle.rifa.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.config.PurchaseStateCleanupProperties;
import com.weddingraffle.rifa.entity.PurchaseIntentAction;
import com.weddingraffle.rifa.entity.PurchaseIntentStatus;
import com.weddingraffle.rifa.repository.PurchaseIntentRepository;
import com.weddingraffle.rifa.service.CapacityReservationService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseStateCleanupJobTests {

    private static final Instant NOW = Instant.parse("2026-08-28T13:00:00Z");

    @Mock
    private CapacityReservationService capacityReservationService;

    @Mock
    private PurchaseIntentRepository purchaseIntentRepository;

    @Test
    void expiresReservationsAndDeletesPendingOnlineIntentsOlderThanConfiguredAge() {
        PurchaseStateCleanupJob job = new PurchaseStateCleanupJob(
                new PurchaseStateCleanupProperties(true, 300_000, 300_000, 3_600_000),
                capacityReservationService,
                purchaseIntentRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(capacityReservationService.expireActiveReservations()).thenReturn(2);
        when(purchaseIntentRepository.deleteAbandonedPendingIntents(
                        PurchaseIntentAction.MERCADO_PAGO_CHECKOUT,
                        PurchaseIntentStatus.PENDING,
                        OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC)))
                .thenReturn(3);

        job.cleanUpStalePurchaseState();

        verify(capacityReservationService).expireActiveReservations();
        verify(purchaseIntentRepository)
                .deleteAbandonedPendingIntents(
                        PurchaseIntentAction.MERCADO_PAGO_CHECKOUT,
                        PurchaseIntentStatus.PENDING,
                        OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC));
    }

    @Test
    void skipsCleanupWhenDisabled() {
        PurchaseStateCleanupJob job = new PurchaseStateCleanupJob(
                new PurchaseStateCleanupProperties(false, 300_000, 300_000, 3_600_000),
                capacityReservationService,
                purchaseIntentRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        job.cleanUpStalePurchaseState();

        verify(capacityReservationService, never()).expireActiveReservations();
        verify(purchaseIntentRepository, never())
                .deleteAbandonedPendingIntents(
                        PurchaseIntentAction.MERCADO_PAGO_CHECKOUT,
                        PurchaseIntentStatus.PENDING,
                        OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC));
    }
}
