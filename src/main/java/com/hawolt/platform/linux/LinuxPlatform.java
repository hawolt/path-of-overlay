package com.hawolt.platform.linux;

import com.hawolt.platform.Platform;

import java.awt.*;
import java.util.Optional;
import java.util.function.Consumer;

public class LinuxPlatform implements Platform {

    private final Object pumpThreadLock = new Object();
    private Thread pumpThread;

    @Override
    public boolean isGameProcessRunning() {
        throw new UnsupportedOperationException("isGameProcessRunning not implemented on Linux");
    }

    @Override
    public String getGameProcessName() {
        throw new UnsupportedOperationException("getGameProcessName not implemented on Linux");
    }

    @Override
    public Optional<Rectangle> getGameWindowBounds() {
        throw new UnsupportedOperationException("getGameWindowBounds not implemented on Linux");
    }

    @Override
    public void makeClickThrough(Window w) {
        throw new UnsupportedOperationException("makeClickThrough not implemented on Linux");
    }

    @Override
    public void makeInteractable(Window w) {
        throw new UnsupportedOperationException("makeInteractable not implemented on Linux");
    }

    @Override
    public boolean registerHotKey(int id, int vk, int mod) {
        throw new UnsupportedOperationException("registerHotKey not implemented on Linux");
    }

    @Override
    public void unregisterHotKey(int id) {
        throw new UnsupportedOperationException("unregisterHotKey not implemented on Linux");
    }

    @Override
    public void runHotKeyLoop(Consumer<Integer> handler) {
        synchronized (pumpThreadLock) {
            pumpThread = Thread.currentThread();
        }
        try {
            while (!Thread.currentThread().isInterrupted()) Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stopHotKeyLoop() {
        synchronized (pumpThreadLock) {
            if (pumpThread != null) pumpThread.interrupt();
        }
    }

    @Override
    public boolean supportsTray() {
        return false;
    }

    @Override
    public Runnable installTray(Runnable a, Runnable b) {
        throw new UnsupportedOperationException("installTray not supported on Linux");
    }
}