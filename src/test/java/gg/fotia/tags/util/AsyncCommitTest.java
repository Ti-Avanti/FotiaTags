package gg.fotia.tags.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncCommitTest {

    @Test
    void appliesMutationOnlyAfterPersistenceSucceeds() {
        CompletableFuture<Void> persistence = new CompletableFuture<>();
        AtomicInteger mutations = new AtomicInteger();

        CompletableFuture<Void> committed = AsyncCommit.after(
                persistence,
                Runnable::run,
                mutations::incrementAndGet
        );

        assertEquals(0, mutations.get());

        persistence.complete(null);
        committed.join();

        assertEquals(1, mutations.get());
    }

    @Test
    void leavesStateUntouchedWhenPersistenceFails() {
        CompletableFuture<Void> persistence = new CompletableFuture<>();
        AtomicInteger mutations = new AtomicInteger();

        CompletableFuture<Void> committed = AsyncCommit.after(
                persistence,
                Runnable::run,
                mutations::incrementAndGet
        );

        persistence.completeExceptionally(new IllegalStateException("database unavailable"));

        assertThrows(CompletionException.class, committed::join);
        assertEquals(0, mutations.get());
    }
}
