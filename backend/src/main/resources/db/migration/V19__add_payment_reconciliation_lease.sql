ALTER TABLE transaction
    ADD COLUMN payment_reconciliation_attempted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN payment_reconciliation_lease_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN payment_reconciliation_lease_token UUID,
    ADD CONSTRAINT chk_transaction_payment_reconciliation_lease
        CHECK (
            (payment_reconciliation_lease_until IS NULL AND payment_reconciliation_lease_token IS NULL)
            OR
            (
                payment_reconciliation_lease_until IS NOT NULL
                AND payment_reconciliation_lease_token IS NOT NULL
                AND payment_reconciliation_attempted_at IS NOT NULL
                AND payment_reconciliation_lease_until > payment_reconciliation_attempted_at
            )
        );
