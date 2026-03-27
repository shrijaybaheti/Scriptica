package com.scriptica.client;

import net.minecraft.client.MinecraftClient;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class MinecraftThread {
    private MinecraftThread() {}

    public static void runSync(Runnable runnable) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (client.isOnThread()) {
            runnable.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        client.execute(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static <T> T callSync(Supplier<T> supplier, T fallback) {
        Objects.requireNonNull(supplier, "supplier");
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return fallback;
        if (client.isOnThread()) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        AtomicReference<T> ref = new AtomicReference<>(fallback);
        CountDownLatch latch = new CountDownLatch(1);
        client.execute(() -> {
            try {
                ref.set(supplier.get());
            } catch (RuntimeException ignored) {
                // keep fallback
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get();
    }
}
