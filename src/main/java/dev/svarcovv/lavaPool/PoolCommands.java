package dev.svarcovv.lavaPool;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class PoolCommands implements CommandExecutor, TabCompleter, Listener {
    private final LavaPool plugin;
    private final NamespacedKey selector;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    PoolCommands(LavaPool plugin) {
        this.plugin = plugin;
        selector = new NamespacedKey(plugin, "selector");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (List.of("wand", "create", "remove", "reload").contains(sub) && !admin(sender)) {
            messages.send(sender, "noPermission");
            return true;
        }
        if (sub.equals("reload")) {
            try {
                plugin.reloadData();
                plugin.messages().send(sender, "reloadSuccess");
            } catch (Exception exception) {
                messages.send(sender, "reloadError", "%error%", exception.getMessage());
            }
            return true;
        }
        if (sub.equals("remove")) {
            if (args.length != 2) {
                messages.send(sender, "removeUsage");
            } else if (!Pool.validName(args[1]) || find(args[1]) == null) {
                messages.send(sender, "poolNotFound", "%name%", args[1]);
            } else {
                try {
                    Files.delete(plugin.poolFile(args[1]).toPath());
                    plugin.reloadData();
                    messages.send(sender, "poolRemoved", "%name%", args[1]);
                } catch (Exception exception) {
                    messages.send(sender, "poolRemoveError");
                }
            }
            return true;
        }
        if (sub.equals("help")) {
            messages.send(sender, "help");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "onlyPlayers");
            return true;
        }
        switch (sub) {
            case "wand" -> {
                ItemStack wand = new ItemStack(Material.DIAMOND_AXE);
                wand.editMeta(meta -> {
                    meta.displayName(Component.text("Pool Selector"));
                    meta.getPersistentDataContainer().set(selector, PersistentDataType.BYTE, (byte) 1);
                });
                if (player.getInventory().addItem(wand).isEmpty()) {
                    messages.send(player, "wandReceived");
                }
            }
            case "create" -> create(player, args);
            case "teleport", "tp" -> {
                if (args.length != 2) {
                    messages.send(player, "teleportUsage");
                } else {
                    Pool pool = find(args[1]);
                    if (pool == null) {
                        messages.send(player, "poolNotFound", "%name%", args[1]);
                    } else if (pool.premium() && !premium(player)) {
                        messages.send(player, "premiumDeny");
                    } else {
                        teleport(player, pool);
                    }
                }
            }
            default -> messages.send(sender, "help");
        }
        return true;
    }

    @EventHandler(ignoreCancelled = false)
    public void select(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || !admin(event.getPlayer())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(selector, PersistentDataType.BYTE)) {
            return;
        }
        int point = event.getAction() == Action.LEFT_CLICK_BLOCK ? 0 : event.getAction() == Action.RIGHT_CLICK_BLOCK ? 1 : -1;
        if (point < 0) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        selections.computeIfAbsent(event.getPlayer().getUniqueId(), key -> new Location[2])[point] = block.getLocation();
        plugin.messages().send(event.getPlayer(), point == 0 ? "point1Set" : "point2Set",
                "%x%", Integer.toString(block.getX()), "%y%", Integer.toString(block.getY()), "%z%", Integer.toString(block.getZ()));
    }

    void clear(Player player) {
        selections.remove(player.getUniqueId());
    }

    private void create(Player player, String[] args) {
        Messages messages = plugin.messages();
        if (args.length != 3 || (!args[2].equalsIgnoreCase("free") && !args[2].equalsIgnoreCase("premium"))) {
            messages.send(player, "createUsage");
            return;
        }
        String name = args[1];
        if (!Pool.validName(name)) {
            messages.send(player, "invalidName");
            return;
        }
        if (plugin.poolFile(name).exists()) {
            messages.send(player, "poolExists", "%name%", name);
            return;
        }
        Location[] points = selections.get(player.getUniqueId());
        if (points == null || points[0] == null || points[1] == null || !points[0].getWorld().equals(points[1].getWorld())) {
            messages.send(player, "noSelection");
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("NamePool", name);
        config.set("type", args[2].toLowerCase(Locale.ROOT));
        config.set("time", "5m");
        config.set("world", points[0].getWorld().getName());
        for (int i = 0; i < 2; i++) {
            String key = "pos" + (i + 1);
            config.set(key + ".x", points[i].getBlockX());
            config.set(key + ".y", points[i].getBlockY());
            config.set(key + ".z", points[i].getBlockZ());
        }
        config.set("defaultReward", args[2].equalsIgnoreCase("premium") ? 300 : 150);
        config.set("weekendMultiple", 2);
        config.set("commandsReward", List.of("minecraft:give %player_name% minecraft:amethyst_shard 3"));
        try {
            config.save(plugin.poolFile(name));
            plugin.reloadData();
            clear(player);
            messages.send(player, "poolCreated", "%name%", name);
        } catch (Exception exception) {
            messages.send(player, "poolCreateError");
        }
    }

    private void teleport(Player player, Pool pool) {
        World world = plugin.getServer().getWorld(pool.world());
        if (world == null) {
            plugin.messages().send(player, "noSafeLocation", "%name%", pool.name());
            return;
        }
        int x = (int) (((long) pool.minX() + pool.maxX()) / 2);
        int z = (int) (((long) pool.minZ() + pool.maxZ()) / 2);
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!plugin.isEnabled()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location safe = null;
                for (int y = Math.min(pool.maxY() + 2, world.getMaxHeight() - 2); y > Math.max(world.getMinHeight(), pool.minY() - 1); y--) {
                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    Material floor = world.getBlockAt(x, y - 1, z).getType();
                    if (feet.isPassable() && head.isPassable() && floor.isSolid()
                            && !hazard(feet.getType()) && !hazard(head.getType()) && !hazard(floor)) {
                        safe = new Location(world, x + 0.5, y, z + 0.5);
                        break;
                    }
                }
                if (safe == null) {
                    plugin.messages().send(player, "noSafeLocation", "%name%", pool.name());
                } else {
                    player.teleportAsync(safe).thenAccept(success -> {
                        if (success && player.isOnline()) {
                            plugin.messages().send(player, "teleported", "%name%", pool.name());
                        }
                    });
                }
            });
        }).exceptionally(exception -> null);
    }

    private static boolean hazard(Material material) {
        return material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE
                || material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE || material == Material.POWDER_SNOW
                || material == Material.SWEET_BERRY_BUSH || material == Material.WITHER_ROSE;
    }

    private Pool find(String name) {
        return plugin.pools().stream().filter(pool -> pool.name().equals(name)).findFirst().orElse(null);
    }

    private static boolean admin(CommandSender sender) {
        return sender.hasPermission("lavapool.admin") || sender.hasPermission("w1npool.admin");
    }

    private static boolean premium(Player player) {
        return player.hasPermission("lavapool.type.premium") || player.hasPermission("w1npool.type.premium");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = List.of();
        if (args.length == 1) {
            options = admin(sender) ? List.of("wand", "create", "remove", "teleport", "reload") : List.of("teleport");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("teleport") || (admin(sender) && args[0].equalsIgnoreCase("remove")))) {
            options = plugin.pools().stream().map(Pool::name).toList();
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create") && admin(sender)) {
            options = List.of("free", "premium");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
