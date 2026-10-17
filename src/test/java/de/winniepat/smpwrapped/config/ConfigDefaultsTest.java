package de.winniepat.smpwrapped.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDefaultsTest {

    private static final Pattern ADD_DEFAULT =
        Pattern.compile("addDefault\\(\\s*\"([^\"]+)\"\\s*,\\s*([A-Za-z0-9_.\"\\-]+)\\s*\\)\\s*;");
    private static final Pattern GETTER =
        Pattern.compile("cfg\\.get(?!Boolean\\(\"admin)(Boolean|Int|Long|Double|String)\\(\\s*\"([^\"]+)\"\\s*(?:,\\s*([^)]*?))?\\)\\s*[,;]");

    private static String source() throws Exception {
        Path path = Paths.get("src", "main", "java", "de", "winniepat",
            "smpwrapped", "config", "ConfigManager.java");
        if (!Files.exists(path)) {
            path = Paths.get("..", "src", "main", "java", "de", "winniepat",
                "smpwrapped", "config", "ConfigManager.java");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String normalise(String literal) {
        return literal == null ? null : literal.trim().replaceAll("\\s+", "");
    }

    private static final Map<String, String> INTENTIONAL = new LinkedHashMap<>();

    static {
        INTENTIONAL.put("database.path",
            "read twice on purpose: validation reads it with a blank fallback so a missing or "
                + "empty path raises a config error, the constructor then reads it with the real default");
    }

    private static String normaliseNumber(String literal) {
        if (literal == null) {
            return null;
        }
        String trimmed = literal.trim();
        if (trimmed.endsWith("L") || trimmed.endsWith("l")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @Test
    @DisplayName("Every read fallback matches the registered default")
    void readFallbacksMatchDefaults() throws Exception {
        String source = source();

        Map<String, String> defaults = new LinkedHashMap<>();
        Matcher addMatcher = ADD_DEFAULT.matcher(source);
        while (addMatcher.find()) {
            defaults.put(addMatcher.group(1), normaliseNumber(addMatcher.group(2)));
        }

        Map<String, String> problems = new LinkedHashMap<>();
        Map<String, String> intentional = new LinkedHashMap<>();
        Matcher getMatcher = GETTER.matcher(source);
        while (getMatcher.find()) {
            String key = getMatcher.group(2);
            String fallback = normaliseNumber(getMatcher.group(3));
            if (fallback == null || !defaults.containsKey(key)) {
                continue;
            }
            String registered = defaults.get(key);
            if (registered.equals(fallback)) {
                continue;
            }
            String reason = "addDefault=" + registered + " but get fallback=" + fallback;
            if (INTENTIONAL.containsKey(key)) {
                intentional.put(key, reason);
            } else {
                problems.put(key, reason);
            }
        }

        assertThat(intentional.keySet())
            .as("allow-listed mismatches must still exist, otherwise drop them from the allow list")
            .isEqualTo(INTENTIONAL.keySet());

        assertThat(problems)
            .as("Bukkit only consults addDefault once the value is materialised in the live config, "
                + "so a mismatched getX fallback silently wins at runtime. Keep them in sync.")
            .isEmpty();
    }

    @Test
    @DisplayName("Defaults are materialised before any value is read")
    void defaultsAreMaterialisedBeforeReads() throws Exception {
        String source = source();

        int copyDefaults = source.indexOf("options().copyDefaults(true)");
        int reload = source.indexOf("plugin.reloadConfig()", copyDefaults);
        int firstRead = source.indexOf("cfg.getInt(\"web.port\"");

        assertThat(copyDefaults).isGreaterThanOrEqualTo(0);
        assertThat(reload).as("config must be reloaded after saving so addDefault values are readable")
            .isGreaterThan(copyDefaults);
        assertThat(firstRead).as("config values are read after the defaults are materialised")
            .isGreaterThan(reload);
    }

    @Test
    @DisplayName("Creative block tracking is counted by default")
    void creativeBlocksCountedByDefault() throws Exception {
        String source = source();

        assertThat(source).contains("addDefault(\"tracking.ignore-creative\", false)");
        assertThat(source).contains("cfg.getBoolean(\"tracking.ignore-creative\", false)");
    }
}