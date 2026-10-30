package dev.svarcovv.lavaPool;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PoolIndex {
    private final Map<String, Map<Long, List<Pool>>> chunks = new HashMap<>();
    private final Map<String, List<Pool>> large = new HashMap<>();

    PoolIndex(List<Pool> pools) {
        for (Pool pool : pools) {
            int minX = pool.minX() >> 4;
            int maxX = pool.maxX() >> 4;
            int minZ = pool.minZ() >> 4;
            int maxZ = pool.maxZ() >> 4;
            if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > 4096) {
                large.computeIfAbsent(pool.world(), key -> new ArrayList<>()).add(pool);
                continue;
            }
            Map<Long, List<Pool>> world = chunks.computeIfAbsent(pool.world(), key -> new HashMap<>());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(pool);
                }
            }
        }
    }

    Pool find(Location location) {
        String name = location.getWorld().getName();
        Map<Long, List<Pool>> world = chunks.get(name);
        Pool found = world == null ? null : first(world.get(key(location.getBlockX() >> 4, location.getBlockZ() >> 4)), location);
        Pool broad = first(large.get(name), location);
        if (found == null) {
            return broad;
        }
        return broad != null && broad.name().compareTo(found.name()) < 0 ? broad : found;
    }

    private Pool first(List<Pool> pools, Location location) {
        if (pools != null) {
            for (Pool pool : pools) {
                if (pool.contains(location)) {
                    return pool;
                }
            }
        }
        return null;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
