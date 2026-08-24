package com.weddingraffle.rifa.integration;

enum MercadoPagoOperation {
    CREATE_PREFERENCE(true),
    GET_PAYMENT(true);

    private final boolean retrySafe;

    MercadoPagoOperation(boolean retrySafe) {
        this.retrySafe = retrySafe;
    }

    boolean isRetrySafe() {
        return retrySafe;
    }
}
