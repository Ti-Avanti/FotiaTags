package gg.fotia.tags.gradient;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradientPurchaseTransactionTest {

    @Test
    void alreadyOwnedDoesNotWithdrawOrPersist() {
        FakePayment payment = new FakePayment();
        AtomicInteger writes = new AtomicInteger();

        GradientPurchaseResult result = GradientPurchaseTransaction.execute(
                true,
                payment,
                () -> {
                    writes.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
        ).join();

        assertEquals(GradientPurchaseResult.ALREADY_OWNED, result);
        assertEquals(0, payment.withdrawals);
        assertEquals(0, writes.get());
    }

    @Test
    void insufficientBalanceDoesNotWithdrawOrPersist() {
        FakePayment payment = new FakePayment();
        payment.enough = false;
        AtomicInteger writes = new AtomicInteger();

        GradientPurchaseResult result = GradientPurchaseTransaction.execute(
                false,
                payment,
                () -> {
                    writes.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
        ).join();

        assertEquals(GradientPurchaseResult.INSUFFICIENT_FUNDS, result);
        assertEquals(0, payment.withdrawals);
        assertEquals(0, writes.get());
    }

    @Test
    void successfulPurchaseWithdrawsAndPersistsOnce() {
        FakePayment payment = new FakePayment();
        AtomicInteger writes = new AtomicInteger();

        GradientPurchaseResult result = GradientPurchaseTransaction.execute(
                false,
                payment,
                () -> {
                    writes.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
        ).join();

        assertEquals(GradientPurchaseResult.SUCCESS, result);
        assertEquals(1, payment.withdrawals);
        assertEquals(1, writes.get());
        assertEquals(0, payment.refunds);
    }

    @Test
    void failedPersistenceRefundsTheExactWithdrawal() {
        FakePayment payment = new FakePayment();
        AtomicInteger refundDispatches = new AtomicInteger();

        GradientPurchaseResult result = GradientPurchaseTransaction.execute(
                false,
                payment,
                () -> CompletableFuture.failedFuture(new IllegalStateException("db unavailable")),
                task -> {
                    refundDispatches.incrementAndGet();
                    task.run();
                }
        ).join();

        assertEquals(GradientPurchaseResult.PERSIST_FAILED_REFUNDED, result);
        assertEquals(1, payment.withdrawals);
        assertEquals(1, payment.refunds);
        assertEquals(1, refundDispatches.get());
    }

    private static final class FakePayment implements GradientPaymentOperations {
        private boolean available = true;
        private boolean enough = true;
        private boolean withdraw = true;
        private boolean refund = true;
        private int withdrawals;
        private int refunds;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean hasEnough() {
            return enough;
        }

        @Override
        public boolean withdraw() {
            withdrawals++;
            return withdraw;
        }

        @Override
        public boolean refund() {
            refunds++;
            return refund;
        }
    }
}
