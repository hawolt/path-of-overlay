package com.hawolt.platform;

import java.awt.*;
import java.util.Optional;
import java.util.function.Consumer;

public interface Platform {

    boolean isGameProcessRunning();

    String getGameProcessName();

    Optional<Rectangle> getGameWindowBounds();

    void makeClickThrough(Window window);

    void makeInteractable(Window window);

    boolean registerHotKey(int id, int vk, int mod);

    void unregisterHotKey(int id);

    void runHotKeyLoop(Consumer<Integer> handler);

    void stopHotKeyLoop();

    boolean supportsTray();

    Runnable installTray(Runnable onOpenSettings, Runnable onExit);
}