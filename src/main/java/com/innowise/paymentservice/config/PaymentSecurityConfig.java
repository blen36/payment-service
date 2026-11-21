package com.innowise.paymentservice.config;

import com.innowise.paymentservice.security.JwtSupport;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class PaymentSecurityConfig {
    @Bean
    JwtDecoder paymentDecoder(@Value("${payment.jwt.jwk-set-uri}") String uri,
                            @Value("${payment.jwt.issuer}") String issuer,
                            @Value("${payment.jwt.audience}") String audience) {
        return JwtSupport.decoder(NimbusJwtDecoder.withJwkSetUri(uri).build(), issuer, audience, "access");
    }

    @Bean
    SecurityFilterChain paymentApi(HttpSecurity http, ObjectMapper json) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        org.springframework.security.web.AuthenticationEntryPoint unauthorized = (req, res, ex) -> {
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeError(res, json, HttpStatus.UNAUTHORIZED, "A valid access token is required");
        };
        org.springframework.security.web.access.AccessDeniedHandler forbidden =
                (req, res, ex) -> writeError(res, json, HttpStatus.FORBIDDEN, "Access denied");
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/payments/total/all").hasRole("ADMIN")
                        .anyRequest().hasAnyRole("USER", "ADMIN"))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper json, HttpStatus status,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, message));
    }
}
