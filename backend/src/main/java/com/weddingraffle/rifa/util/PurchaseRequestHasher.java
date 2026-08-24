package com.weddingraffle.rifa.util;

import com.weddingraffle.rifa.entity.PurchaseIntentAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PurchaseRequestHasher {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    public String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key header must not be blank.");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key header must not exceed 64 characters.");
        }
        return normalized;
    }

    public String online(String name, String phone, String giftMessage, int quantity) {
        return hash(
                PurchaseIntentAction.MERCADO_PAGO_CHECKOUT,
                List.of(name, phone, giftMessage == null ? "" : giftMessage, Integer.toString(quantity)));
    }

    public String online(String name, String phone, int quantity) {
        return online(name, phone, null, quantity);
    }

    public String cash(String name, String phone, String email, String giftMessage, int quantity) {
        return hash(
                PurchaseIntentAction.CASH_REGISTRATION,
                List.of(
                        name,
                        phone,
                        email == null ? "" : email,
                        giftMessage == null ? "" : giftMessage,
                        Integer.toString(quantity)));
    }

    public String cash(String name, String phone, String email, int quantity) {
        return cash(name, phone, email, null, quantity);
    }

    private static String hash(PurchaseIntentAction action, List<String> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(action.name().getBytes(StandardCharsets.UTF_8));
            for (String field : fields) {
                digest.update((byte) 0);
                digest.update(field.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
