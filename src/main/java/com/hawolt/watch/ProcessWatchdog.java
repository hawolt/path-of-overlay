package com.hawolt.watch;

import com.hawolt.logger.Logger;
import com.hawolt.platform.Platform;

import java.util.function.Consumer;

public class ProcessWatchdog implements Runnable {

    private static final long POLL_INTERVAL_MS = 2000;

    private final Consumer<Boolean> onStateChanged;
    private final Object runningLock = new Object();
    private final Platform platform;
    private boolean running = false;

    public ProcessWatchdog(Platform platform, Consumer<Boolean> onStateChanged) {
        this.platform = platform;
        this.onStateChanged = onStateChanged;
    }

    public boolean isRunning() {
        synchronized (runningLock) {
            return running;
        }
    }

    @Override
    public void run() {
        boolean initial = platform.isGameProcessRunning();
        synchronized (runningLock) {
            running = initial;
        }
        onStateChanged.accept(initial);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
                boolean current = platform.isGameProcessRunning();
                boolean changed;
                synchronized (runningLock) {
                    changed = current != running;
                    if (changed) running = current;
                }
                if (changed) {
                    onStateChanged.accept(current);
                    Logger.info(
                            "[Watchdog] {} {}",
                            platform.getGameProcessName(),
                            current ? "started" : "stopped"
                    );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}