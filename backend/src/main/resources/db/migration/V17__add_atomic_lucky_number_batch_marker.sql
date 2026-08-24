ALTER TABLE transaction
    ADD COLUMN lucky_numbers_generated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE lucky_number
    ADD COLUMN allocation_index INTEGER;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transaction raffle_transaction
        JOIN lucky_number
          ON lucky_number.transaction_id = raffle_transaction.id
        GROUP BY raffle_transaction.id, raffle_transaction.quantity
        HAVING COUNT(lucky_number.id) <> raffle_transaction.quantity
    ) THEN
        RAISE EXCEPTION
            'Cannot mark existing lucky-number batches: persisted count differs from transaction quantity';
    END IF;
END;
$$;

WITH ordered_lucky_numbers AS (
    SELECT
        id,
        ROW_NUMBER() OVER (PARTITION BY transaction_id ORDER BY id) AS allocation_index
    FROM lucky_number
)
UPDATE lucky_number
SET allocation_index = ordered_lucky_numbers.allocation_index
FROM ordered_lucky_numbers
WHERE lucky_number.id = ordered_lucky_numbers.id;

UPDATE transaction raffle_transaction
SET lucky_numbers_generated_at = COALESCE(raffle_transaction.updated_at, raffle_transaction.created_at)
WHERE EXISTS (
    SELECT 1
    FROM lucky_number
    WHERE lucky_number.transaction_id = raffle_transaction.id
);

ALTER TABLE lucky_number
    ALTER COLUMN allocation_index SET NOT NULL,
    ADD CONSTRAINT chk_lucky_number_allocation_index_positive
        CHECK (allocation_index > 0),
    ADD CONSTRAINT uq_lucky_number_transaction_allocation_index
        UNIQUE (transaction_id, allocation_index);

CREATE FUNCTION assert_lucky_number_batch_integrity(checked_transaction_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    expected_quantity INTEGER;
    generated_at TIMESTAMP WITH TIME ZONE;
    persisted_count BIGINT;
    first_index INTEGER;
    last_index INTEGER;
BEGIN
    SELECT quantity, lucky_numbers_generated_at
    INTO expected_quantity, generated_at
    FROM transaction
    WHERE id = checked_transaction_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT COUNT(*), MIN(allocation_index), MAX(allocation_index)
    INTO persisted_count, first_index, last_index
    FROM lucky_number
    WHERE transaction_id = checked_transaction_id;

    IF generated_at IS NULL THEN
        IF persisted_count <> 0 THEN
            RAISE EXCEPTION
                'Transaction % has lucky numbers without a completed batch marker',
                checked_transaction_id;
        END IF;
        RETURN;
    END IF;

    IF persisted_count <> expected_quantity
        OR first_index <> 1
        OR last_index <> expected_quantity THEN
        RAISE EXCEPTION
            'Transaction % lucky-number batch is incomplete: expected %, found %, indexes %..%',
            checked_transaction_id,
            expected_quantity,
            persisted_count,
            first_index,
            last_index;
    END IF;
END;
$$;

CREATE FUNCTION check_lucky_number_batch_integrity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'lucky_number' THEN
        IF TG_OP = 'DELETE' THEN
            PERFORM assert_lucky_number_batch_integrity(OLD.transaction_id);
        ELSIF TG_OP = 'INSERT' THEN
            PERFORM assert_lucky_number_batch_integrity(NEW.transaction_id);
        ELSE
            PERFORM assert_lucky_number_batch_integrity(OLD.transaction_id);
            IF NEW.transaction_id IS DISTINCT FROM OLD.transaction_id THEN
                PERFORM assert_lucky_number_batch_integrity(NEW.transaction_id);
            END IF;
        END IF;
    ELSE
        PERFORM assert_lucky_number_batch_integrity(COALESCE(NEW.id, OLD.id));
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_transaction_lucky_number_batch_integrity
    AFTER INSERT OR UPDATE ON transaction
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_lucky_number_batch_integrity();

CREATE CONSTRAINT TRIGGER trg_lucky_number_batch_integrity
    AFTER INSERT OR UPDATE OR DELETE ON lucky_number
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_lucky_number_batch_integrity();
