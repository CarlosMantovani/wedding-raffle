package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record WeddingEventAtUpdateRequest(@NotNull OffsetDateTime weddingEventAt) {}
