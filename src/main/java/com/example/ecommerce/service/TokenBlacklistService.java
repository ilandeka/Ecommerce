package com.example.ecommerce.service;

import com.example.ecommerce.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
public class TokenBlacklistService {
    // Using Redis would be better for production
    private final Set<String> blacklistedTokens = Collections.synchronizedSet(new HashSet<>());
    private final JwtTokenProvider tokenProvider;

    public TokenBlacklistService(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public void blacklistToken(String token) {
        blacklistedTokens.add(token);
    }

    public boolean isBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }
}
