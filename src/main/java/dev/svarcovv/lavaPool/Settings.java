package dev.svarcovv.lavaPool;

import org.bukkit.configuration.ConfigurationSection;

record Settings(boolean pauseTimer, String premiumAction, boolean useMultiplier, int ipLimit,
                boolean broadcast, int commandsPerTick, long budgetNanos, boolean silent,
                int broadcastCooldownSeconds) {
    Settings {
        premiumAction = premiumAction.toLowerCase(java.util.Locale.ROOT);
    }
    static Settings read(ConfigurationSection config) {
        int commands = config.getInt("performance.commandsPerTick", 4);
        int budget = config.getInt("performance.rewardBudgetMillis", 2);
        int ipLimit = config.getInt("IPLimit", 3);
        int cooldown = config.getInt("performance.broadcastCooldownSeconds", 30);
        if (commands < 1 || commands > 100 || budget < 1 || budget > 50 || ipLimit < 0 || cooldown < 0) {
            throw new IllegalArgumentException("Некорректные ограничения в config.yml");
        }
        return new Settings(config.getBoolean("pauseTimer", true),
                config.getString("premiumEnterAction", "denyMessage").toLowerCase(java.util.Locale.ROOT),
                config.getBoolean("useMultiplier", true), ipLimit, config.getBoolean("broadcastRewards", false),
                commands, budget * 1_000_000L, config.getBoolean("performance.silentRewards", true), cooldown);
    }
}
