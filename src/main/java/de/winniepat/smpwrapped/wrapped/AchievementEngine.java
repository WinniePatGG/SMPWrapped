package de.winniepat.smpwrapped.wrapped;

import de.winniepat.smpwrapped.log.LogContext;
import de.winniepat.smpwrapped.log.SMPLogger;
import de.winniepat.smpwrapped.model.Achievement;
import de.winniepat.smpwrapped.model.Achievement.Category;
import de.winniepat.smpwrapped.model.Achievement.Tier;
import de.winniepat.smpwrapped.model.PlayerStats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;


public final class AchievementEngine {

    private static final SMPLogger LOG = SMPLogger.get("achievements");

    private AchievementEngine() {}

    public static List<Achievement> evaluate(PlayerStats stats, Map<String, Double> percentiles) {
        List<Achievement> out = new ArrayList<>();

        if (stats == null) {
            LOG.error("Cannot evaluate achievements without stats");
            return out;
        }

        long playtime = stats.getPlaytimeSeconds();
        double hours = playtime / 3600.0;
        long deaths = stats.getDeaths();
        long uniqueDays = Math.max(stats.getUniqueDaysPlayed(), stats.getDailyPlaytime().size());

        
        Map<Integer, Long> hourly = stats.getHourlyPlaytime();
        long hourlyTotal = sum(hourly.values());
        long night = sumHours(hourly, 22, 23, 0, 1, 2, 3, 4);
        long early = sumHours(hourly, 5, 6, 7, 8);
        long midday = sumHours(hourly, 9, 10, 11, 12, 13, 14, 15, 16);
        long evening = sumHours(hourly, 17, 18, 19, 20, 21);
        double nightShare = share(night, hourlyTotal);
        double earlyShare = share(early, hourlyTotal);
        double middayShare = share(midday, hourlyTotal);
        double eveningShare = share(evening, hourlyTotal);

        
        Map<String, Long> daily = stats.getDailyPlaytime();
        long dailyTotal = sum(daily.values());
        long weekendSeconds = 0;
        long maxDaySeconds = 0;
        Set<LocalDate> days = new HashSet<>();
        for (Map.Entry<String, Long> e : daily.entrySet()) {
            maxDaySeconds = Math.max(maxDaySeconds, e.getValue());
            LocalDate date = parseDate(e.getKey());
            if (date != null) {
                days.add(date);
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    weekendSeconds += e.getValue();
                }
            }
        }
        int streak = longestStreak(days);
        double weekendShare = share(weekendSeconds, dailyTotal);

        
        Map<String, Long> dims = stats.getDimensionTime();
        long dimTotal = sum(dims.values());
        long nether = dims.getOrDefault("NETHER", 0L);
        long theEnd = dims.getOrDefault("THE_END", 0L);
        long overworld = dims.getOrDefault("NORMAL", 0L);

        
        Map<String, Long> mined = stats.getBlocksMinedByType();
        Map<String, Long> placed = stats.getBlocksPlacedByType();
        Map<String, Long> mobs = stats.getMobsKilledByType();

        long minedCount = stats.getBlocksMined();
        long placedCount = stats.getBlocksPlaced();
        long mobCount = stats.getMobsKilled();
        long distance = stats.getDistanceTraveled();

        
        add(out, playtime > 0 && hours < 1, "newcomer", "Newcomer",
            "Your journey has just begun.", Tier.BRONZE, Category.ACTIVITY);
        add(out, hours >= 1, "first_steps", "First Steps",
            "Played for your first full hour.", Tier.BRONZE, Category.ACTIVITY);
        add(out, hours >= 10, "getting_serious", "Getting Serious",
            "Ten hours in - this is becoming a habit.", Tier.SILVER, Category.ACTIVITY);
        add(out, hours >= 25, "committed", "Committed",
            "Twenty-five hours of dedication.", Tier.GOLD, Category.ACTIVITY);
        add(out, hours >= 50, "veteran", "Veteran",
            "Fifty hours on the server.", Tier.GOLD, Category.ACTIVITY);
        add(out, hours >= 100, "legend", "Legend",
            "One hundred hours. A true SMP legend.", Tier.DIAMOND, Category.ACTIVITY);
        add(out, hours >= 250, "timeless", "Timeless",
            "Two hundred and fifty hours - beyond dedication.", Tier.LEGENDARY, Category.ACTIVITY);

