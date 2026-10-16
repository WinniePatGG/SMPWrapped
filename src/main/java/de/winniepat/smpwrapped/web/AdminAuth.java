package de.winniepat.smpwrapped.web;

import de.winniepat.smpwrapped.SMPWrapped;
import java.util.UUID;

/**
 * Handles admin authentication for the web API.
 * Uses token-based auth.
 */
public class AdminAuth {

    private final SMPWrapped plugin;

    public AdminAuth(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    /**
     * Validate an admin token.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        String storedToken = plugin.getConfigManager().get().adminToken();
        return storedToken != null && !storedToken.isBlank() && storedToken.equals(token);
    }

    /**
     * Generate a new admin token.
     */
    public String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "");
    }
}
