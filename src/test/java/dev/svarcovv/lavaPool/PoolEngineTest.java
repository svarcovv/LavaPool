package dev.svarcovv.lavaPool;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PoolEngineTest {
    private Server server;
    private World world;
    private JavaPlugin plugin;
    private Messages messages;
    private List<String> executed;
    private AtomicInteger checks;

    @BeforeEach
    void setup() {
        server = mock(Server.class);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        plugin = mock(JavaPlugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
        when(server.createCommandSender(any())).thenReturn(mock(CommandSender.class));
        YamlConfiguration config = new YamlConfiguration();
        config.set("actionBar", "%timer%");
        config.set("premiumDeny", "premium denied");
        config.set("ipLimit", "ip limit");
        config.set("rewardMessageFree", List.of("reward"));
        messages = new Messages(config);
        executed = new ArrayList<>();
        checks = new AtomicInteger();
        when(server.dispatchCommand(any(), anyString())).thenAnswer(invocation -> {
            executed.add(invocation.getArgument(1));
            return true;
        });
    }

    private Settings settings(boolean pause, int ipLimit) {
        return new Settings(pause, "denyMessage", true, ipLimit, false, 4, 1_000_000_000L, true, 30);
    }

    private Pool pool(String name, int x, int seconds, boolean premium, int commands) {
        List<String> reward = new ArrayList<>();
        for (int i = 0; i < commands; i++) {
            reward.add("reward" + i + " %player_name%");
        }
        return new Pool(name, premium, "world", x, 0, 0, x + 9, 100, 9, seconds, 150, 2, reward);
    }

    private Player player(String name, int ip) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.isInWater()).thenReturn(true);
        when(player.getAddress()).thenReturn(new InetSocketAddress("192.0.2." + ip, 25565));
        when(player.getLocation(any(Location.class))).thenAnswer(invocation -> {
            checks.incrementAndGet();
            Location location = invocation.getArgument(0);
            location.setWorld(world);
            location.setX(5);
            location.setY(50);
            location.setZ(5);
            return location;
        });
        return player;
    }

    private PoolEngine engine(Settings settings, Pool... pools) {
        return new PoolEngine(plugin, settings, messages, List.of(pools), (player, text) -> text);
    }

    private void ticks(PoolEngine engine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            engine.tick();
        }
    }

    @Test
    void hundredPlayersAreSpreadAcrossTicksAndAllReceiveRewards() {
        PoolEngine engine = engine(settings(true, 0), pool("afk", 0, 1, false, 2));
        for (int i = 0; i < 100; i++) {
            engine.add(player("user" + i, i + 1));
        }
        for (int i = 0; i < 200; i++) {
            checks.set(0);
            int before = executed.size();
            engine.tick();
            assertTrue(executed.size() - before <= 4, "Превышен лимит команд за тик");
            assertTrue(checks.get() <= 9, "Проверки 100 игроков собрались в один тик");
        }
        HashSet<String> rewarded = new HashSet<>();
        for (String command : executed) {
            if (command.startsWith("reward1 ")) {
                rewarded.add(command.split(" ")[1]);
            }
        }
        assertEquals(100, rewarded.size());
        assertTrue(executed.size() > 200);
    }

    @Test
    void pauseResumesOnlyWhenPlayerReturnsToSamePool() {
        PoolEngine engine = engine(settings(true, 0), pool("a", 0, 3, false, 1));
        Player player = player("one", 1);
        engine.add(player);
        ticks(engine, 60);
        assertTrue(executed.isEmpty());
        when(player.isInWater()).thenReturn(false);
        ticks(engine, 40);
        when(player.isInWater()).thenReturn(true);
        ticks(engine, 20);
        assertTrue(executed.isEmpty());
        ticks(engine, 20);
        assertEquals(1, executed.size());
    }

    @Test
    void disabledPauseResetsProgress() {
        PoolEngine engine = engine(settings(false, 0), pool("a", 0, 3, false, 1));
        Player player = player("one", 1);
        engine.add(player);
        ticks(engine, 60);
        when(player.isInWater()).thenReturn(false);
        ticks(engine, 20);
        when(player.isInWater()).thenReturn(true);
        ticks(engine, 60);
        assertTrue(executed.isEmpty());
        ticks(engine, 20);
        assertEquals(1, executed.size());
    }

    @Test
    void oneIpCannotReceiveRewardsOnFourAccountsAndSlotIsReleasedOnQuit() {
        PoolEngine engine = engine(settings(true, 3), pool("a", 0, 1, false, 1));
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Player player = player("one" + i, 1);
            players.add(player);
            engine.add(player);
        }
        ticks(engine, 40);
        assertEquals(3, executed.size());
        String paidName = executed.getFirst().split(" ")[1];
        Player paid = players.stream().filter(p -> p.getName().equals(paidName)).findFirst().orElseThrow();
        engine.remove(paid.getUniqueId());
        ticks(engine, 40);
        assertTrue(executed.stream().anyMatch(cmd -> cmd.endsWith("one0")));
    }

    @Test
    void premiumDenialDoesNotSpam() {
        PoolEngine engine = engine(settings(true, 0), pool("a", 0, 1, true, 1));
        Player player = player("one", 1);
        engine.add(player);
        ticks(engine, 400);
        assertTrue(executed.isEmpty());
        verify(player, times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void quittingCancelsRemainingCommandsWithoutRepeatingPartialReward() {
        PoolEngine engine = engine(settings(true, 0), pool("a", 0, 1, false, 10));
        Player player = player("one", 1);
        engine.add(player);
        ticks(engine, 40);
        assertEquals(4, executed.size());
        engine.remove(player.getUniqueId());
        ticks(engine, 100);
        assertEquals(4, executed.size());
    }

    @Test
    void permissionIsCheckedAgainBeforeQueuedCommands() {
        PoolEngine engine = engine(settings(true, 0), pool("a", 0, 1, true, 10));
        Player player = player("one", 1);
        when(player.hasPermission("lavapool.type.premium")).thenReturn(true);
        engine.add(player);
        ticks(engine, 40);
        assertEquals(4, executed.size());
        when(player.hasPermission("lavapool.type.premium")).thenReturn(false);
        ticks(engine, 1);
        assertEquals(4, executed.size());
    }

    @Test
    void failingCommandDoesNotRetryOrSendSuccessAndWarningsAreLimited() {
        when(server.dispatchCommand(any(), anyString())).thenReturn(false);
        PoolEngine engine = engine(settings(true, 0), pool("a", 0, 1, false, 2));
        Player player = player("one", 1);
        engine.add(player);
        ticks(engine, 400);
        verify(plugin.getLogger(), times(1)).warning(anyString());
        verify(player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(server, never()).dispatchCommand(any(), eq("reward1 one"));
    }

    @Test
    void closingEngineCancelsAllPendingRewards() {
        PoolEngine engine = engine(settings(true, 0), pool("a", 0, 1, false, 10));
        engine.add(player("one", 1));
        ticks(engine, 40);
        engine.close();
        ticks(engine, 100);
        assertEquals(4, executed.size());
    }
}
