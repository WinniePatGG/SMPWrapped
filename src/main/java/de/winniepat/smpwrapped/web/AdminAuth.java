package de.winniepat.smpwrapped.web;

import de.winniepat.smpwrapped.SMPWrapped;
import java.util.UUID;


public class AdminAuth {

    private final SMPWrapped plugin;

    public AdminAuth(SMPWrapped plugin) {
        this.plugin = plugin;
    }

    
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        String storedToken = plugin.getConfigManager().get().adminToken();
        return storedToken != null && !storedToken.isBlank() && storedToken.equals(token);
    }

    
    public String generateToken() {
        return UUID.randomUUID().toString().replace("-", "") +
               UUID.randomUUID().toString().replace("-", "");
    }
}
