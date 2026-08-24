ALTER TABLE purchase_intent
    DROP CONSTRAINT IF EXISTS fk_purchase_intent_transaction;

ALTER TABLE purchase_intent
    ADD COLUMN participant_name VARCHAR(255),
    ADD COLUMN participant_phone VARCHAR(20),
    ADD COLUMN participant_email VARCHAR(320),
    ADD COLUMN gift_message VARCHAR(280),
    ADD COLUMN quantity INTEGER,
    ADD COLUMN unit_price NUMERIC(19, 2),
    ADD COLUMN total_amount NUMERIC(19, 2),
    ADD COLUMN mp_preference_id VARCHAR(255),
    ADD COLUMN mp_checkout_url VARCHAR(2048),
    ADD COLUMN mp_collector_id VARCHAR(255);
