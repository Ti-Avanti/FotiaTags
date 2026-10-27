package gg.fotia.tags.hook;

import gg.fotia.basictool.common.reward.*;
import gg.fotia.tags.FotiaTags;
import org.bukkit.Bukkit;
import java.util.concurrent.*;

/** 与玩家购买的称号分表存储，撤销只作用于对应奖励来源。 */
public final class LevelRewards implements RewardProvider {
    private final FotiaTags plugin;
    private LevelRewards(FotiaTags plugin) { this.plugin = plugin; }
    public static AutoCloseable register(FotiaTags plugin) {
        var reward = RewardProviders.register(RewardAction.Type.TITLE, new LevelRewards(plugin));
        var ownership = OwnershipProviders.register("title", (player, check) -> {
            if (!check.contexts().isEmpty() || !plugin.getTagManager().tagExists(check.value())) return CompletableFuture.completedFuture(OwnershipResult.UNAVAILABLE);
            return plugin.getDatabaseManager().loadPlayerData(player).thenApply(data -> {
                if (!data.hasTag(check.value())) return OwnershipResult.ABSENT;
                long expiry = data.getTagExpireTime(check.value());
                return expiry == -1 ? OwnershipResult.PERMANENT : OwnershipResult.until(expiry);
            });
        });
        return () -> { try { ownership.close(); } finally { reward.close(); } };
    }
    @Override public boolean idempotent() { return true; }
    @Override public CompletionStage<Result> deliver(RewardDelivery reward) {
        String tag = reward.action().value();
        if (!reward.revoke() && !plugin.getTagManager().tagExists(tag)) return CompletableFuture.completedFuture(Result.INVALID);
        return plugin.getDatabaseManager().applyLevelReward(reward.player(), "basictool:" + reward.source(), tag,
                reward.expiresAt(), reward.version(), reward.revoke()).thenApply(v -> {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> plugin.getTagManager().refreshPlayerData(reward.player()));
            return Result.SUCCESS;
        });
    }
}
