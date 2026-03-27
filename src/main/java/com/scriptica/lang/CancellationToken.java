package com.scriptica.lang;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CancellationToken parent;

    public CancellationToken() {
        this(null);
    }

    public CancellationToken(CancellationToken parent) {
        this.parent = parent;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        if (cancelled.get()) return true;
        return parent != null && parent.isCancelled();
    }

    public void checkCancelled() {
        if (isCancelled()) {
            throw new ScripticaRuntimeException("Cancelled");
        }
    }
}
