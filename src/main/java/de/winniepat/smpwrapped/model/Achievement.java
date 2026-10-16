package de.winniepat.smpwrapped.model;


public record Achievement(
    String id,
    String name,
    String description,
    Tier tier,
    Category category
) {

    
    public enum Tier { BRONZE, SILVER, GOLD, DIAMOND, LEGENDARY }

    
    public enum Category {
        ACTIVITY, MINING, BUILDING, COMBAT, SURVIVAL,
        EXPLORATION, CRAFTING, FISHING, COLLECTION, SOCIAL, SERVER, FUN
    }

    public Achievement {
        
        name = name.replace('|', '/');
        description = description.replace('|', '/');
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
}
