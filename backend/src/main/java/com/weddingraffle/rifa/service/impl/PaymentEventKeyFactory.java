package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventKeyFactory {

    public String create(PaymentProviderPayment payment) {
        String canonical = String.join(
                "|",
                value(payment.paymentId()),
                value(payment.externalReference()),
                value(payment.orderExternalReference()),
                value(payment.preferenceId()),
                value(payment.collectorId()),
                value(payment.transactionAmount()),
                value(payment.currencyId()),
                value(payment.status()),
                value(payment.statusDetail()),
                value(payment.dateCreated()),
                value(payment.dateLastUpdated()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String value(Object value) {
        String text =
                value instanceof OffsetDateTime dateTime ? dateTime.toInstant().toString() : String.valueOf(value);
        return text.length() + ":" + text;
    }
}
