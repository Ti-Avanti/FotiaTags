package gg.fotia.tags.util;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AsyncCommit {

    private AsyncCommit() {
    }

    public static CompletableFuture<Void> after(
            CompletableFuture<Void> persistence,
            Executor commitExecutor,
            Runnable mutation
    ) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(commitExecutor, "commitExecutor");
        Objects.requireNonNull(mutation, "mutation");
        return persistence.thenRunAsync(mutation, commitExecutor);
    }
}
