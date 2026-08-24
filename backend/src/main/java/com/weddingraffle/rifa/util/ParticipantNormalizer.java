package com.weddingraffle.rifa.util;

import org.springframework.util.StringUtils;

public final class ParticipantNormalizer {

    private static final int MAX_GIFT_MESSAGE_LENGTH = 280;

    private ParticipantNormalizer() {}

    public static String normalizeName(String name) {
        return name.trim();
    }

    public static String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    public static String normalizeGiftMessage(String giftMessage) {
        if (!StringUtils.hasText(giftMessage)) {
            return null;
        }
        String normalized = giftMessage.trim();
        if (normalized.length() > MAX_GIFT_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Gift message must not exceed 280 characters.");
        }
        return normalized;
    }

    public static String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() != 10 && digits.length() != 11) {
            throw new IllegalArgumentException("Phone must have 10 or 11 digits.");
        }
        return digits;
    }
}
