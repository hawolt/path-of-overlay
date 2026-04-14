package com.hawolt.overlay;

import com.hawolt.hotkey.HotkeyLabel;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javax.swing.JPanel;

class SettingsHotkeyHandler {

    private final int[] fieldEncoded;
    private final Runnable onSuspendHotkeys;
    private final Runnable onResumeHotkeys;
    private final Runnable onNeedFocus;
    private final Runnable onRepaint;
    private final IntConsumer onCommit;

    private int listeningIndex = -1;

    SettingsHotkeyHandler(
            int[] fieldEncoded,
            Runnable onSuspendHotkeys,
            Runnable onResumeHotkeys,
            Runnable onNeedFocus,
            Runnable onRepaint,
            IntConsumer onCommit
    ) {
        this.fieldEncoded = fieldEncoded;
        this.onSuspendHotkeys = onSuspendHotkeys;
        this.onResumeHotkeys = onResumeHotkeys;
        this.onNeedFocus = onNeedFocus;
        this.onRepaint = onRepaint;
        this.onCommit = onCommit;
    }

    void install(JPanel panel) {
        panel.addMouseListener(buildMouseListener());
        panel.addMouseWheelListener(buildMouseWheelListener());
        panel.addKeyListener(buildKeyListener());
    }

    boolean isListening() {
        return listeningIndex >= 0;
    }

    int listeningIndex() {
        return listeningIndex;
    }

    void startListening(int index) {
        listeningIndex = index;
        onSuspendHotkeys.run();
        onRepaint.run();
        onNeedFocus.run();
    }

    void stopListening() {
        if (listeningIndex < 0) return;
        listeningIndex = -1;
        onResumeHotkeys.run();
        onRepaint.run();
    }

    private void commitHotkey(int encoded) {
        if (listeningIndex < 0) return;
        for (int i = 0; i < fieldEncoded.length; i++) {
            if (i != listeningIndex && fieldEncoded[i] == encoded) {
                fieldEncoded[i] = 0;
            }
        }
        fieldEncoded[listeningIndex] = encoded;
        int committed = listeningIndex;
        stopListening();
        onCommit.accept(committed);
    }

    private MouseAdapter buildMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
                if (listeningIndex < 0) return;
                int virtualKey = HotkeyLabel.mouseButtonVk(mouseEvent.getButton());
                if (virtualKey != 0) {
                    int modifiers = mouseEvent.getModifiersEx() & (
                            InputEvent.SHIFT_DOWN_MASK
                                    | InputEvent.CTRL_DOWN_MASK
                                    | InputEvent.ALT_DOWN_MASK
                    );
                    commitHotkey(HotkeyLabel.encode(virtualKey, modifiers));
                }
            }
        };
    }

    private MouseWheelListener buildMouseWheelListener() {
        return mouseWheelEvent -> {
            if (listeningIndex < 0) return;
            int virtualKey = mouseWheelEvent.getWheelRotation() < 0
                    ? HotkeyLabel.VK_SCROLL_UP
                    : HotkeyLabel.VK_SCROLL_DOWN;
            commitHotkey(HotkeyLabel.encode(virtualKey, 0));
        };
    }

    private KeyAdapter buildKeyListener() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (listeningIndex < 0) return;
                int virtualKey = keyEvent.getKeyCode();
                if (virtualKey == KeyEvent.VK_ESCAPE) {
                    stopListening();
                    return;
                }
                if (virtualKey == KeyEvent.VK_SHIFT
                        || virtualKey == KeyEvent.VK_CONTROL
                        || virtualKey == KeyEvent.VK_ALT) return;
                int modifiers = keyEvent.getModifiersEx() & (
                        InputEvent.SHIFT_DOWN_MASK
                                | InputEvent.CTRL_DOWN_MASK
                                | InputEvent.ALT_DOWN_MASK
                );
                commitHotkey(HotkeyLabel.encode(virtualKey, modifiers));
            }
        };
    }
}