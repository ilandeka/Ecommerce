package com.eshop.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class WebhookConfig {
    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

}