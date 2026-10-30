package dev.svarcovv.lavaPool;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreTest {
    @Test
    void queueStopsAfterTimeBudgetAndNeverDuplicatesPlayer() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        RewardQueue<String> queue = new RewardQueue<>(clock::get);
        assertTrue(queue.offer("one", () -> {
            calls.incrementAndGet();
            clock.addAndGet(3_000_000);
            return true;
        }));
        assertFalse(queue.offer("one", () -> fail("Повторная награда")));
        queue.offer("two", () -> { calls.incrementAndGet(); return true; });
        queue.drain(4, 2_000_000);
        assertEquals(1, calls.get());
        assertEquals(1, queue.size());
        queue.drain(4, 2_000_000);
        assertEquals(2, calls.get());
    }

    @Test
    void invalidTimesAndUnsafeNamesAreRejected() {
        assertEquals(60, Pool.duration("1m"));
        assertEquals(3600, Pool.duration("1h"));
        assertEquals(5, Pool.duration("5s"));
        for (String text : List.of("0", "-1s", "abc", "999999999999m", "1d")) {
            assertThrows(IllegalArgumentException.class, () -> Pool.duration(text));
        }
        assertFalse(Pool.validName("../config"));
        assertFalse(Pool.validName("C:\\data"));
        assertTrue(Pool.validName("premium-2"));
    }

    @Test
    void selectedEdgeBlocksAreIncludedAndOtherWorldsExcluded() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Pool pool = new Pool("a", false, "world", -16, 0, -16, -1, 100, -1, 60, 150, 2, List.of());
        PoolIndex index = new PoolIndex(List.of(pool));
        assertSame(pool, index.find(new Location(world, -0.01, 50, -0.01)));
        assertNull(index.find(new Location(world, 0, 50, -0.01)));
        World other = mock(World.class);
        when(other.getName()).thenReturn("other");
        assertNull(index.find(new Location(other, -5, 50, -5)));
    }

    @Test
    void hugeZonesDoNotAllocateMillionsOfChunkEntries() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Pool pool = new Pool("a", false, "world", -30_000_000, -64, -30_000_000,
                30_000_000, 320, 30_000_000, 60, 150, 2, List.of());
        assertSame(pool, new PoolIndex(List.of(pool)).find(new Location(world, 1, 50, 1)));
    }

    @Test
    void russianTimerUsesCorrectEndings() {
        assertEquals("21 секунда", Messages.time(21));
        assertEquals("11 секунд", Messages.time(11));
        assertEquals("1 минута 2 секунды", Messages.time(62));
        assertEquals("22 минуты", Messages.time(22 * 60));
    }
}
