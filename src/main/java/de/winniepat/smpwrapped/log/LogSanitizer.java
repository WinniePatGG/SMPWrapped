package de.winniepat.smpwrapped.log;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LogSanitizer {

    static final String MASK = "***REDACTED***";

    private static final int MAX_VALUE_LENGTH = 512;

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private static final Set<String> SENSITIVE_SEGMENTS = Set.of(
        "token", "tokens", "password", "passwords", "passwd", "passphrase",
        "secret", "secrets", "apikey", "authorization", "auth",
        "credential", "credentials", "signature", "salt", "sessionid", "privatekey");

    private static final Set<String> SENSITIVE_COMPACT = Set.of(
        "apikey", "apitoken", "apisecret", "accesskey", "privatekey", "authtoken", "bearertoken");

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{6,}");

    private LogSanitizer() {
    }

    static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = CAMEL_BOUNDARY.matcher(key).replaceAll("$1_$2").toLowerCase(Locale.ROOT);
        if (SENSITIVE_COMPACT.contains(normalized.replaceAll("[^a-z0-9]+", ""))) {
            return true;
        }
        for (String segment : SEPARATORS.split(normalized)) {
            if (SENSITIVE_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    static String value(String key, Object raw, boolean redact) {
        String text = raw == null ? "null" : String.valueOf(raw);
        text = stripControlCharacters(text);
        if (redact && isSensitiveKey(key) && !text.isEmpty() && !"null".equals(text)) {
            return MASK;
        }
        if (redact) {
            text = BEARER.matcher(text).replaceAll(Matcher.quoteReplacement("$1" + MASK));
        }
        return truncate(text, MAX_VALUE_LENGTH);
    }

    static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[truncated " + (text.length() - maxLength) + " chars]";
    }

    private static String stripControlCharacters(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else if (c < 0x20 || c == 0x7F) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}