package gg.fotia.tags.hook.placeholders;

import gg.fotia.tags.FotiaTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

/** 通用目标玩家查询：在线优先，离线异步加载，仅向调用线程公开不可变快照。 */
public final class TargetTagService implements Listener, AutoCloseable {
    private final FotiaTags plugin;
    private final TargetTagSnapshotFactory snapshots;
    private final AtomicLong lastWarning = new AtomicLong();
    private volatile State state;
    private volatile boolean closed;

    /** 初始化查询缓存和玩家上下线失效监听。 */
    public TargetTagService(FotiaTags plugin) {
        this.plugin = plugin;
        snapshots = new TargetTagSnapshotFactory(plugin);
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** 在主线程重载设置并丢弃旧缓存，防止配置更新后继续输出旧称号。 */
    public void reload() {
        if (state != null) {
            state.cache().close();
            state.nested().close();
        }
        var settings = TargetPlaceholderSettings.read(plugin.getConfigManager().getConfig());
        String errorMessage = PlainTextComponentSerializer.plainText().serialize(
                plugin.getMessageManager().get("placeholder-target-load-failed"));
        var cache = new AsyncLookupCache<String, TagPlaceholderSnapshot>(settings.capacity(), settings.maxPending(),
                this::load, snapshot -> snapshot == TagPlaceholderSnapshot.MISSING ? 1000L
                : snapshot.online() ? settings.onlineTtl() : settings.offlineTtl(),
                error -> warn(errorMessage, error), System::currentTimeMillis);
        var nested = new NestedTargetResolver(plugin, settings, error -> warn(errorMessage, error));
        state = new State(settings, cache, nested, errorMessage);
    }

    /** 将大括号包裹的 PAPI 表达式解析为目标身份，直接玩家名及 UUID 无需二次解析。 */
    public String query(String field, OfflinePlayer viewer, String target) {
        State current = state;
        if (closed || !current.settings().enabled()) return current.settings().unavailable();
        if (target.indexOf('{') >= 0) {
            target = current.nested().resolve(viewer, target);
            if (target == null) return current.settings().loading();
        }
        return value(field, target);
    }

    /** 读取指定目标字段。未知身份不会回退为观看者，首次离线加载也不会阻塞 PAPI。 */
    public String value(String field, String target) {
        State current = state;
        String identity = PlaceholderQuery.identity(target);
        if (closed || !current.settings().enabled() || identity == null) return current.settings().unavailable();
        var snapshot = current.cache().get(identity);
        if (snapshot == null) return current.settings().loading();
        if (snapshot.uuid() == null) return current.settings().unavailable();
        try {
            return snapshot.value(field, System.currentTimeMillis());
        } catch (RuntimeException failure) {
            warn(current.errorMessage(), failure);
            return current.settings().unavailable();
        }
    }

    private CompletableFuture<TagPlaceholderSnapshot> load(String identity) {
        return onMain(() -> {
            Player player = identity.length() == 36 ? Bukkit.getPlayer(UUID.fromString(identity))
                    : Bukkit.getPlayerExact(identity);
            var data = player == null ? null : plugin.getTagManager().getPlayerData(player.getUniqueId());
            return data == null ? null : snapshots.capture(data, true);
        }).thenCompose(online -> {
            if (online != null) return CompletableFuture.completedFuture(online);
            return plugin.getDatabaseManager().findPlayerId(identity).thenCompose(id -> {
                if (id.isEmpty()) return CompletableFuture.completedFuture(TagPlaceholderSnapshot.MISSING);
                return plugin.getDatabaseManager().loadPlayerData(id.get()).thenCompose(data -> onMain(() -> {
                    // 数据库返回前目标可能已上线，使用较新的主线程玩家数据。
                    var live = plugin.getTagManager().getPlayerData(id.get());
                    return snapshots.capture(live == null ? data : live, live != null);
                }));
            });
        });
    }

    private <T> CompletableFuture<T> onMain(Supplier<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed || !plugin.isEnabled()) {
            future.cancel(false);
            return future;
        }
        Runnable task = () -> {
            try {
                if (closed) future.cancel(false);
                else future.complete(action.get());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        };
        if (Bukkit.isPrimaryThread()) task.run();
        else {
            try {
                Bukkit.getScheduler().runTask(plugin, task);
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }
        return future;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    private void invalidate(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName().toLowerCase(Locale.ROOT);
        state.cache().invalidate((key, snapshot) -> key.equals(name) || key.equals(id.toString())
                || snapshot != null && id.equals(snapshot.uuid()));
        state.nested().invalidate(id);
    }

    private void warn(String message, Throwable failure) {
        long now = System.currentTimeMillis();
        long previous = lastWarning.get();
        if (now - previous < 30000 || !lastWarning.compareAndSet(previous, now)) return;
        plugin.getLogger().log(Level.WARNING, message, failure);
    }

    @Override
    public void close() {
        closed = true;
        state.cache().close();
        state.nested().close();
        HandlerList.unregisterAll(this);
    }

    private record State(TargetPlaceholderSettings settings,
                         AsyncLookupCache<String, TagPlaceholderSnapshot> cache,
                         NestedTargetResolver nested, String errorMessage) {}
}
