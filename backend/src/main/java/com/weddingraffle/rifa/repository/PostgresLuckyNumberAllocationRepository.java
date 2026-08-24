package com.weddingraffle.rifa.repository;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Claims lucky numbers with PostgreSQL conflict handling so concurrent allocators can retain every
 * candidate that won the unique-index race and retry only the missing quantity.
 */
@Repository
public class PostgresLuckyNumberAllocationRepository implements LuckyNumberAllocationRepository {

    private static final String INSERT_CANDIDATES_SQL =
            """
            WITH candidates(number, allocation_index) AS (
                SELECT number, allocation_index
                FROM unnest(CAST(? AS text[]), CAST(? AS integer[]))
                    AS candidate(number, allocation_index)
            )
            INSERT INTO lucky_number (number, email, transaction_id, allocation_index)
            SELECT number, ?, ?, allocation_index
            FROM candidates
            ON CONFLICT DO NOTHING
            RETURNING number
            """;

    private static final String INSERT_RANDOM_AVAILABLE_SQL =
            """
            WITH available AS MATERIALIZED (
                SELECT formatted.number
                FROM generate_series(?, ?) AS candidate(value)
                CROSS JOIN LATERAL (
                    SELECT lpad(candidate.value::text, ?, '0') AS number
                ) formatted
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM lucky_number existing
                    WHERE existing.number = formatted.number
                )
                ORDER BY hashtextextended(formatted.number, CAST(? AS bigint))
                LIMIT ?
            ),
            numbered AS (
                SELECT
                    number,
                    CAST(? + row_number() OVER (
                        ORDER BY hashtextextended(number, CAST(? AS bigint))
                    ) - 1 AS integer) AS allocation_index
                FROM available
            )
            INSERT INTO lucky_number (number, email, transaction_id, allocation_index)
            SELECT number, ?, ?, allocation_index
            FROM numbered
            ON CONFLICT DO NOTHING
            RETURNING number
            """;

    private static final String COUNT_AVAILABLE_SQL =
            """
            SELECT count(*)
            FROM generate_series(?, ?) AS candidate(value)
            WHERE NOT EXISTS (
                SELECT 1
                FROM lucky_number existing
                WHERE existing.number = lpad(candidate.value::text, ?, '0')
            )
            """;

    private static final String COMPACT_INDEXES_SQL =
            """
            WITH ranked AS (
                SELECT
                    id,
                    CAST(row_number() OVER (ORDER BY id) AS integer) AS allocation_index
                FROM lucky_number
                WHERE transaction_id = ?
            )
            UPDATE lucky_number allocated
            SET allocation_index = ranked.allocation_index
            FROM ranked
            WHERE allocated.id = ranked.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresLuckyNumberAllocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> insertCandidates(
            long transactionId, String email, List<LuckyNumberAllocationCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.execute((ConnectionCallback<List<String>>)
                connection -> insertCandidates(connection, transactionId, email, candidates));
    }

    @Override
    public List<String> insertRandomAvailable(
            long transactionId,
            String email,
            int numberMin,
            int numberMax,
            int numberWidth,
            int batchSize,
            int firstTemporaryIndex,
            int randomSeed) {
        return jdbcTemplate.query(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_RANDOM_AVAILABLE_SQL);
                    statement.setInt(1, numberMin);
                    statement.setInt(2, numberMax);
                    statement.setInt(3, numberWidth);
                    statement.setInt(4, randomSeed);
                    statement.setInt(5, batchSize);
                    statement.setInt(6, firstTemporaryIndex);
                    statement.setInt(7, randomSeed);
                    setNullableString(statement, 8, email);
                    statement.setLong(9, transactionId);
                    return statement;
                },
                (resultSet, rowNumber) -> resultSet.getString("number"));
    }

    @Override
    public long countAvailable(int numberMin, int numberMax, int numberWidth) {
        Long available =
                jdbcTemplate.queryForObject(COUNT_AVAILABLE_SQL, Long.class, numberMin, numberMax, numberWidth);
        return available == null ? 0 : available;
    }

    @Override
    public int compactAllocationIndexes(long transactionId) {
        return jdbcTemplate.update(COMPACT_INDEXES_SQL, transactionId);
    }

    private static List<String> insertCandidates(
            Connection connection, long transactionId, String email, List<LuckyNumberAllocationCandidate> candidates)
            throws SQLException {
        Object[] numbers =
                candidates.stream().map(LuckyNumberAllocationCandidate::number).toArray();
        Object[] allocationIndexes = candidates.stream()
                .map(LuckyNumberAllocationCandidate::allocationIndex)
                .toArray();
        Array numberArray = connection.createArrayOf("text", numbers);
        Array allocationIndexArray = connection.createArrayOf("integer", allocationIndexes);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CANDIDATES_SQL)) {
            statement.setArray(1, numberArray);
            statement.setArray(2, allocationIndexArray);
            setNullableString(statement, 3, email);
            statement.setLong(4, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> inserted = new ArrayList<>();
                while (resultSet.next()) {
                    inserted.add(resultSet.getString("number"));
                }
                return inserted;
            }
        } finally {
            numberArray.free();
            allocationIndexArray.free();
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