        add(out, hours >= 1 && nightShare >= 0.5, "night_owl", "Night Owl",
            "Mostly online between 10pm and 5am (server time).", Tier.GOLD, Category.ACTIVITY);
        add(out, hours >= 1 && earlyShare >= 0.4, "early_bird", "Early Bird",
            "Mostly online between 5am and 9am (server time).", Tier.GOLD, Category.ACTIVITY);
        add(out, hours >= 1 && middayShare >= 0.5, "midday_warrior", "Midday Warrior",
            "Mostly online during the day (9am-5pm).", Tier.SILVER, Category.ACTIVITY);
        add(out, hours >= 1 && eveningShare >= 0.5, "evening_gamer", "Evening Gamer",
            "Mostly online in the evening (5pm-10pm).", Tier.SILVER, Category.ACTIVITY);
        add(out, hours >= 1 && nightShare >= 0.25 && earlyShare >= 0.25, "graveyard_shift", "Graveyard Shift",
            "No matter the hour, you're always around.", Tier.GOLD, Category.ACTIVITY);

        add(out, streak >= 7, "unbreakable", "Unbreakable",
            "Logged in 7 days in a row.", Tier.GOLD, Category.ACTIVITY);
        add(out, streak >= 14, "unstoppable", "Unstoppable",
            "Logged in 14 days in a row.", Tier.DIAMOND, Category.ACTIVITY);
        add(out, uniqueDays >= 7, "daily_grinder", "Daily Grinder",
            "Played on 7 different days.", Tier.SILVER, Category.ACTIVITY);
        add(out, uniqueDays >= 14, "season_regular", "Season Regular",
            "Played on 14 different days.", Tier.GOLD, Category.ACTIVITY);
        add(out, uniqueDays >= 28, "everyday_hero", "Everyday Hero",
            "Played on 28 different days.", Tier.DIAMOND, Category.ACTIVITY);
        add(out, weekendShare >= 0.5 && uniqueDays >= 2, "weekend_warrior", "Weekend Warrior",
            "More than half your playtime was on weekends.", Tier.SILVER, Category.ACTIVITY);
        add(out, maxDaySeconds >= 6 * 3600, "marathoner", "Marathoner",
            "Played for 6+ hours in a single day.", Tier.GOLD, Category.ACTIVITY);
        add(out, maxDaySeconds >= 12 * 3600, "ultra_marathoner", "Ultra Marathoner",
            "Played for 12+ hours in a single day.", Tier.DIAMOND, Category.ACTIVITY);

