package com.eshop.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which endpoints answer without signing in. The deployed demo depends on three of them: the health check (smoke test
 * of the deploy), the product list (the store front) and Stripe's webhook (marks an order as paid).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndpointAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void productListIsPublicAndSeeded() throws Exception {
        mockMvc.perform(get("/api/products/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void stripeWebhookIsReachableWithoutSigningIn() throws Exception {
        // Stripe has no JWT. A request with a bad signature must be rejected by the webhook handler itself, not by the
        // security filter before it (401/403), otherwise no order can ever be marked as paid.
        int status = mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=not-a-valid-signature")
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void browserMayCallTheApiFromTheFrontendWithTheHeadersItSends() throws Exception {
        // The frontend adds Cache-Control, Pragma and Expires to the auth calls (src/services/api.ts). A header the API
        // does not list makes the browser's preflight fail, and then nobody can sign in.
        // Spring answers 200 and echoes only the requested headers it allows; the browser then refuses the call. So
        // check that every requested header comes back, not just the status.
        String requested = "authorization,cache-control,content-type,expires,pragma";
        String allowed = mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", requested))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andReturn().getResponse().getHeader("Access-Control-Allow-Headers");

        assertThat(allowed).isNotNull();
        assertThat(Arrays.stream(allowed.split(",")).map(String::trim).map(String::toLowerCase))
                .containsAll(Arrays.asList(requested.split(",")));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cartNeedsSigningIn() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().is4xxClientError());
    }
}
