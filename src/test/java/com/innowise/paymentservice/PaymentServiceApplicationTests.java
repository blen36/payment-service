package com.innowise.paymentservice;

import com.innowise.paymentservice.config.PaymentSecurityConfig;
import com.innowise.paymentservice.controller.PaymentController;
import com.innowise.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(PaymentSecurityConfig.class)
class PaymentServiceApplicationTests {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentService payments;
    @MockitoBean JwtDecoder paymentDecoder;
    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/payments")).andExpect(status().isUnauthorized());
        verifyNoInteractions(payments);
    }
    @Test void rejectsMissingOrderId() throws Exception {
        mvc.perform(post("/api/v1/payments").with(jwt().jwt(j -> j.subject("7").claim("role","USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(payments);
    }
    @Test void rejectsOversizedPage() throws Exception {
        mvc.perform(get("/api/v1/payments?size=101").with(jwt().jwt(j -> j.subject("7").claim("role","USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isBadRequest());
        verifyNoInteractions(payments);
    }
    @Test void protectsAdminEndpoint() throws Exception {
        mvc.perform(get("/api/v1/payments/total/all?from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z")
                .with(jwt().jwt(j -> j.subject("7").claim("role","USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }
}