        add(out, stats.getLoginSessions() >= 50, "frequent_flyer", "Frequent Flyer",
            "Logged in 50 times.", Tier.SILVER, Category.SOCIAL);
        add(out, stats.getLoginSessions() >= 200, "server_resident", "Server Resident",
            "Logged in 200 times.", Tier.GOLD, Category.SOCIAL);

        
        add(out, minedCount >= 100, "stone_age", "Stone Age",
            "Mined your first 100 blocks.", Tier.BRONZE, Category.MINING);
        add(out, minedCount >= 1_000, "miner", "Miner",
            "Mined 1,000 blocks.", Tier.SILVER, Category.MINING);
        add(out, minedCount >= 10_000, "deep_diver", "Deep Diver",
            "Mined 10,000 blocks.", Tier.GOLD, Category.MINING);
        add(out, minedCount >= 50_000, "mole", "Mole",
            "Mined 50,000 blocks.", Tier.DIAMOND, Category.MINING);
        add(out, minedCount >= 250_000, "earth_eater", "Earth Eater",
            "Mined 250,000 blocks.", Tier.LEGENDARY, Category.MINING);
        add(out, sumTypes(mined, "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE") >= 50, "diamond_hands", "Diamond Hands",
            "Mined 50 diamond ore.", Tier.GOLD, Category.MINING);
        add(out, sumTypes(mined, "OBSIDIAN") >= 100, "obsidian_obsessed", "Obsidian Obsessed",
            "Mined 100 obsidian.", Tier.GOLD, Category.MINING);
        add(out, sumTypes(mined, "ANCIENT_DEBRIS") >= 10, "ancient_secrets", "Ancient Secrets",
            "Mined 10 ancient debris.", Tier.DIAMOND, Category.MINING);
        add(out, sumTypes(mined, "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE") >= 25, "emerald_tycoon", "Emerald Tycoon",
            "Mined 25 emerald ore.", Tier.GOLD, Category.MINING);
        add(out, sumTypes(mined, "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE") >= 100, "redstone_engineer", "Redstone Engineer",
            "Mined 100 redstone ore.", Tier.SILVER, Category.MINING);
        add(out, sumTypes(mined, "COAL_ORE", "DEEPSLATE_COAL_ORE") >= 1_000, "coal_for_days", "Coal for Days",
            "Mined 1,000 coal ore.", Tier.SILVER, Category.MINING);
        add(out, sumTypes(mined, "DEEPSLATE") >= 1_000, "deepslate_dweller", "Deepslate Dweller",
            "Mined 1,000 deepslate.", Tier.SILVER, Category.MINING);

        
        add(out, placedCount >= 500, "architect", "Architect",
            "Placed 500 blocks.", Tier.BRONZE, Category.BUILDING);
        add(out, placedCount >= 5_000, "master_builder", "Master Builder",
            "Placed 5,000 blocks.", Tier.GOLD, Category.BUILDING);
        add(out, placedCount >= 25_000, "city_planner", "City Planner",
            "Placed 25,000 blocks.", Tier.DIAMOND, Category.BUILDING);
        add(out, sumTypes(placed, "GLASS", "GLASS_PANE") >= 500, "glass_house", "Glass House",
            "Placed 500 glass blocks.", Tier.SILVER, Category.BUILDING);
        add(out, sumTypes(placed, "STONE_BRICKS", "BRICKS", "DEEPSLATE_BRICKS",
            "POLISHED_ANDESITE", "POLISHED_DIORITE", "POLISHED_GRANITE") >= 1_000, "brick_by_brick", "Brick by Brick",
            "Placed 1,000 decorative stone blocks.", Tier.SILVER, Category.BUILDING);

        
        add(out, mobCount >= 100, "slayer", "Slayer",
            "Defeated 100 mobs.", Tier.BRONZE, Category.COMBAT);
        add(out, mobCount >= 1_000, "monster_hunter", "Monster Hunter",
            "Defeated 1,000 mobs.", Tier.GOLD, Category.COMBAT);
        add(out, mobCount >= 10_000, "exterminator", "Exterminator",
            "Defeated 10,000 mobs.", Tier.DIAMOND, Category.COMBAT);
        add(out, mobCount >= 50_000, "pest_control", "Pest Control",
            "Defeated 50,000 mobs.", Tier.LEGENDARY, Category.COMBAT);
        add(out, sumTypes(mobs, "CREEPER") >= 50, "creeper_bane", "Creeper's Bane",
            "Defeated 50 creepers.", Tier.SILVER, Category.COMBAT);
        add(out, sumTypes(mobs, "SKELETON", "STRAY", "WITHER_SKELETON") >= 50, "skeleton_sniper", "Skeleton Sniper",
            "Defeated 50 skeletons.", Tier.SILVER, Category.COMBAT);
        add(out, sumTypes(mobs, "ZOMBIE", "ZOMBIE_VILLAGER", "HUSK", "DROWNED") >= 100, "zombie_slayer", "Zombie Slayer",
            "Defeated 100 undead.", Tier.SILVER, Category.COMBAT);
        add(out, sumTypes(mobs, "SPIDER", "CAVE_SPIDER") >= 50, "spider_squasher", "Spider Squasher",
            "Defeated 50 spiders.", Tier.SILVER, Category.COMBAT);
        add(out, sumTypes(mobs, "ENDERMAN") >= 25, "enderman_hunter", "Enderman Hunter",
            "Defeated 25 endermen.", Tier.GOLD, Category.COMBAT);
        add(out, sumTypes(mobs, "BLAZE") >= 25, "blaze_butcher", "Blaze Butcher",
            "Defeated 25 blazes.", Tier.GOLD, Category.COMBAT);
        add(out, sumTypes(mobs, "GHAST") >= 10, "ghast_whisperer", "Ghast Whisperer",
            "Defeated 10 ghasts.", Tier.SILVER, Category.COMBAT);
        add(out, sumTypes(mobs, "PIGLIN", "PIGLIN_BRUTE") >= 25, "piglin_problem", "Piglin Problem",
            "Defeated 25 piglins.", Tier.GOLD, Category.COMBAT);
        add(out, sumTypes(mobs, "WITHER") >= 1, "wither_slayer", "Wither Slayer",
            "Defeated the Wither.", Tier.DIAMOND, Category.COMBAT);
        add(out, sumTypes(mobs, "ELDER_GUARDIAN") >= 1, "elder_guardian_slayer", "Elder Guardian Slayer",
            "Defeated an Elder Guardian.", Tier.DIAMOND, Category.COMBAT);
        add(out, sumTypes(mobs, "ENDER_DRAGON") >= 1, "dragon_slayer", "Dragon Slayer",
            "Defeated the Ender Dragon.", Tier.LEGENDARY, Category.COMBAT);
        add(out, stats.getDamageDealt() >= 10_000, "bruiser", "Bruiser",
            "Dealt 10,000 damage.", Tier.SILVER, Category.COMBAT);

        
        add(out, playtime >= 3600 && deaths == 0, "flawless", "Flawless",
            "An hour in with zero deaths.", Tier.SILVER, Category.SURVIVAL);
        add(out, playtime >= 5 * 3600 && deaths == 0, "untouchable", "Untouchable",
            "Five hours without dying once.", Tier.GOLD, Category.SURVIVAL);
        add(out, hours >= 10 && deaths <= 2, "survivor", "Survivor",
            "Ten hours with almost no deaths.", Tier.GOLD, Category.SURVIVAL);
        add(out, deaths >= 25, "living_on_the_edge", "Living on the Edge",
            "Died 25 times and kept going.", Tier.BRONZE, Category.FUN);
        add(out, deaths >= 100, "death_wish", "Death Wish",
            "Died 100 times.", Tier.SILVER, Category.FUN);
        add(out, deaths >= 250, "respawn_addict", "Respawn Addict",
            "Died 250 times. Resurrection is a lifestyle.", Tier.GOLD, Category.FUN);
        add(out, stats.getDamageReceived() >= 10_000, "glass_jaw", "Glass Jaw",
            "Took 10,000 damage.", Tier.BRONZE, Category.FUN);

        
        add(out, distance >= 10_000, "wanderer", "Wanderer",
            "Traveled 10,000 blocks.", Tier.BRONZE, Category.EXPLORATION);
        add(out, distance >= 100_000, "globetrotter", "Globetrotter",
            "Traveled 100,000 blocks.", Tier.GOLD, Category.EXPLORATION);
        add(out, distance >= 1_000_000, "odysseus", "Odysseus",
            "Traveled a million blocks.", Tier.DIAMOND, Category.EXPLORATION);
        add(out, dims.size() >= 3, "dimension_hopper", "Dimension Hopper",
            "Spent time in all three dimensions.", Tier.SILVER, Category.EXPLORATION);
        add(out, nether >= 3600 && share(nether, dimTotal) >= 0.3, "nether_dweller", "Nether Dweller",
            "Spent at least an hour in the Nether.", Tier.GOLD, Category.EXPLORATION);
        add(out, theEnd >= 1800, "end_wanderer", "End Wanderer",
            "Spent 30+ minutes in the End.", Tier.DIAMOND, Category.EXPLORATION);
        add(out, overworld >= 5 * 3600 && share(overworld, dimTotal) >= 0.9, "overworld_native", "Overworld Native",
            "You call the Overworld home.", Tier.SILVER, Category.EXPLORATION);

        
        add(out, stats.getItemsCrafted() >= 500, "crafter", "Crafter",
            "Crafted 500 items.", Tier.BRONZE, Category.CRAFTING);
        add(out, stats.getItemsCrafted() >= 5_000, "artisan", "Artisan",
            "Crafted 5,000 items.", Tier.GOLD, Category.CRAFTING);
        add(out, stats.getItemsCrafted() >= 25_000, "mass_production", "Mass Production",
            "Crafted 25,000 items.", Tier.DIAMOND, Category.CRAFTING);
        add(out, stats.getItemsCollected() >= 10_000, "hoarder", "Hoarder",
            "Collected 10,000 items.", Tier.SILVER, Category.COLLECTION);
        add(out, stats.getItemsCollected() >= 50_000, "pack_rat", "Pack Rat",
            "Collected 50,000 items.", Tier.GOLD, Category.COLLECTION);
        add(out, stats.getItemsCollected() >= 250_000, "collector_supreme", "Collector Supreme",
            "Collected 250,000 items.", Tier.DIAMOND, Category.COLLECTION);
        add(out, stats.getFishingCatches() >= 50, "angler", "Angler",
            "Caught 50 fish.", Tier.BRONZE, Category.FISHING);
        add(out, stats.getFishingCatches() >= 500, "master_angler", "Master Angler",
            "Caught 500 fish.", Tier.GOLD, Category.FISHING);
        add(out, stats.getFishingCatches() >= 5_000, "legend_of_the_sea", "Legend of the Sea",
            "Caught 5,000 fish.", Tier.DIAMOND, Category.FISHING);

        
        if (percentiles != null && !percentiles.isEmpty()) {
            double best = percentiles.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            add(out, best >= 90, "elite", "Elite",
                "Top 10% of the server in at least one stat.", Tier.GOLD, Category.SERVER);
            add(out, best >= 99, "top_one_percent", "Top 1%",
                "You are among the very best on the server.", Tier.DIAMOND, Category.SERVER);
            add(out, pct(percentiles, "blocks_mined") >= 90, "master_miner", "Master Miner",
                "Top 10% for blocks mined.", Tier.GOLD, Category.SERVER);
            add(out, pct(percentiles, "distance_traveled") >= 90, "master_explorer", "Master Explorer",
                "Top 10% for distance traveled.", Tier.GOLD, Category.SERVER);
            add(out, pct(percentiles, "mobs_killed") >= 90, "master_hunter", "Master Hunter",
                "Top 10% for mobs killed.", Tier.GOLD, Category.SERVER);
            add(out, pct(percentiles, "playtime_seconds") >= 90, "no_life", "No-Lifer",
                "Top 10% for playtime. Respect.", Tier.GOLD, Category.FUN);
        }

        
        out.sort(Comparator
            .comparingInt((Achievement a) -> a.tier().ordinal()).reversed()
            .thenComparing(Achievement::name));

