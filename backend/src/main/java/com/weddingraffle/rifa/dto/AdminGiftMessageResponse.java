package com.weddingraffle.rifa.dto;

import java.time.OffsetDateTime;

public record AdminGiftMessageResponse(
        String externalReference, OffsetDateTime createdAt, String name, String giftMessage) {}
