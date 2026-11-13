package gg.fotia.tags.hook.placeholders;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AsyncLookupCacheTest {
    @Test
    void deduplicatesInFlightLoadsAndRetriesExpiredValuesWithoutBlocking() {
        AtomicLong now = new AtomicLong();
        var loads = new ArrayList<CompletableFuture<String>>();
        var cache = new AsyncLookupCache<String, String>(2, 1, key -> {
            var pending = new CompletableFuture<String>(); loads.add(pending); return pending;
        }, value -> 100L, failure -> fail(failure), now::get);
        assertNull(cache.get("player"));
        assertNull(cache.get("player"));
        assertNull(cache.get("other"));
        assertEquals(1, loads.size());
        loads.get(0).complete("first");
        assertEquals("first", cache.get("player"));
        now.set(100);
        assertEquals("first", cache.get("player"));
        assertEquals("first", cache.get("player"));
        assertEquals(2, loads.size());
        loads.get(1).complete("updated");
        assertEquals("updated", cache.get("player"));
    }

    @Test
    void periodicAsyncRefreshKeepsLastValueVisibleWhenEveryReadExceedsTtl() {
        AtomicLong now = new AtomicLong();
        var loads = new ArrayList<CompletableFuture<String>>();
        var cache = new AsyncLookupCache<String, String>(2, 1, key -> {
            var future = new CompletableFuture<String>(); loads.add(future); return future;
        }, value -> 50L, failure -> fail(failure), now::get);
        assertNull(cache.get("target"));
        loads.get(0).complete("title-0");
        for (int refresh = 1; refresh <= 5; refresh++) {
            now.addAndGet(1000);
            assertEquals("title-" + (refresh - 1), cache.get("target"));
            assertEquals(refresh + 1, loads.size());
            loads.get(refresh).complete("title-" + refresh);
        }
        assertEquals("title-5", cache.get("target"));
    }

    @Test
    void evictionAndInvalidationPreventStaleCompletionFromReplacingNewState() {
        var old = new CompletableFuture<String>();
        AtomicInteger calls = new AtomicInteger();
        var cache = new AsyncLookupCache<String, String>(1, 2,
                key -> calls.incrementAndGet() == 1 ? old : CompletableFuture.completedFuture(key),
                value -> 1000L, failure -> fail(failure), () -> 0L);
        assertNull(cache.get("a"));
        cache.invalidate((key, value) -> true);
        assertEquals("a", cache.get("a"));
        old.complete("outdated");
        assertEquals("a", cache.get("a"));
        assertEquals("b", cache.get("b"));
        assertEquals("a", cache.get("a"));
        assertEquals(4, calls.get());
        cache.close();
        assertNull(cache.get("c"));
        assertEquals(4, calls.get());
    }

    @Test
    void failuresReleaseThePendingSlotAndAreTemporarilyCached() {
        AtomicLong now = new AtomicLong();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        var cache = new AsyncLookupCache<String, String>(2, 1, key -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("database unavailable"));
        }, value -> 100L, failure -> failures.incrementAndGet(), now::get);
        assertNull(cache.get("a"));
        assertNull(cache.get("a"));
        assertEquals(1, calls.get());
        assertNull(cache.get("b"));
        assertEquals(2, calls.get());
        now.set(1000);
        assertNull(cache.get("a"));
        assertEquals(3, failures.get());
    }

    @Test
    void concurrentReadersShareOnePendingRequest() {
        AtomicInteger calls = new AtomicInteger();
        var pending = new CompletableFuture<String>();
        var cache = new AsyncLookupCache<String, String>(4, 2, key -> {
            calls.incrementAndGet(); return pending;
        }, value -> 1000L, failure -> fail(failure), () -> 0L);
        java.util.stream.IntStream.range(0, 64).parallel().forEach(ignored -> assertNull(cache.get("same")));
        assertEquals(1, calls.get());
        pending.complete("ready");
        assertEquals("ready", cache.get("same"));
    }
}
