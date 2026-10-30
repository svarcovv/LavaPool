package dev.svarcovv.lavaPool;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.net.InetAddress;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

final class PoolEngine {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Messages messages;
    private final PoolIndex index;
    private final BiFunction<Player, String, String> placeholders;
    private final Map<UUID, State> players = new HashMap<>();
    private final List<Map<UUID, State>> buckets = new ArrayList<>();
    private final Map<InetAddress, LinkedHashSet<UUID>> addresses = new HashMap<>();
    private final RewardQueue<UUID> rewards = new RewardQueue<>();
    private final CommandSender sender;
    private final CommandSender vanillaSender;
    private long tick;
    private long nextBroadcast;
    private long nextWarning;
    private int nextBucket;

    PoolEngine(JavaPlugin plugin, Settings settings, Messages messages, List<Pool> pools,
               BiFunction<Player, String, String> placeholders) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.index = new PoolIndex(pools);
        this.placeholders = placeholders;
        sender = settings.silent() ? QuietConsole.wrap(plugin.getServer().getConsoleSender()) : plugin.getServer().getConsoleSender();
        vanillaSender = settings.silent() ? plugin.getServer().createCommandSender(message -> { }) : sender;
        for (int i = 0; i < 20; i++) {
            buckets.add(new HashMap<>());
        }
    }

    void add(Player player) {
        remove(player.getUniqueId());
        State state = new State(player, nextBucket++ % 20);
        players.put(player.getUniqueId(), state);
        buckets.get(state.bucket).put(player.getUniqueId(), state);
    }

    void remove(UUID uuid) {
        State state = players.remove(uuid);
        if (state != null) {
            if (state.active != null) {
                messages.clearStatus(state.player);
            }
            release(state);
            buckets.get(state.bucket).remove(uuid);
        }
        rewards.remove(uuid);
    }

    void close() {
        for (State state : List.copyOf(players.values())) {
            remove(state.player.getUniqueId());
        }
    }

    void tick() {
        tick++;
        Map<UUID, State> bucket = buckets.get((int) (tick % 20));
        for (State state : List.copyOf(bucket.values())) {
            if (players.get(state.player.getUniqueId()) != state) {
                continue;
            }
            try {
                update(state);
            } catch (RuntimeException exception) {
                warn("Ошибка проверки зоны: " + exception.getClass().getSimpleName());
            }
        }
        rewards.drain(settings.commandsPerTick(), settings.budgetNanos());
    }

    private void update(State state) {
        Player player = state.player;
        if (!player.isOnline()) {
            remove(player.getUniqueId());
            return;
        }
        Pool pool = player.isDead() ? null : index.find(player.getLocation(state.location));
        if (pool != null && !allowed(player, pool)) {
            deny(state, pool);
            pool = null;
        } else {
            state.deniedPool = null;
        }
        if (pool == null) {
            leave(state);
            return;
        }
        if (state.active != pool) {
            leave(state);
            state.active = pool;
            state.lastCheck = tick;
            if (!reserve(state)) {
                state.active = null;
                if (tick >= state.nextNotice) {
                    messages.notice(player, "ipLimit");
                    state.nextNotice = tick + 200;
                }
                return;
            }
            messages.transition(player, true);
        } else if (!state.pending) {
            int elapsed = (int) Math.min(20, tick - state.lastCheck);
            long limit = (long) pool.seconds() * 20;
            state.progress.merge(pool.name(), (long) elapsed, (a, b) -> Math.min(limit, a + b));
            state.lastCheck = tick;
        } else {
            state.lastCheck = tick;
        }
        long spent = state.progress.getOrDefault(pool.name(), 0L);
        String time = Messages.time((int) Math.max(0, pool.seconds() - spent / 20));
        messages.status(player, time);
        if (!state.pending && spent / 20 >= pool.seconds() && !pool.commands().isEmpty()) {
            state.pending = true;
            state.progress.put(pool.name(), 0L);
            rewards.offer(player.getUniqueId(), new Reward(state, pool));
        }
    }

    private void leave(State state) {
        if (state.active == null) {
            return;
        }
        if (!settings.pauseTimer()) {
            state.progress.remove(state.active.name());
        }
        rewards.remove(state.player.getUniqueId());
        state.pending = false;
        state.active = null;
        release(state);
        messages.transition(state.player, false);
    }

    private boolean reserve(State state) {
        if (settings.ipLimit() == 0) {
            return true;
        }
        if (state.player.getAddress() == null || state.player.getAddress().getAddress() == null) {
            return false;
        }
        InetAddress address = state.player.getAddress().getAddress();
        LinkedHashSet<UUID> group = addresses.computeIfAbsent(address, ignored -> new LinkedHashSet<>());
        if (group.size() >= settings.ipLimit()) {
            return false;
        }
        group.add(state.player.getUniqueId());
        state.address = address;
        return true;
    }

    private void release(State state) {
        if (state.address != null) {
            LinkedHashSet<UUID> group = addresses.get(state.address);
            if (group != null) {
                group.remove(state.player.getUniqueId());
                if (group.isEmpty()) {
                    addresses.remove(state.address);
                }
            }
            state.address = null;
        }
    }

    private boolean allowed(Player player, Pool pool) {
        return !pool.premium() || player.hasPermission("lavapool.type.premium") || player.hasPermission("w1npool.type.premium");
    }

    private void deny(State state, Pool pool) {
        if (pool.name().equals(state.deniedPool)) {
            return;
        }
        state.deniedPool = pool.name();
        String action = settings.premiumAction();
        if (action.contains("push")) {
            Vector direction = state.player.getLocation().toVector().subtract(new Vector(
                    (pool.minX() + pool.maxX()) / 2.0, state.player.getY(), (pool.minZ() + pool.maxZ()) / 2.0)).setY(0);
            if (direction.lengthSquared() < 0.001) {
                direction.setZ(1);
            }
            state.player.setVelocity(direction.normalize().multiply(0.8).setY(0.42));
        }
        if (action.contains("tp")) {
            state.player.teleportAsync(state.player.getWorld().getSpawnLocation());
        }
        if (tick >= state.nextNotice && action.contains("denymessage")) {
            messages.notice(state.player, "premiumDeny");
            state.nextNotice = tick + 100;
        }
    }

    private void warn(String text) {
        if (tick >= nextWarning) {
            plugin.getLogger().warning(text);
            nextWarning = tick + 1200;
        }
    }

    private final class Reward implements RewardQueue.Job {
        private final State state;
        private final Pool pool;
        private final long amount;
        private int command;

        private Reward(State state, Pool pool) {
            this.state = state;
            this.pool = pool;
            DayOfWeek day = LocalDate.now().getDayOfWeek();
            boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            amount = pool.reward() * (settings.useMultiplier() && weekend ? pool.weekendMultiplier() : 1);
        }

        @Override
        public boolean step() {
            Player player = state.player;
            if (players.get(player.getUniqueId()) != state || state.active != pool || !player.isOnline()
                    || player.isDead() || index.find(player.getLocation(state.location)) != pool
                    || !allowed(player, pool)) {
                state.pending = false;
                return true;
            }
            try {
                String text = format(pool.commands().get(command++));
                String root = text.split(" ", 2)[0];
                CommandSender commandSender = plugin.getServer().getPluginCommand(root) == null ? vanillaSender : sender;
                if (text.isBlank() || text.contains("\n") || text.contains("\r")
                        || !plugin.getServer().dispatchCommand(commandSender, text)) {
                    warn("Не выполнена команда награды зоны " + pool.name() + ". Проверьте commandsReward.");
                    state.pending = false;
                    return true;
                }
                if (command == pool.commands().size()) {
                    state.pending = false;
                    if (players.get(player.getUniqueId()) == state && player.isOnline()) {
                        boolean broadcast = settings.broadcast() && tick >= nextBroadcast;
                        if (broadcast) {
                            nextBroadcast = tick + settings.broadcastCooldownSeconds() * 20L;
                        }
                        Component message = messages.line(messages.rewards(pool).stream().map(this::format).toList());
                        if (broadcast) {
                            for (Player online : plugin.getServer().getOnlinePlayers()) {
                                messages.notice(online, "reward", message);
                            }
                        } else {
                            messages.notice(player, "reward", message);
                        }
                    }
                    return true;
                }
                return false;
            } catch (RuntimeException exception) {
                state.pending = false;
                warn("Ошибка награды зоны " + pool.name() + ": " + exception.getClass().getSimpleName());
                return true;
            }
        }

        private String format(String text) {
            return placeholders.apply(state.player, text.replace("%player_name%", state.player.getName())
                    .replace("%player%", state.player.getName()).replace("%money%", Long.toString(amount)));
        }
    }

    private static final class State {
        final Player player;
        final int bucket;
        final Map<String, Long> progress = new HashMap<>();
        final Location location = new Location(null, 0, 0, 0);
        Pool active;
        InetAddress address;
        String deniedPool;
        boolean pending;
        long lastCheck;
        long nextNotice;

        State(Player player, int bucket) {
            this.player = player;
            this.bucket = bucket;
        }
    }
}
