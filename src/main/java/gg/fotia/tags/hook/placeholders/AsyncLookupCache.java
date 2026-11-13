package gg.fotia.tags.hook.placeholders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/** 有容量限制的异步查询缓存；同一目标只发起一次并发加载，读取永不等待数据库。 */
public final class AsyncLookupCache<K, V> implements AutoCloseable {
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int capacity;
    private final int maxPending;
    private final Function<K, CompletableFuture<V>> loader;
    private final ToLongFunction<V> ttl;
    private final Consumer<Throwable> errors;
    private final LongSupplier clock;
    private int pending;
    private boolean closed;

    /** 配置缓存容量、并发上限、加载器、有效期及错误处理。 */
    public AsyncLookupCache(int capacity, int maxPending, Function<K, CompletableFuture<V>> loader,
                            ToLongFunction<V> ttl, Consumer<Throwable> errors, LongSupplier clock) {
        this.capacity = Math.max(1, capacity);
        this.maxPending = Math.max(1, maxPending);
        this.loader = loader;
        this.ttl = ttl;
        this.errors = errors;
        this.clock = clock;
    }

    /** 首次加载返回 null；刷新期间继续读取已有快照，避免异步调用一直只拿到加载状态。 */
    public V get(K key) {
        Entry<V> entry;
        synchronized (this) {
            if (closed) return null;
            entry = entries.get(key);
            if (entry != null && (entry.loading || clock.getAsLong() < entry.expires)) return entry.value;
            if (pending >= maxPending) return entry == null ? null : entry.value;
            if (entry == null && entries.size() >= capacity) {
                var iterator = entries.entrySet().iterator();
                boolean removed = false;
                while (iterator.hasNext()) {
                    if (!iterator.next().getValue().loading) {
                        iterator.remove();
                        removed = true;
                        break;
                    }
                }
                if (!removed) return null;
            }
            V previous = entry == null ? null : entry.value;
            entry = new Entry<>();
            entry.value = previous;
            entries.put(key, entry);
            pending++;
        }
        Entry<V> request = entry;
        try {
            loader.apply(key).whenComplete((value, failure) -> complete(key, request, value, failure));
        } catch (RuntimeException failure) {
            complete(key, request, null, failure);
        }
        synchronized (this) {
            return entries.get(key) == request ? request.value : null;
        }
    }

    private void complete(K key, Entry<V> request, V value, Throwable failure) {
        boolean report;
        synchronized (this) {
            if (!request.loading) return;
            request.loading = false;
            pending--;
            report = !closed && failure != null;
            if (!closed && entries.get(key) == request) {
                request.value = failure == null ? value : null;
                request.expires = clock.getAsLong() + (failure == null ? Math.max(1, ttl.applyAsLong(value)) : 1000L);
            }
        }
        if (report) errors.accept(failure);
    }

    /** 删除匹配目标；失效前发起的查询不能再次写回被删除的条目。 */
    public synchronized void invalidate(BiPredicate<K, V> matches) {
        entries.entrySet().removeIf(entry -> matches.test(entry.getKey(), entry.getValue().value));
    }

    @Override
    public synchronized void close() {
        closed = true;
        entries.clear();
    }

    private static final class Entry<V> {
        private V value;
        private long expires;
        private boolean loading = true;
    }
}
