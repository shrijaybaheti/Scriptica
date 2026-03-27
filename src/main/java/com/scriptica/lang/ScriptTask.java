package com.scriptica.lang;

public final class ScriptTask {
    public final CancellationToken token;
    public volatile Value result = Value.NULL;
    public volatile ScripticaRuntimeException error = null;
    public volatile Thread thread;
    public volatile boolean done = false;

    public ScriptTask(CancellationToken token) {
        this.token = token;
    }
}
