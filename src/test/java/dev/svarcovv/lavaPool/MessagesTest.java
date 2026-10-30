package dev.svarcovv.lavaPool;

import dev.svarcovv.lavaAPI.api.LavaService;
import dev.svarcovv.lavaAPI.hud.HudService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessagesTest {
    private Plugin owner;
    private Player player;
    private HudService hud;
    private Messages messages;

    @BeforeEach
    void setup() {
        owner = mock(Plugin.class);
        player = mock(Player.class);
        hud = mock(HudService.class);
        LavaService lava = mock(LavaService.class);
        when(lava.hud()).thenReturn(hud);
        YamlConfiguration config = new YamlConfiguration();
        config.set("actionBar", "&6Осталось: %timer%");
        config.set("title1", "&6Вы вошли");
        config.set("title2", "");
        config.set("title3", "&7Вы вышли");
        config.set("title4", "");
        config.set("reloadSuccess", "Обновлено");
        messages = new Messages(owner, lava, config);
    }

    @Test
    void countdownRenewsOneStatusInsteadOfQueuingNotifications() {
        messages.status(player, "10 секунд");
        messages.status(player, "9 секунд");
        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(hud, times(2)).publishStatus(eq(owner), eq(player), eq("pool:timer"), text.capture(), eq(30), eq(2500L));
        assertEquals(List.of("Осталось: 10 секунд", "Осталось: 9 секунд"),
                text.getAllValues().stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList());
        verify(hud, never()).publish(any(), any(), anyString(), any(), anyInt(), anyLong());
        verifyNoInteractions(player);
    }

    @Test
    void leavingWithdrawsOnlyOurStatusAndPublishesOneNotice() {
        messages.transition(player, false);
        verify(hud).withdrawStatus(owner, player, "pool:timer");
        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(hud).publish(eq(owner), eq(player), eq("pool:transition"), text.capture(), eq(40), eq(2400L));
        assertEquals("Вы вышли", PlainTextComponentSerializer.plainText().serialize(text.getValue()));
        verifyNoInteractions(player);
    }

    @Test
    void rewardLinesBecomeOneNoticeWithoutBlankLines() {
        Component reward = messages.line(List.of("", " &6Награда получена ", "", "&f3 осколка", ""));
        messages.notice(player, "reward", reward);
        assertEquals("Награда получена 3 осколка", PlainTextComponentSerializer.plainText().serialize(reward));
        verify(hud).publish(owner, player, "pool:reward", reward, 40, 2400L);
    }

    @Test
    void commandRepliesRemainReadableInConsole() {
        CommandSender console = mock(CommandSender.class);
        messages.send(console, "reloadSuccess");
        verify(console).sendMessage(any(Component.class));
        verifyNoInteractions(hud);
    }
}
