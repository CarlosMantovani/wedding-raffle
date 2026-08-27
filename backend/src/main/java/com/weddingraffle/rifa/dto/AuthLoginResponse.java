package com.weddingraffle.rifa.dto;

import java.util.List;

public record AuthLoginResponse(String tokenType, String accessToken, long expiresIn, List<String> roles) {}
