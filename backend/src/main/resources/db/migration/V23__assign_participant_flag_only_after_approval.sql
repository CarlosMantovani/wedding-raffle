ALTER TABLE transaction
    ALTER COLUMN participant_flag_code DROP NOT NULL,
    ALTER COLUMN participant_flag_name DROP NOT NULL,
    ALTER COLUMN participant_flag_emoji DROP NOT NULL;

ALTER TABLE transaction
    ADD CONSTRAINT ck_transaction_participant_flag_complete
    CHECK (
        (participant_flag_code IS NULL
            AND participant_flag_name IS NULL
            AND participant_flag_emoji IS NULL)
        OR
        (participant_flag_code IS NOT NULL
            AND participant_flag_name IS NOT NULL
            AND participant_flag_emoji IS NOT NULL)
    );

UPDATE transaction
SET
    participant_flag_code = NULL,
    participant_flag_name = NULL,
    participant_flag_emoji = NULL
WHERE status IN ('PENDING', 'REJECTED', 'CANCELLED');
