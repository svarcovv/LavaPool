package dev.svarcovv.lavaPool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

final class RewardQueue<K> {
    interface Job {
        boolean step();
    }

    private final Map<K, Job> jobs = new LinkedHashMap<>();
    private final LongSupplier clock;

    RewardQueue() {
        this(System::nanoTime);
    }

    RewardQueue(LongSupplier clock) {
        this.clock = clock;
    }

    boolean offer(K key, Job job) {
        return jobs.putIfAbsent(key, job) == null;
    }

    void remove(K key) {
        jobs.remove(key);
    }

    int size() {
        return jobs.size();
    }

    void drain(int limit, long budgetNanos) {
        long start = clock.getAsLong();
        for (int i = 0; i < limit && !jobs.isEmpty(); i++) {
            if (i > 0 && clock.getAsLong() - start >= budgetNanos) {
                break;
            }
            Map.Entry<K, Job> entry = jobs.entrySet().iterator().next();
            if (entry.getValue().step()) {
                jobs.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
