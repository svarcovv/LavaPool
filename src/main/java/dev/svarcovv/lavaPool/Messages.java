package dev.svarcovv.lavaPool;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

final class Messages {
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final YamlConfiguration config;
    private final Title enter;
    private final Title leave;

    Messages(YamlConfiguration config) {
        this.config = config;
        enter = title("title1", "title2");
        leave = title("title3", "title4");
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
        player.showTitle(entering ? enter : leave);
        if (!entering) {
            player.sendActionBar(Component.empty());
        }
    }

    private Title title(String key, String subtitle) {
        return Title.title(text(config.getString(key, "")), text(config.getString(subtitle, "")),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500)));
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
