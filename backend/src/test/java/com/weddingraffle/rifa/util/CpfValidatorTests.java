package com.weddingraffle.rifa.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfValidatorTests {

    @Test
    void normalizesFormattedCpf() {
        assertThat(CpfValidator.normalize("529.982.247-25")).isEqualTo("52998224725");
    }

    @Test
    void validatesFormatAndCheckDigits() {
        assertThat(CpfValidator.isValid("529.982.247-25")).isTrue();
        assertThat(CpfValidator.isValid("52998224725")).isTrue();
        assertThat(CpfValidator.isValid("529.982.247-24")).isFalse();
        assertThat(CpfValidator.isValid("111.111.111-11")).isFalse();
    }
}
