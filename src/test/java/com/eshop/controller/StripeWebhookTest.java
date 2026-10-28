package com.eshop.controller;

import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What Stripe's webhook answers. A call that is not signed with our secret is the caller's mistake (400), not a server
 * error (500): a 500 shows up as a failing service in the Cloud Run logs and monitoring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeWebhookTest {

    // An event type the shop does not handle, so only the signature decides the answer
    private static final String PAYLOAD = "{\"id\":\"evt_test\",\"object\":\"event\",\"type\":\"customer.created\","
            + "\"data\":{\"object\":{\"id\":\"cus_test\",\"object\":\"customer\"}}}";

    @Autowired
    private MockMvc mockMvc;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Test
    void wrongSignatureIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=not-a-valid-signature")
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void signatureFromAnotherSecretIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sign(PAYLOAD, "whsec_somebody_elses_secret"))
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventSignedWithOurSecretIsAccepted() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sign(PAYLOAD, webhookSecret))
                        .content(PAYLOAD))
                .andExpect(status().isOk());
    }

    /** The header Stripe sends: the time and the HMAC-SHA256 of "time.payload" with the signing secret. */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Webhook.Util.getTimeNow();
        return "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
    }
}
