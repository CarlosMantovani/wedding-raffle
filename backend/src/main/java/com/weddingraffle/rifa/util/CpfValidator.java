package com.weddingraffle.rifa.util;

import java.util.regex.Pattern;

public final class CpfValidator {

    private static final Pattern CPF_PATTERN = Pattern.compile("^(?:\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})$");

    private CpfValidator() {}

    public static String normalize(String cpf) {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
    }

    public static boolean isValid(String cpf) {
        if (cpf == null || !CPF_PATTERN.matcher(cpf).matches()) {
            return false;
        }

        String digits = normalize(cpf);
        if (digits.chars().distinct().count() == 1) {
            return false;
        }

        return calculateDigit(digits, 9) == Character.digit(digits.charAt(9), 10)
                && calculateDigit(digits, 10) == Character.digit(digits.charAt(10), 10);
    }

    private static int calculateDigit(String digits, int length) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += Character.digit(digits.charAt(index), 10) * (length + 1 - index);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
