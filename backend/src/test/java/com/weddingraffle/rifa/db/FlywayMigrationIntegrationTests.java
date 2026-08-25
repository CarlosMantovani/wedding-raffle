package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTests {

    private static final String ADMIN_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String RAFFLE_UNIT_PRICE = "10.00";
    private static final String RAFFLE_NUMBER_MIN = "00000";
    private static final String RAFFLE_NUMBER_MAX = "99999";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wedding_raffle")
            .withUsername("wedding_raffle")
            .withPassword("wedding_raffle");

    @Test
    void appliesAllDatabaseMigrations() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "admin_username", "admin",
                        "admin_password_hash", ADMIN_PASSWORD_HASH,
                        "raffle_unit_price", RAFFLE_UNIT_PRICE,
                        "raffle_number_min", RAFFLE_NUMBER_MIN,
                        "raffle_number_max", RAFFLE_NUMBER_MAX))
                .load();

        flyway.migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            assertThat(tableExists(statement, "transaction")).isTrue();
            assertThat(tableExists(statement, "lucky_number")).isTrue();
            assertThat(tableExists(statement, "raffle_draw")).isTrue();
            assertThat(tableExists(statement, "admin_user")).isTrue();
            assertThat(tableExists(statement, "raffle_config")).isTrue();
            assertThat(tableExists(statement, "raffle_capacity")).isTrue();
            assertThat(tableExists(statement, "capacity_reservation")).isTrue();
            assertThat(tableExists(statement, "purchase_intent")).isTrue();
            assertThat(tableExists(statement, "provider_payment")).isTrue();
            assertThat(tableExists(statement, "payment_event")).isTrue();
            assertThat(tableExists(statement, "raffle_combo")).isTrue();
            assertThat(indexExists(statement, "idx_transaction_email")).isTrue();
            assertThat(indexExists(statement, "idx_transaction_external_reference"))
                    .isTrue();
            assertThat(indexExists(statement, "idx_transaction_status")).isTrue();
            assertThat(indexExists(statement, "idx_lucky_number_email")).isTrue();
            assertThat(indexExists(statement, "idx_provider_payment_transaction_id"))
                    .isTrue();
            assertThat(indexExists(statement, "idx_payment_event_history")).isTrue();
            assertThat(columnExists(statement, "transaction", "confirmation_email_sent_at"))
                    .isFalse();
            assertThat(columnExists(statement, "transaction", "confirmation_email_failed_at"))
                    .isFalse();
            assertThat(columnExists(statement, "transaction", "confirmation_email_last_error"))
                    .isFalse();
            assertThat(columnExists(statement, "transaction", "unit_price")).isTrue();
            assertThat(columnExists(statement, "purchase_intent", "request_hash"))
                    .isTrue();
            assertThat(columnExists(statement, "purchase_intent", "response_payload"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "mp_collector_id"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "payment_state_updated_at"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "current_payment_event_id"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "lucky_numbers_generated_at"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "payment_reconciliation_attempted_at"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "payment_reconciliation_lease_until"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "payment_reconciliation_lease_token"))
                    .isTrue();
            assertThat(columnExists(statement, "transaction", "raffle_combo_id"))
                    .isTrue();
            assertThat(columnIsNullable(statement, "transaction", "participant_flag_code"))
                    .isTrue();
            assertThat(columnIsNullable(statement, "transaction", "participant_flag_name"))
                    .isTrue();
            assertThat(columnIsNullable(statement, "transaction", "participant_flag_emoji"))
                    .isTrue();
            assertThat(columnExists(statement, "lucky_number", "allocation_index"))
                    .isTrue();
            assertThat(constraintExists(statement, "uq_lucky_number_transaction_allocation_index"))
                    .isTrue();
            assertThat(triggerExists(statement, "trg_transaction_lucky_number_batch_integrity"))
                    .isTrue();
            assertThat(triggerExists(statement, "trg_lucky_number_batch_integrity_insert"))
                    .isTrue();
            assertThat(triggerExists(statement, "trg_lucky_number_batch_integrity_update"))
                    .isTrue();
            assertThat(triggerExists(statement, "trg_lucky_number_batch_integrity_delete"))
                    .isTrue();
            assertThat(adminSeedExists(statement)).isTrue();
            assertThat(
                            singleString(
                                    statement,
                                    "select string_agg(quantity || ':' || price, ',' order by display_order) from raffle_combo"))
                    .isEqualTo("5:240.00,10:460.00,20:880.00,30:1275.00");
            assertThat(approvedFlagRankingQueryWorks(statement)).isTrue();
            assertThat(adminTransactionSummaryQueryWorks(statement)).isTrue();
        }
    }

    @Test
    void migratesExistingPaymentIdentifiersIntoAnUnverifiedLegacyLedger() throws SQLException {
        String schema = "legacy_payment_ledger";
        Flyway beforeLedger = flyway(schema, MigrationVersion.fromVersion("15"));
        beforeLedger.migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.executeUpdate(
                    """
                    insert into transaction (
                        name,
                        phone,
                        quantity,
                        total_amount,
                        unit_price,
                        status,
                        payment_method,
                        external_reference,
                        recovery_code,
                        participant_flag_code,
                        participant_flag_name,
                        participant_flag_emoji,
                        mp_payment_id,
                        mp_preference_id
                    ) values (
                        'Legacy Buyer',
                        '11999999999',
                        2,
                        20.00,
                        10.00,
                        'APPROVED',
                        'MERCADO_PAGO',
                        'legacy-external-reference',
                        '4821',
                        'BRAZIL',
                        'Brasil',
                        'BR',
                        'legacy-payment-123',
                        '456-preference-123'
                    )
                    """);
        }

        flyway(schema, null).migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            assertThat(singleString(statement, "select mp_collector_id from transaction"))
                    .isEqualTo("456");
            assertThat(singleLong(statement, "select count(*) from provider_payment"))
                    .isEqualTo(1);
            assertThat(singleString(statement, "select failure_reasons from payment_event"))
                    .isEqualTo("LEGACY_UNVERIFIED");
        }
    }

    @Test
    void migratesAnExistingExactLuckyNumberBatchAndPersistsItsMarker() throws SQLException {
        String schema = "legacy_exact_lucky_number_batch";
        flyway(schema, MigrationVersion.fromVersion("16")).migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            long transactionId = insertLegacyTransaction(statement, "legacy-exact-batch", 2);
            statement.executeUpdate("insert into lucky_number (number, transaction_id) values ('00001', "
                    + transactionId + "), ('00002', " + transactionId + ")");
        }

        flyway(schema, null).migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            assertThat(singleLong(
                            statement, "select count(*) from transaction where lucky_numbers_generated_at is not null"))
                    .isEqualTo(1);
            assertThat(
                            singleString(
                                    statement,
                                    "select string_agg(allocation_index::text, ',' order by allocation_index) from lucky_number"))
                    .isEqualTo("1,2");
        }
    }

    @Test
    void refusesToMarkAnExistingPartialLuckyNumberBatchAsCompleted() throws SQLException {
        String schema = "legacy_partial_lucky_number_batch";
        flyway(schema, MigrationVersion.fromVersion("16")).migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            long transactionId = insertLegacyTransaction(statement, "legacy-partial-batch", 2);
            statement.executeUpdate(
                    "insert into lucky_number (number, transaction_id) values ('00001', " + transactionId + ")");
        }

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("persisted count differs from transaction quantity");
    }

    @Test
    void removesFlagsFromPendingTransactionsAndAllowsDeletionWithoutFlagRelease() throws SQLException {
        String schema = "pending_transaction_flag_cleanup";
        flyway(schema, MigrationVersion.fromVersion("22")).migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.executeUpdate(
                    """
                    insert into transaction (
                        name,
                        phone,
                        quantity,
                        total_amount,
                        unit_price,
                        status,
                        payment_method,
                        external_reference,
                        recovery_code,
                        participant_flag_code,
                        participant_flag_name,
                        participant_flag_emoji
                    ) values (
                        'Pending Buyer',
                        '11999999999',
                        1,
                        10.00,
                        10.00,
                        'PENDING',
                        'MERCADO_PAGO',
                        'pending-flag-cleanup',
                        '4821',
                        'BRAZIL',
                        'Brasil',
                        '🇧🇷'
                    )
                    """);
        }

        flyway(schema, null).migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            assertThat(
                            singleLong(
                                    statement,
                                    "select count(*) from transaction where external_reference = 'pending-flag-cleanup' and participant_flag_code is null and participant_flag_name is null and participant_flag_emoji is null"))
                    .isEqualTo(1);
            assertThat(statement.executeUpdate(
                            "delete from transaction where external_reference = 'pending-flag-cleanup'"))
                    .isEqualTo(1);
        }
    }

    private static Flyway flyway(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .locations("classpath:db/migration")
                .placeholders(Map.of(
                        "admin_username", "admin",
                        "admin_password_hash", ADMIN_PASSWORD_HASH,
                        "raffle_unit_price", RAFFLE_UNIT_PRICE,
                        "raffle_number_min", RAFFLE_NUMBER_MIN,
                        "raffle_number_max", RAFFLE_NUMBER_MAX));
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static String singleString(Statement statement, String query) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static long singleLong(Statement statement, String query) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static boolean tableExists(Statement statement, String tableName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = '"
                        + tableName + "')")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean indexExists(Statement statement, String indexName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select exists (select 1 from pg_indexes where schemaname = 'public' and indexname = '" + indexName
                        + "')")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean columnExists(Statement statement, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = '"
                        + tableName + "' and column_name = '" + columnName + "')")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean columnIsNullable(Statement statement, String tableName, String columnName)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select is_nullable from information_schema.columns where table_schema = current_schema() and table_name = '"
                        + tableName + "' and column_name = '" + columnName + "'")) {
            resultSet.next();
            return "YES".equals(resultSet.getString(1));
        }
    }

    private static boolean constraintExists(Statement statement, String constraintName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select exists (select 1 from pg_constraint where connamespace = 'public'::regnamespace and conname = '"
                        + constraintName + "')")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean triggerExists(Statement statement, String triggerName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select exists (select 1 from pg_trigger where not tgisinternal and tgname = '" + triggerName + "')")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean adminSeedExists(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "select exists (select 1 from admin_user where username = 'admin' and char_length(password_hash) = 60)")) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean approvedFlagRankingQueryWorks(Statement statement) throws SQLException {
        statement.executeUpdate(
                """
                insert into transaction (
                    name,
                    phone,
                    email,
                    quantity,
                    total_amount,
                    unit_price,
                    status,
                    payment_method,
                    external_reference,
                    recovery_code,
                    participant_flag_code,
                    participant_flag_name,
                    participant_flag_emoji
                ) values (
                    'Older Buyer',
                    '44999999999',
                    'older@example.com',
                    3,
                    30.00,
                    10.00,
                    'APPROVED',
                    'CASH',
                    'external-reference-ranking-older',
                    '4821',
                    'BRAZIL',
                    'Brasil',
                    '🇧🇷'
                )
                """);
        statement.executeUpdate(
                """
                insert into transaction (
                    name,
                    phone,
                    email,
                    quantity,
                    total_amount,
                    unit_price,
                    status,
                    payment_method,
                    external_reference,
                    recovery_code,
                    participant_flag_code,
                    participant_flag_name,
                    participant_flag_emoji,
                    created_at
                ) values (
                    'Recent Buyer',
                    '44999999998',
                    'recent@example.com',
                    3,
                    30.00,
                    10.00,
                    'APPROVED',
                    'CASH',
                    'external-reference-ranking-recent',
                    '4822',
                    'CANADA',
                    'Canada',
                    'CA',
                    '2030-08-21T10:00:00-03:00'
                )
                """);
        insertCapacityReviewTransaction(
                statement,
                "capacity-review-contribution",
                "CONTRIBUTION_WITHOUT_NUMBERS",
                100,
                "1000.00",
                "UNITED_STATES");
        insertCapacityReviewTransaction(
                statement, "capacity-review-refund", "REFUND_COMPLETED", 50, "500.00", "MEXICO");
        insertCapacityReviewTransaction(statement, "capacity-review-pending", "PENDING", 20, "200.00", "FRANCE");

        try (ResultSet resultSet = statement.executeQuery(
                """
                select
                    participant_flag_code as code,
                    participant_flag_name as name,
                    participant_flag_emoji as emoji,
                    cast(sum(quantity) as bigint) as total_numbers
                from transaction
                where status = 'APPROVED'
                  and capacity_review_status is null
                group by participant_flag_code, participant_flag_name, participant_flag_emoji
                order by sum(quantity) desc, max(created_at) desc, participant_flag_name asc
                """)) {
            return resultSet.next()
                    && "CANADA".equals(resultSet.getString("code"))
                    && resultSet.getLong("total_numbers") == 3L;
        }
    }

    private static boolean adminTransactionSummaryQueryWorks(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                """
                select
                    cast(count(id) as bigint) as total_transactions,
                    cast(coalesce(sum(case
                        when status = 'APPROVED' and capacity_review_status is null then quantity
                        else 0
                    end), 0) as bigint)
                        as approved_lucky_numbers,
                    coalesce(sum(case
                        when status = 'APPROVED'
                            and capacity_review_status is distinct from 'REFUND_COMPLETED'
                        then total_amount
                        else 0
                    end), 0)
                        as approved_revenue
                from transaction
                """)) {
            return resultSet.next()
                    && resultSet.getLong("total_transactions") == 5L
                    && resultSet.getLong("approved_lucky_numbers") == 6L
                    && resultSet.getBigDecimal("approved_revenue").compareTo(new java.math.BigDecimal("1260.00")) == 0;
        }
    }

    private static void insertCapacityReviewTransaction(
            Statement statement,
            String externalReference,
            String capacityReviewStatus,
            int quantity,
            String totalAmount,
            String flagCode)
            throws SQLException {
        statement.executeUpdate(
                """
                insert into transaction (
                    name,
                    phone,
                    quantity,
                    total_amount,
                    unit_price,
                    status,
                    payment_method,
                    external_reference,
                    recovery_code,
                    participant_flag_code,
                    participant_flag_name,
                    participant_flag_emoji,
                    capacity_review_status
                ) values (
                    'Capacity Review Buyer',
                    '11999999999',
                    %d,
                    %s,
                    10.00,
                    'APPROVED',
                    'MERCADO_PAGO',
                    '%s',
                    '4821',
                    '%s',
                    '%s',
                    'FLAG',
                    '%s'
                )
                """
                        .formatted(quantity, totalAmount, externalReference, flagCode, flagCode, capacityReviewStatus));
    }

    private static long insertLegacyTransaction(Statement statement, String externalReference, int quantity)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                """
                insert into transaction (
                    name,
                    phone,
                    quantity,
                    total_amount,
                    unit_price,
                    status,
                    payment_method,
                    external_reference,
                    recovery_code,
                    participant_flag_code,
                    participant_flag_name,
                    participant_flag_emoji
                ) values (
                    'Legacy Batch Buyer',
                    '11999999999',
                    %d,
                    %d0.00,
                    10.00,
                    'APPROVED',
                    'CASH',
                    '%s',
                    '4821',
                    'BRAZIL',
                    'Brasil',
                    'BR'
                )
                returning id
                """
                        .formatted(quantity, quantity, externalReference))) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
