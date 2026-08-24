package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.NotNull;

public record CapacityReviewDecisionRequest(@NotNull CapacityReviewDecision decision) {}
