package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.dto.AuthLoginRequest;
import com.weddingraffle.rifa.dto.AuthLoginResponse;
import com.weddingraffle.rifa.security.JwtService;
import com.weddingraffle.rifa.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public AuthLoginResponse login(AuthLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        return new AuthLoginResponse(
                TOKEN_TYPE,
                jwtService.generateToken(authentication),
                jwtService.expiresInSeconds(),
                jwtService.roles(authentication));
    }
}
