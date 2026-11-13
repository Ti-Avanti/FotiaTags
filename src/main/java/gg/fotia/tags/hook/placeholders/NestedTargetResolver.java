package gg.fotia.tags.hook.placeholders;

import gg.fotia.tags.FotiaTags;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** 在主线程解析任意扩展提供的目标变量，并限制递归、缓存容量和待处理请求数。 */
final class NestedTargetResolver implements AutoCloseable {
    private final FotiaTags plugin;
    private final ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> false);
    private final AsyncLookupCache<Context, String> cache;
    private volatile boolean closed;

    NestedTargetResolver(FotiaTags plugin, TargetPlaceholderSettings settings, Consumer<Throwable> errors) {
        this.plugin = plugin;
        cache = new AsyncLookupCache<>(settings.capacity(), settings.maxPending(), this::load,
                ignored -> settings.onlineTtl(), errors, System::currentTimeMillis);
    }

    String resolve(OfflinePlayer viewer, String expression) {
        if (closed || resolving.get() || expression.length() > 256) return "";
        return cache.get(new Context(viewer, expression));
    }

    private CompletableFuture<String> load(Context context) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Runnable action = () -> {
            if (closed || !plugin.isEnabled()) {
                future.cancel(false);
                return;
            }
            resolving.set(true);
            try {
                future.complete(PlaceholderAPI.setBracketPlaceholders(context.viewer(), context.expression()));
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            } finally {
                resolving.remove();
            }
        };
        if (Bukkit.isPrimaryThread()) action.run();
        else {
            try {
                Bukkit.getScheduler().runTask(plugin, action);
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }
        return future;
    }

    void invalidate(UUID viewer) {
        cache.invalidate((key, value) -> key.viewer() != null && viewer.equals(key.viewer().getUniqueId()));
    }

    @Override
    public void close() {
        closed = true;
        cache.close();
    }

    private record Context(OfflinePlayer viewer, String expression) {}
}
