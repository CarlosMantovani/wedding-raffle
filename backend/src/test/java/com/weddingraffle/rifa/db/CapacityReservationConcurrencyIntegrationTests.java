package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.weddingraffle.rifa.entity.CapacityReservationStatus;
import com.weddingraffle.rifa.entity.RaffleCapacity;
import com.weddingraffle.rifa.exception.InvalidRaffleStateException;
import com.weddingraffle.rifa.repository.CapacityReservationRepository;
import com.weddingraffle.rifa.repository.RaffleCapacityRepository;
import com.weddingraffle.rifa.service.CapacityReservationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CapacityReservationConcurrencyIntegrationTests {

    private static final String ADMIN_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("capacity_reservation")
            .withUsername("capacity_reservation")
            .withPassword("capacity_reservation");

    @Autowired
    private CapacityReservationService capacityReservationService;

    @Autowired
    private RaffleCapacityRepository raffleCapacityRepository;

    @Autowired
    private CapacityReservationRepository capacityReservationRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> ADMIN_PASSWORD_HASH);
        registry.add("spring.flyway.placeholders.raffle_unit_price", () -> "10.00");
        registry.add("spring.flyway.placeholders.raffle_number_min", () -> "00000");
        registry.add("spring.flyway.placeholders.raffle_number_max", () -> "00000");
        registry.add("app.frontend-origin", () -> "http://localhost:5173");
        registry.add("app.jwt.secret", () -> "01234567890123456789012345678901");
        registry.add("app.jwt.expiration-seconds", () -> "3600");
        registry.add("app.jwt.issuer", () -> "test");
        registry.add("app.raffle.unit-price", () -> "10.00");
        registry.add("app.raffle.number-min", () -> "00000");
        registry.add("app.raffle.number-max", () -> "00000");
        registry.add("app.mercado-pago.access-token", () -> "TEST-token");
        registry.add("app.mercado-pago.webhook-url", () -> "https://example.com/webhook");
        registry.add("app.mercado-pago.webhook-secret", () -> "secret");
        registry.add("app.mercado-pago.success-url", () -> "https://example.com/success");
        registry.add("app.mercado-pago.failure-url", () -> "https://example.com/failure");
        registry.add("app.mercado-pago.pending-url", () -> "https://example.com/pending");
        registry.add("app.mercado-pago.retry.max-attempts", () -> "1");
        registry.add("app.mercado-pago.retry.delay-millis", () -> "1");
        registry.add("app.mercado-pago.retry.multiplier", () -> "1");
    }

    @Test
    void onlyOneSimultaneousPurchaseReservesTheLastAvailableNumber() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                String reference = "concurrent-reference-" + index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        capacityReservationService.reserve(reference, 1);
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }));
            }

            ready.await();
            start.countDown();
            List<Throwable> results = futures.stream().map(this::get).toList();

            assertThat(results).hasSize(2);
            assertThat(results.stream().filter(result -> result == null)).hasSize(1);
            assertThat(results.stream().filter(InvalidRaffleStateException.class::isInstance))
                    .hasSize(1);
            RaffleCapacity capacity = raffleCapacityRepository
                    .findById(RaffleCapacity.SINGLETON_ID)
                    .orElseThrow();
            assertThat(capacity.getReservedQuantity()).isEqualTo(1);
            assertThat(capacity.getAllocatedQuantity()).isZero();
            assertThat(capacityReservationRepository.findAll())
                    .singleElement()
                    .extracting(reservation -> reservation.getStatus())
                    .isEqualTo(CapacityReservationStatus.ACTIVE);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable get(Future<Throwable> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