        LOG.trace("Evaluated achievements", LogContext.of(
            "unlocked", out.size(),
            "hours", Math.round(hours * 10.0) / 10.0,
            "deaths", deaths,
            "uniqueDays", uniqueDays,
            "longestStreak", streak,
            "nightShare", Math.round(nightShare * 100.0) / 100.0));

        return out;
    }

    

    private static void add(List<Achievement> out, boolean condition, String id, String name,
                            String description, Tier tier, Category category) {
        if (condition) {
            out.add(new Achievement(id, name, description, tier, category));
        }
    }

    private static long sumHours(Map<Integer, Long> map, int... hours) {
        long total = 0;
        for (int h : hours) total += map.getOrDefault(h, 0L);
        return total;
    }

    private static long sumTypes(Map<String, Long> map, String... keys) {
        long total = 0;
        for (String k : keys) total += map.getOrDefault(k, 0L);
        return total;
    }

    private static long sum(Collection<Long> values) {
        long total = 0;
        for (long v : values) total += v;
        return total;
    }

    private static double share(long part, long total) {
        return total <= 0 ? 0 : (double) part / total;
    }

    private static double pct(Map<String, Double> map, String key) {
        return map.getOrDefault(key, 0.0);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            LOG.trace("Skipping daily playtime entry with an unparsable date",
                LogContext.of("value", value));
            return null;
        }
    }

    private static int longestStreak(Set<LocalDate> days) {
        if (days.isEmpty()) return 0;
        List<LocalDate> sorted = new ArrayList<>(days);
        Collections.sort(sorted);
        int best = 1;
        int current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1).plusDays(1))) {
                current++;
            } else {
                current = 1;
            }
            best = Math.max(best, current);
        }
        return best;
    }
}
