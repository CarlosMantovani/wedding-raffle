package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.dto.AuthLoginRequest;
import com.weddingraffle.rifa.dto.AuthLoginResponse;
import com.weddingraffle.rifa.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTests {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private Authentication authentication;

    @Test
    void authenticatesCredentialsAndReturnsBearerToken() {
        AuthServiceImpl authService = new AuthServiceImpl(authenticationManager, jwtService);
        AuthLoginRequest request = new AuthLoginRequest("admin", "password");
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authentication);
        when(jwtService.generateToken(authentication)).thenReturn("jwt-token");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);
        when(jwtService.roles(authentication)).thenReturn(java.util.List.of("MASTER"));

        AuthLoginResponse response = authService.login(request);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.roles()).containsExactly("MASTER");

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("admin");
        assertThat(captor.getValue().getCredentials()).isEqualTo("password");
    }
}
