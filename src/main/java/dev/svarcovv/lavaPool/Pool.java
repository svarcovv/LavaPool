package dev.svarcovv.lavaPool;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

record Pool(String name, boolean premium, String world, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ, int seconds, long reward, int weekendMultiplier,
            List<String> commands) {
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1,48}");

    static boolean validName(String name) {
        return NAME.matcher(name).matches();
    }

    static int duration(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9]+[smh]?")) {
            throw new IllegalArgumentException("Неверный интервал: " + text);
        }
        char suffix = value.charAt(value.length() - 1);
        int factor = suffix == 'h' ? 3600 : suffix == 'm' ? 60 : 1;
        long amount = Long.parseLong(Character.isDigit(suffix) ? value : value.substring(0, value.length() - 1));
        if (amount < 1 || amount > Integer.MAX_VALUE / factor) {
            throw new IllegalArgumentException("Некорректный интервал выдачи наград");
        }
        return (int) amount * factor;
    }

    static Pool read(String name, ConfigurationSection config) {
        if (!validName(name)) {
            throw new IllegalArgumentException("Недопустимое имя зоны");
        }
        String type = config.getString("type", "free").toLowerCase(Locale.ROOT);
        if (!type.equals("free") && !type.equals("premium")) {
            throw new IllegalArgumentException("Тип зоны должен быть free или premium");
        }
        String world = config.getString("world");
        if (world == null || world.isBlank()) {
            throw new IllegalArgumentException("Не указан мир");
        }
        int[] pos = new int[6];
        String[] keys = {"pos1.x", "pos1.y", "pos1.z", "pos2.x", "pos2.y", "pos2.z"};
        for (int i = 0; i < keys.length; i++) {
            double coordinate = config.getDouble(keys[i], Double.NaN);
            if (!Double.isFinite(coordinate) || Math.abs(coordinate) > 30_000_000) {
                throw new IllegalArgumentException("Некорректная координата " + keys[i]);
            }
            pos[i] = (int) Math.floor(coordinate);
        }
        long reward = config.getLong("defaultReward", 150);
        int multiplier = config.getInt("weekendMultiple", 2);
        if (reward < 0 || multiplier < 1 || reward > Long.MAX_VALUE / multiplier) {
            throw new IllegalArgumentException("Некорректная награда или множитель");
        }
        List<String> commands = config.getStringList("commandsReward").stream()
                .map(String::trim).map(s -> s.startsWith("/") ? s.substring(1) : s)
                .map(s -> s.replaceAll("(?i)^((?:minecraft:)?give\\s+\\S+\\s+)amethystshard(?=\\s|$)", "$1minecraft:amethyst_shard")).toList();
        if (commands.size() > 100 || commands.stream().anyMatch(s -> s.isBlank() || s.contains("\n") || s.contains("\r"))) {
            throw new IllegalArgumentException("Некорректный список команд награды");
        }
        return new Pool(name, type.equals("premium"), world,
                Math.min(pos[0], pos[3]), Math.min(pos[1], pos[4]), Math.min(pos[2], pos[5]),
                Math.max(pos[0], pos[3]), Math.max(pos[1], pos[4]), Math.max(pos[2], pos[5]),
                duration(config.getString("time", "5m")), reward, multiplier, commands);
    }

    boolean contains(Location location) {
        return location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }
}
