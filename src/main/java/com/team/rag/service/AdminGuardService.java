package com.team.rag.service;

import com.team.rag.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminGuardService {

    private final RagProperties ragProperties;

    public AdminGuardService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public void requireAdmin(String providedToken) {
        String expectedToken = ragProperties.getAdmin().getToken();
        if (!StringUtils.hasText(expectedToken)) {
            return;
        }
        if (!StringUtils.hasText(providedToken) || !sameToken(expectedToken, providedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员令牌无效");
        }
    }

    private boolean sameToken(String expectedToken, String providedToken) {
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
