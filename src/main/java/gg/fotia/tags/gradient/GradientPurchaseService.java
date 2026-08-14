package gg.fotia.tags.gradient;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.hook.PaymentManager;
import gg.fotia.tags.util.PlayerOperationLock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public class GradientPurchaseService {

    private final FotiaTags plugin;
    private final PlayerOperationLock operations = new PlayerOperationLock();

    public GradientPurchaseService(FotiaTags plugin) {
        this.plugin = plugin;
    }

    public void purchase(Player player, GradientEffect effect, Consumer<GradientPurchaseResult> completion) {
        UUID uuid = player.getUniqueId();
        if (!operations.tryAcquire(uuid)) {
            plugin.getMessageManager().send(player, "gradient-purchase-processing");
            return;
        }

        GradientPurchase purchase = effect.purchase();
        long expireTime;
        try {
            expireTime = plugin.getGradientManager().calculateExpireTime(purchase.durationMillis());
        } catch (IllegalArgumentException exception) {
            operations.release(uuid);
            plugin.getMessageManager().send(player, "gradient-operation-failed");
            return;
        }
        PaymentManager.PaymentSnapshot snapshot = plugin.getPaymentManager()
                .createSnapshot(purchase.provider(), purchase.price());
        GradientPaymentOperations payment = new GradientPaymentOperations() {
            @Override
            public boolean available() {
                return plugin.getPaymentManager().isAvailable(snapshot);
            }

            @Override
            public boolean hasEnough() {
                return plugin.getPaymentManager().hasEnough(player, snapshot);
            }

            @Override
            public boolean withdraw() {
                return plugin.getPaymentManager().withdraw(player, snapshot);
            }

            @Override
            public boolean refund() {
                return plugin.getPaymentManager().refund(player, snapshot.provider(), snapshot.amount());
            }
        };

        GradientPurchaseTransaction.execute(
                        plugin.getGradientManager().owns(uuid, effect.id()),
                        payment,
                        () -> plugin.getDatabaseManager().grantGradientEffect(uuid, effect.id(), expireTime),
                        task -> Bukkit.getScheduler().runTask(plugin, task)
                )
                .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        GradientPurchaseResult finalResult = throwable == null
                                ? result
                                : GradientPurchaseResult.PERSIST_FAILED_REFUNDED;
                        if (finalResult == GradientPurchaseResult.SUCCESS) {
                            plugin.getGradientManager().applyPersistedEffect(uuid, effect.id(), expireTime);
                        }
                        sendResult(player, effect, snapshot, finalResult);
                        if (completion != null) {
                            completion.accept(finalResult);
                        }
                    } finally {
                        operations.release(uuid);
                    }
                }));
    }

    private void sendResult(Player player, GradientEffect effect, PaymentManager.PaymentSnapshot snapshot,
                            GradientPurchaseResult result) {
        String key = switch (result) {
            case SUCCESS -> "gradient-purchase-success";
            case ALREADY_OWNED -> "gradient-already-owned";
            case PAYMENT_UNAVAILABLE -> "gradient-payment-unavailable";
            case INSUFFICIENT_FUNDS -> "gradient-insufficient-funds";
            case WITHDRAW_FAILED -> "gradient-withdraw-failed";
            case PERSIST_FAILED_REFUNDED -> "gradient-save-failed-refunded";
            case PERSIST_FAILED_REFUND_FAILED -> "gradient-save-failed-refund-failed";
        };
        plugin.getMessageManager().send(player, key, MessageManager.of(
                "effect", effect.displayName(),
                "price", plugin.getPaymentManager().getPriceText(snapshot)
        ));
    }
}
