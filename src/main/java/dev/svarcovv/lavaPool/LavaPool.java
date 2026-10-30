package dev.svarcovv.lavaPool;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class LavaPool extends JavaPlugin implements Listener {
    private PoolEngine engine;
    private Messages messages;
    private List<Pool> pools = List.of();
    private BukkitTask task;
    private PoolCommands commands;
    private Method placeholderMethod;
    private boolean placeholderWarning;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().isPluginEnabled("W1nPool")) {
            getLogger().severe("W1nPool уже запущен. LavaPool отключён, чтобы не выдавать двойные награды.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            prepareFiles();
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                placeholderMethod = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                        .getMethod("setPlaceholders", Player.class, String.class);
            }
            reloadData();
            commands = new PoolCommands(this);
            PluginCommand command = Objects.requireNonNull(getCommand("lavapool"));
            command.setExecutor(commands);
            command.setTabCompleter(commands);
            getServer().getPluginManager().registerEvents(this, this);
            getServer().getPluginManager().registerEvents(commands, this);
            task = getServer().getScheduler().runTaskTimer(this, () -> engine.tick(), 1, 1);
        } catch (Exception exception) {
            getLogger().severe("Не удалось запустить LavaPool: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        engine.add(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        engine.remove(event.getPlayer().getUniqueId());
        commands.clear(event.getPlayer());
    }

    void reloadData() throws Exception {
        YamlConfiguration config = read(new File(getDataFolder(), "config.yml"));
        Settings settings = Settings.read(config);
        YamlConfiguration messageConfig = read(new File(getDataFolder(), "messages.yml"));
        try (var stream = getResource("messages.yml")) {
            if (stream != null) {
                messageConfig.setDefaults(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)));
            }
        }
        Messages newMessages = new Messages(messageConfig);
        List<Pool> loaded = new ArrayList<>();
        File[] files = new File(getDataFolder(), "pools").listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (files == null) {
            throw new IOException("Не удалось прочитать папку pools");
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            try {
                loaded.add(Pool.read(file.getName().substring(0, file.getName().length() - 4), read(file)));
            } catch (Exception exception) {
                throw new IOException(file.getName() + ": " + exception.getMessage(), exception);
            }
        }
        PoolEngine next = new PoolEngine(this, settings, newMessages, loaded, this::placeholders);
        if (engine != null) {
            engine.close();
        }
        pools = List.copyOf(loaded);
        messages = newMessages;
        engine = next;
        getServer().getOnlinePlayers().forEach(engine::add);
    }

    Messages messages() {
        return messages;
    }

    List<Pool> pools() {
        return pools;
    }

    File poolFile(String name) {
        if (!Pool.validName(name)) {
            throw new IllegalArgumentException("Недопустимое имя зоны");
        }
        return new File(getDataFolder(), "pools/" + name + ".yml");
    }

    private String placeholders(Player player, String text) {
        if (placeholderMethod == null || !text.contains("%")) {
            return text;
        }
        try {
            return (String) placeholderMethod.invoke(null, player, text);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!placeholderWarning) {
                placeholderWarning = true;
                getLogger().warning("PlaceholderAPI не обработал текст награды.");
            }
            return text;
        }
    }

    private void prepareFiles() throws IOException {
        Files.createDirectories(getDataFolder().toPath());
        File old = new File(getDataFolder().getParentFile(), "W1nPool");
        boolean fresh = !new File(getDataFolder(), "config.yml").exists();
        if (fresh && old.isDirectory()) {
            for (String name : List.of("config.yml", "messages.yml")) {
                File source = new File(old, name);
                File target = new File(getDataFolder(), name);
                if (source.isFile() && !target.exists()) {
                    Files.copy(source.toPath(), target.toPath());
                }
            }
            File[] oldPools = new File(old, "pools").listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
            Files.createDirectories(new File(getDataFolder(), "pools").toPath());
            if (oldPools != null) {
                for (File source : oldPools) {
                    File target = new File(getDataFolder(), "pools/" + source.getName());
                    if (!target.exists()) {
                        Files.copy(source.toPath(), target.toPath());
                    }
                }
            }
        }
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        File folder = new File(getDataFolder(), "pools");
        if (!folder.exists()) {
            Files.createDirectories(folder.toPath());
            for (String name : List.of("afk", "free", "premium")) {
                saveResource("pools/" + name + ".yml", false);
            }
        }
    }

    private static YamlConfiguration read(File file) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        return config;
    }
}
