package de.winniepat.smpwrapped.model;

import java.util.Objects;


public final class Achievement {
    private final String id;
    private final String name;
    private final String description;
    private final Tier tier;
    private final Category category;

    public Achievement(String id, String name, String description, Tier tier, Category category) {
        this.id = id;
        this.name = name.replace('|', '/');
        this.description = description.replace('|', '/');
        this.tier = tier;
        this.category = category;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public Tier tier() { return tier; }
    public Category category() { return category; }

    public enum Tier { BRONZE, SILVER, GOLD, DIAMOND, LEGENDARY }

    public enum Category {
        ACTIVITY, MINING, BUILDING, COMBAT, SURVIVAL,
        EXPLORATION, CRAFTING, FISHING, COLLECTION, SOCIAL, SERVER, FUN
    }

    public String encode() {
        return String.join("|", id, name, description, tier.name(), category.name());
    }

    public static Achievement decode(String raw) {
        if (raw == null) {
            return new Achievement("unknown", "Unknown", "", Tier.BRONZE, Category.FUN);
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 5) {

            return new Achievement(slug(raw), raw, "", Tier.BRONZE, Category.FUN);
        }
        return new Achievement(parts[0], parts[1], parts[2], parseTier(parts[3]), parseCategory(parts[4]));
    }

    private static Tier parseTier(String value) {
        try {
            return Tier.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Tier.BRONZE;
        }
    }

    private static Category parseCategory(String value) {
        try {
            return Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            return Category.FUN;
        }
    }

    private static String slug(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("(^_+|_+$)", "");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Achievement)) return false;
        Achievement that = (Achievement) other;
        return Objects.equals(id, that.id)
            && Objects.equals(name, that.name)
            && Objects.equals(description, that.description)
            && tier == that.tier
            && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, tier, category);
    }

    @Override
    public String toString() {
        return "Achievement[id=" + id + ", name=" + name + ", tier=" + tier
            + ", category=" + category + "]";
    }
}