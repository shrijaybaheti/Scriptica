package com.scriptica.client;

import com.scriptica.lang.CancellationToken;
import com.scriptica.lang.Interpreter;
import com.scriptica.lang.ScripticaCallable;
import com.scriptica.lang.ScripticaEngine;
import com.scriptica.lang.ScripticaRuntimeException;
import com.scriptica.lang.Value;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public final class ScriptRunner {
    private static final ScriptRunner INSTANCE = new ScriptRunner();

    public static ScriptRunner instance() {
        return INSTANCE;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Scriptica-ScriptRunner");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService eventExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Scriptica-Event");
        t.setDaemon(true);
        return t;
    });

    private final ScripticaEngine engine = new ScripticaEngine();
    private final MinecraftScripticaHost host = new MinecraftScripticaHost();

    private volatile CancellationToken token;
    private volatile Future<?> task;

    private final AtomicLong nextEventId = new AtomicLong(1);

    private enum EventType {
        TICK,
        CHAT,
        CUSTOM
    }

    private record EventReg(EventType type, String name, ScripticaCallable fn, CancellationToken token) {}

    private final Map<Long, EventReg> events = new ConcurrentHashMap<>();

    private ScriptRunner() {}

    public boolean isRunning() {
        Future<?> t = task;
        return t != null && !t.isDone();
    }

    public void stop() {
        CancellationToken t = token;
        if (t != null) t.cancel();
        events.clear();
        host.releaseAllKeys();
        ScripticaLog.info("Stop requested");
    }

    public void runAsync(String source) {
        stop();
        CancellationToken next = new CancellationToken();
        token = next;

        task = executor.submit(() -> {
            ScripticaLog.info("Script start");
            try {
                engine.run(source, host, next);
                ScripticaLog.info("Script finished");
            } catch (ScripticaRuntimeException e) {
                ScripticaLog.error("Runtime error: " + e.getMessage());
            } catch (RuntimeException e) {
                ScripticaLog.error("Error: " + e.getMessage());
            } finally {
                events.clear();
                host.releaseAllKeys();
            }
        });
    }

    public double registerTick(ScripticaCallable fn, CancellationToken token) {
        return register(EventType.TICK, null, fn, token);
    }

    public double registerChat(ScripticaCallable fn, CancellationToken token) {
        return register(EventType.CHAT, null, fn, token);
    }

    public double registerCustom(String name, ScripticaCallable fn, CancellationToken token) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return 0.0;
        return register(EventType.CUSTOM, n, fn, token);
    }

    private double register(EventType type, String name, ScripticaCallable fn, CancellationToken token) {
        if (fn == null) return 0.0;
        long id = nextEventId.getAndIncrement();
        events.put(id, new EventReg(type, name, fn, token));
        return (double) id;
    }

    public void unregisterEvent(double handle) {
        long id = (long) Math.rint(handle);
        events.remove(id);
    }

    public void dispatchTick() {
        if (events.isEmpty()) return;
        for (EventReg reg : events.values()) {
            if (reg.type != EventType.TICK) continue;
            scheduleCallback(reg, List.of());
        }
    }

    public void dispatchChat(String message) {
        if (events.isEmpty()) return;
        Value msg = Value.of(message == null ? "" : message);
        for (EventReg reg : events.values()) {
            if (reg.type != EventType.CHAT) continue;
            scheduleCallback(reg, List.of(msg));
        }
    }

    public void dispatchCustom(String name, Value payload) {
        if (events.isEmpty()) return;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return;
        Value p = payload == null ? Value.NULL : payload;
        for (EventReg reg : events.values()) {
            if (reg.type != EventType.CUSTOM) continue;
            if (reg.name == null || !reg.name.equals(n)) continue;
            scheduleCallback(reg, List.of(p));
        }
    }

    private void scheduleCallback(EventReg reg, List<Value> args) {
        if (reg.token == null || reg.token.isCancelled()) return;
        eventExecutor.submit(() -> {
            if (reg.token.isCancelled()) return;
            try {
                Interpreter interpreter = new Interpreter(host, reg.token);
                int minA = reg.fn.minArity();
                int maxA = reg.fn.maxArity();
                if (args.size() < minA || args.size() > maxA) return;
                reg.fn.call(interpreter, args);
            } catch (ScripticaRuntimeException e) {
                if (!"Cancelled".equals(e.getMessage())) {
                    ScripticaLog.error("Event error: " + e.getMessage());
                }
            } catch (RuntimeException e) {
                ScripticaLog.error("Event error: " + e.getMessage());
            }
        });
    }
}
