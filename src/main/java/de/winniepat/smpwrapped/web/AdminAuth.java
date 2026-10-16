package de.winniepat.smpwrapped.web;

import de.winniepat.smpwrapped.SMPWrapped;
import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;


public class AdminAuth {

    private static final SMPLogger LOG = SMPLogger.get("web-auth");

    private final SMPWrapped plugin;

    public AdminAuth(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String storedToken = plugin.getConfigManager().get().adminToken();
            if (storedToken == null || storedToken.isBlank()) {
                LOG.error("Admin API is enabled but no admin token is configured", null, LogContext.of(
                    "hint", "Set admin.token in config.yml"));
                return false;
            }
            return constantTimeEquals(storedToken, token);
        } catch (Throwable t) {
            LOG.error("Could not validate the admin token", t);
            return false;
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8));
    }

    
    public String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "");
    }
}