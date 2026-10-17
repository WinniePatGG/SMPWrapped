package de.winniepat.smpwrapped.util;

public final class Chat {

    public static final String RESET = "§r";
    public static final String GREEN = "§a";
    public static final String AQUA = "§b";
    public static final String YELLOW = "§e";
    public static final String GOLD = "§6";
    public static final String RED = "§c";
    public static final String WHITE = "§f";
    public static final String GRAY = "§7";
    public static final String LIME = "§a";
    public static final String BOLD = "§l";

    private Chat() {
    }

    public static String of(String color, String message) {
        return color + message + RESET;
    }

    public static String title(String message) {
        return GOLD + BOLD + message + RESET;
    }

    public static String line(String color, String label, String value) {
        return of(color, label + ": " + YELLOW + value);
    }

    public static String prefix(String message) {
        return GREEN + "[SMPWrapped] " + RESET + WHITE + message;
    }

    public static String link(String label, String url) {
        return AQUA + label + GRAY + " " + url;
    }
}