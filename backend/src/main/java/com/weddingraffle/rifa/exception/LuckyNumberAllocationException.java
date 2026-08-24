package com.weddingraffle.rifa.exception;

public class LuckyNumberAllocationException extends RuntimeException {

    private final Reason reason;

    public LuckyNumberAllocationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INSUFFICIENT_NUMBERS,
        RETRY_LIMIT_REACHED
    }
}
