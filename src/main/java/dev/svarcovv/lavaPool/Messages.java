package dev.svarcovv.lavaPool;

import dev.svarcovv.lavaAPI.api.LavaService;
import dev.svarcovv.lavaAPI.hud.HudService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

final class Messages {
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final YamlConfiguration config;
    private final Plugin owner;
    private final HudService hud;
    private final Component enter;
    private final Component leave;

    Messages(Plugin owner, LavaService lava, YamlConfiguration config) {
        this.owner = owner;
        this.hud = lava.hud();
        this.config = config;
        enter = line(List.of(config.getString("title1", ""), config.getString("title2", "")));
        leave = line(List.of(config.getString("title3", ""), config.getString("title4", "")));
    }

    Component text(String text) {
        return COLORS.deserialize(text);
    }

    String raw(String key) {
        return config.getString(key, key);
    }

    void send(CommandSender sender, String key, String... replacements) {
        String value = raw(key);
        for (int i = 0; i < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(text(value));
    }

    List<String> rewards(Pool pool) {
        String key = pool.premium() ? "rewardMessagePremium" : "rewardMessageFree";
        return config.isList(key) ? config.getStringList(key) : List.of(config.getString(key, ""));
    }

    void transition(Player player, boolean entering) {
        if (!entering) {
            clearStatus(player);
        }
        notice(player, "transition", entering ? enter : leave);
    }

    void status(Player player, String timer) {
        hud.publishStatus(owner, player, "pool:timer", text(raw("actionBar").replace("%timer%", timer)), 30, 2500L);
    }

    void clearStatus(Player player) {
        hud.withdrawStatus(owner, player, "pool:timer");
    }

    void notice(Player player, String key) {
        notice(player, key, text(raw(key)));
    }

    void notice(Player player, String key, Component text) {
        if (!text.equals(Component.empty())) {
            hud.publish(owner, player, "pool:" + key, text, 40, 2400L);
        }
    }

    Component line(List<String> lines) {
        Component result = Component.empty();
        for (String value : lines) {
            if (value.isBlank()) {
                continue;
            }
            if (!result.equals(Component.empty())) {
                result = result.append(Component.space());
            }
            result = result.append(text(value.strip()));
        }
        return result;
    }

    static String time(int seconds) {
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        String result = minutes == 0 ? "" : minutes + " " + word(minutes, "минута", "минуты", "минут");
        if (remainder > 0 || minutes == 0) {
            result += (result.isEmpty() ? "" : " ") + remainder + " " + word(remainder, "секунда", "секунды", "секунд");
        }
        return result;
    }

    private static String word(int number, String one, String few, String many) {
        int last = number % 10;
        int pair = number % 100;
        return pair >= 11 && pair <= 14 ? many : last == 1 ? one : last >= 2 && last <= 4 ? few : many;
    }
}
