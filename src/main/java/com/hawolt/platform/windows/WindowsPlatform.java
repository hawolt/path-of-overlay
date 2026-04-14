package com.hawolt.platform.windows;

import com.hawolt.Main;
import com.hawolt.logger.Logger;
import com.hawolt.platform.Platform;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser.MSG;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;

public class WindowsPlatform implements Platform {

    private static final String GAME_WINDOW_TITLE = "Path of Exile";
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int WS_EX_LAYERED = 0x00080000;
    private static final int WS_EX_TRANSPARENT = 0x00000020;
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;

    private final Object pumpThreadIdLock = new Object();
    private long pumpThreadId = 0;

    @Override
    public boolean isGameProcessRunning() {
        HWND hwnd = WindowsNative.INSTANCE.FindWindowW(null, GAME_WINDOW_TITLE);
        boolean found = hwnd != null;
        Logger.debug("[WindowsPlatform] isGameProcessRunning: FindWindowW('{}') -> {}", GAME_WINDOW_TITLE, found);
        return found;
    }

    @Override
    public String getGameProcessName() {
        return GAME_WINDOW_TITLE;
    }

    @Override
    public Optional<Rectangle> getGameWindowBounds() {
        HWND hwnd = WindowsNative.INSTANCE.FindWindowW(null, GAME_WINDOW_TITLE);
        if (hwnd == null) {
            Logger.debug("[WindowsPlatform] getGameWindowBounds: window not found");
            return Optional.empty();
        }
        com.sun.jna.platform.win32.WinDef.RECT r = new com.sun.jna.platform.win32.WinDef.RECT();
        WindowsNative.INSTANCE.GetWindowRect(hwnd, r);
        Rectangle bounds = new Rectangle(r.left, r.top, r.right - r.left, r.bottom - r.top);
        Logger.debug("[WindowsPlatform] getGameWindowBounds: {}", bounds);
        return Optional.of(bounds);
    }

    @Override
    public void makeClickThrough(Window window) {
        HWND h = hwnd(window);
        long v = style(h);
        WindowsNative.INSTANCE.SetWindowLongPtr(
                h,
                GWL_EXSTYLE,
                new Pointer(v | WS_EX_LAYERED | WS_EX_TRANSPARENT)
        );
        Logger.debug("[WindowsPlatform] makeClickThrough applied");
    }

    @Override
    public void makeInteractable(Window window) {
        HWND h = hwnd(window);
        long v = style(h);
        WindowsNative.INSTANCE.SetWindowLongPtr(
                h,
                GWL_EXSTYLE,
                new Pointer((v | WS_EX_LAYERED) & ~WS_EX_TRANSPARENT & ~WS_EX_NOACTIVATE)
        );
        Logger.debug("[WindowsPlatform] makeInteractable applied");
    }

    private long style(HWND h) {
        return Pointer.nativeValue(WindowsNative.INSTANCE.GetWindowLongPtr(h, GWL_EXSTYLE));
    }

    private static HWND hwnd(Window w) {
        return new HWND(Native.getWindowPointer(w));
    }

    @Override
    public boolean registerHotKey(int id, int vk, int mod) {
        boolean ok = WindowsNative.INSTANCE.RegisterHotKey(null, id, mod, vk);
        return ok;
    }

    @Override
    public void unregisterHotKey(int id) {
        WindowsNative.INSTANCE.UnregisterHotKey(null, id);
    }

    @Override
    public void runHotKeyLoop(Consumer<Integer> handler) {
        synchronized (pumpThreadIdLock) {
            pumpThreadId = Kernel32.INSTANCE.GetCurrentThreadId();
            Logger.info("[WindowsPlatform] runHotKeyLoop: pump thread id={}", pumpThreadId);
        }
        try {
            MSG msg = new MSG();
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) handler.accept(msg.wParam.intValue());
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
            Logger.info("[WindowsPlatform] runHotKeyLoop: GetMessage returned <= 0, loop exiting");
        } finally {
            synchronized (pumpThreadIdLock) {
                Logger.info("[WindowsPlatform] runHotKeyLoop: clearing pump thread id");
                pumpThreadId = 0;
            }
        }
    }

    @Override
    public void stopHotKeyLoop() {
        synchronized (pumpThreadIdLock) {
            if (pumpThreadId != 0) {
                Logger.info("[WindowsPlatform] stopHotKeyLoop: posting WM_QUIT to thread id={}", pumpThreadId);
                WindowsNative.INSTANCE.PostThreadMessageW((int) pumpThreadId, WM_QUIT, null, null);
            } else {
                Logger.warn("[WindowsPlatform] stopHotKeyLoop: pumpThreadId is 0 - loop may not be running");
            }
        }
    }

    @Override
    public boolean supportsTray() {
        return SystemTray.isSupported();
    }

    @Override
    public Runnable installTray(Runnable onOpenSettings, Runnable onExit) {
        TrayIcon icon = new TrayIcon(trayImage(), "exile-overlay");
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> {
            Logger.info("[WindowsPlatform] Tray icon double-clicked");
            onOpenSettings.run();
        });
        PopupMenu menu = new PopupMenu();
        MenuItem s = new MenuItem("Settings");
        s.addActionListener(e -> {
            Logger.info("[WindowsPlatform] Tray menu: Settings clicked");
            onOpenSettings.run();
        });
        MenuItem l = new MenuItem("Open Log Dir");
        l.addActionListener(e -> {
            Logger.info("[WindowsPlatform] Tray menu: Open Log Dir clicked");
            openLogDir();
        });
        MenuItem x = new MenuItem("Exit");
        x.addActionListener(e -> {
            Logger.info("[WindowsPlatform] Tray menu: Exit clicked");
            onExit.run();
        });
        menu.add(s);
        menu.add(l);
        menu.addSeparator();
        menu.add(x);
        icon.setPopupMenu(menu);
        try {
            SystemTray.getSystemTray().add(icon);
            Logger.info("[WindowsPlatform] Tray icon installed successfully");
        } catch (AWTException e) {
            Logger.error("[WindowsPlatform] Tray icon install failed: {}", e.getMessage());
        }
        return () -> {
            Logger.info("[WindowsPlatform] Tray icon removed");
            SystemTray.getSystemTray().remove(icon);
        };
    }

    private static void openLogDir() {
        java.io.File logDir = new java.io.File(System.getProperty("user.home"), "poe-overlay/logs");
        Logger.info("[WindowsPlatform] Opening log dir: {}", logDir.getAbsolutePath());
        if (!logDir.exists()) {
            Logger.warn("[WindowsPlatform] Log dir does not exist: {}", logDir.getAbsolutePath());
        }
        try {
            Desktop.getDesktop().open(logDir.exists() ? logDir : logDir.getParentFile());
        } catch (Exception e) {
            Logger.error("[WindowsPlatform] Failed to open log dir: {}", e.getMessage());
        }
    }

    private static Image trayImage() {
        try {
            URL url = Main.class.getResource("/logo.png");
            if (url != null) {
                return ImageIO.read(url);
            }
        } catch (IOException e) {
            Logger.error("[WindowsPlatform] Failed to load tray icon: {}", e.getMessage());
        }
        int sz = 16;
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(101, 67, 33));
        g.fillOval(1, 1, sz - 2, sz - 2);
        g.dispose();
        return img;
    }
}