package com.weddingraffle.rifa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.weddingraffle.rifa.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/auth/login", "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/transactions/quote")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/transactions")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/transactions/recovery")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/transactions/{externalReference}/status")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/transactions/{externalReference}/lucky-numbers.pdf")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/home-summary")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/public/flag-ranking")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/webhook")
                        .permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/raffle/draw")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/transactions/cash")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.GET, "/transactions/{externalReference}/participant-lucky-numbers.pdf")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/transactions/{externalReference}")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/transactions/{externalReference}/capacity-review")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/admin/raffle-config")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/admin/raffle-config/unit-price")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/admin/raffle-config/scheduled-at")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/admin/raffle-config/combos/{comboId}")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/raffle/result",
                                "/raffle/eligible-numbers",
                                "/transactions",
                                "/transactions/summary",
                                "/transactions/messages")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .authenticationEntryPoint((request, response, exception) -> writeErrorResponse(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "Authentication is required.",
                                        request.getRequestURI(),
                                        objectMapper)))
                .exceptionHandling(exception ->
                        exception.accessDeniedHandler((request, response, accessDeniedException) -> writeErrorResponse(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN",
                                "Access is denied.",
                                request.getRequestURI(),
                                objectMapper)))
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(AppProperties appProperties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey(appProperties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(AppProperties appProperties) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey(appProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(
                List.of(appProperties.frontendOrigin(), "https://*.ngrok-free.dev", "https://*.ngrok-free.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "ngrok-skip-browser-warning"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static SecretKey jwtSecretKey(AppProperties appProperties) {
        return new SecretKeySpec(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    private static void writeErrorResponse(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            String path,
            ObjectMapper objectMapper)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.withoutFieldErrors(status, code, message, path));
    }
}
