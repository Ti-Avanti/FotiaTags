package gg.fotia.tags.gradient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class GradientPurchaseTransaction {

    private GradientPurchaseTransaction() {
    }

    public static CompletableFuture<GradientPurchaseResult> execute(
            boolean alreadyOwned,
            GradientPaymentOperations payment,
            Supplier<CompletableFuture<Void>> persist
    ) {
        return execute(alreadyOwned, payment, persist, Runnable::run);
    }

    public static CompletableFuture<GradientPurchaseResult> execute(
            boolean alreadyOwned,
            GradientPaymentOperations payment,
            Supplier<CompletableFuture<Void>> persist,
            Executor completionExecutor
    ) {
        if (alreadyOwned) {
            return CompletableFuture.completedFuture(GradientPurchaseResult.ALREADY_OWNED);
        }
        if (!payment.available()) {
            return CompletableFuture.completedFuture(GradientPurchaseResult.PAYMENT_UNAVAILABLE);
        }
        if (!payment.hasEnough()) {
            return CompletableFuture.completedFuture(GradientPurchaseResult.INSUFFICIENT_FUNDS);
        }
        if (!payment.withdraw()) {
            return CompletableFuture.completedFuture(GradientPurchaseResult.WITHDRAW_FAILED);
        }

        CompletableFuture<Void> write;
        try {
            write = persist.get();
        } catch (RuntimeException e) {
            return CompletableFuture.supplyAsync(() -> refundFailure(payment), completionExecutor);
        }

        return write.handleAsync((ignored, throwable) -> throwable == null
                ? GradientPurchaseResult.SUCCESS
                : refundFailure(payment), completionExecutor);
    }

    private static GradientPurchaseResult refundFailure(GradientPaymentOperations payment) {
        return payment.refund()
                ? GradientPurchaseResult.PERSIST_FAILED_REFUNDED
                : GradientPurchaseResult.PERSIST_FAILED_REFUND_FAILED;
    }
}
