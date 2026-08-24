CREATE FUNCTION assert_lucky_number_batch_integrity_once(checked_transaction_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    inserted_rows INTEGER;
BEGIN
    IF checked_transaction_id IS NULL THEN
        RETURN;
    END IF;

    IF to_regclass('pg_temp.checked_lucky_number_batch_integrity') IS NULL THEN
        EXECUTE '
            CREATE TEMP TABLE checked_lucky_number_batch_integrity (
                transaction_id BIGINT PRIMARY KEY
            ) ON COMMIT DROP
        ';
    END IF;

    EXECUTE '
        INSERT INTO pg_temp.checked_lucky_number_batch_integrity (transaction_id)
        VALUES ($1)
        ON CONFLICT DO NOTHING
    '
    USING checked_transaction_id;
    GET DIAGNOSTICS inserted_rows = ROW_COUNT;

    IF inserted_rows = 1 THEN
        PERFORM assert_lucky_number_batch_integrity(checked_transaction_id);
    END IF;
END;
$$;

-- The V17 row-level constraint trigger re-counted the same batch once per changed number at
-- commit. Keep the deferred invariant, but perform at most one final-state check per transaction.
CREATE OR REPLACE FUNCTION check_lucky_number_batch_integrity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'lucky_number' THEN
        IF TG_OP = 'DELETE' THEN
            PERFORM assert_lucky_number_batch_integrity_once(OLD.transaction_id);
        ELSIF TG_OP = 'INSERT' THEN
            PERFORM assert_lucky_number_batch_integrity_once(NEW.transaction_id);
        ELSE
            PERFORM assert_lucky_number_batch_integrity_once(OLD.transaction_id);
            IF NEW.transaction_id IS DISTINCT FROM OLD.transaction_id THEN
                PERFORM assert_lucky_number_batch_integrity_once(NEW.transaction_id);
            END IF;
        END IF;
    ELSE
        PERFORM assert_lucky_number_batch_integrity_once(COALESCE(NEW.id, OLD.id));
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER trg_lucky_number_batch_integrity ON lucky_number;

-- Transition tables collapse each bulk lucky-number statement into one parent-row update. That
-- update schedules the existing deferred transaction constraint trigger without per-number work.
CREATE FUNCTION schedule_lucky_number_batch_integrity_after_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE transaction raffle_transaction
    SET lucky_numbers_generated_at = raffle_transaction.lucky_numbers_generated_at
    WHERE raffle_transaction.id IN (
        SELECT DISTINCT transaction_id
        FROM inserted_lucky_numbers
    );
    RETURN NULL;
END;
$$;

CREATE FUNCTION schedule_lucky_number_batch_integrity_after_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE transaction raffle_transaction
    SET lucky_numbers_generated_at = raffle_transaction.lucky_numbers_generated_at
    WHERE raffle_transaction.id IN (
        SELECT transaction_id FROM updated_lucky_numbers
        UNION
        SELECT transaction_id FROM previous_lucky_numbers
    );
    RETURN NULL;
END;
$$;

CREATE FUNCTION schedule_lucky_number_batch_integrity_after_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE transaction raffle_transaction
    SET lucky_numbers_generated_at = raffle_transaction.lucky_numbers_generated_at
    WHERE raffle_transaction.id IN (
        SELECT DISTINCT transaction_id
        FROM deleted_lucky_numbers
    );
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_lucky_number_batch_integrity_insert
    AFTER INSERT ON lucky_number
    REFERENCING NEW TABLE AS inserted_lucky_numbers
    FOR EACH STATEMENT
    EXECUTE FUNCTION schedule_lucky_number_batch_integrity_after_insert();

CREATE TRIGGER trg_lucky_number_batch_integrity_update
    AFTER UPDATE ON lucky_number
    REFERENCING OLD TABLE AS previous_lucky_numbers NEW TABLE AS updated_lucky_numbers
    FOR EACH STATEMENT
    EXECUTE FUNCTION schedule_lucky_number_batch_integrity_after_update();

CREATE TRIGGER trg_lucky_number_batch_integrity_delete
    AFTER DELETE ON lucky_number
    REFERENCING OLD TABLE AS deleted_lucky_numbers
    FOR EACH STATEMENT
    EXECUTE FUNCTION schedule_lucky_number_batch_integrity_after_delete();
