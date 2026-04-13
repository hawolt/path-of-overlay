package com.hawolt.hotkey;

import com.hawolt.Config;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public final class HotkeyLabel {

    public static final int VK_MOUSE_LEFT = 0x1001;
    public static final int VK_MOUSE_RIGHT = 0x1002;
    public static final int VK_MOUSE_MIDDLE = 0x1003;
    public static final int VK_MOUSE_BUTTON4 = 0x1004;
    public static final int VK_MOUSE_BUTTON5 = 0x1005;

    public static final int VK_SCROLL_UP = 0x0E01;
    public static final int VK_SCROLL_DOWN = 0x0E02;

    private HotkeyLabel() {
    }

    public static int encode(int vk, int awtMods) {
        return ((awtMods & 0xFFFF) << 16) | (vk & 0xFFFF);
    }

    public static int decodeVk(int encoded) {
        return encoded & 0xFFFF;
    }

    public static int decodeMods(int encoded) {
        return (encoded >> 16) & 0xFFFF;
    }

    public static String labelOf(int encoded) {
        int vk = decodeVk(encoded);
        int mods = decodeMods(encoded);
        if (vk == 0) return "";
        StringBuilder sb = new StringBuilder();
        if ((mods & InputEvent.CTRL_DOWN_MASK) != 0) sb.append("Ctrl+");
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0) sb.append("Shift+");
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0) sb.append("Alt+");
        sb.append(vkName(vk));
        return sb.toString();
    }

    public static String vkName(int vk) {
        if (vk == VK_MOUSE_LEFT) return "Mouse L";
        if (vk == VK_MOUSE_RIGHT) return "Mouse R";
        if (vk == VK_MOUSE_MIDDLE) return "Mouse M";
        if (vk == VK_MOUSE_BUTTON4) return "Mouse 4";
        if (vk == VK_MOUSE_BUTTON5) return "Mouse 5";
        if (vk == VK_SCROLL_UP) return "Scroll Up";
        if (vk == VK_SCROLL_DOWN) return "Scroll Down";
        String name = KeyEvent.getKeyText(vk);
        return name.startsWith("Unknown") ? "0x" + Integer.toHexString(vk).toUpperCase() : name;
    }

    public static int mouseButtonVk(int awtButton) {
        if (awtButton == MouseEvent.BUTTON1) return VK_MOUSE_LEFT;
        if (awtButton == MouseEvent.BUTTON2) return VK_MOUSE_MIDDLE;
        if (awtButton == MouseEvent.BUTTON3) return VK_MOUSE_RIGHT;
        if (awtButton == 4) return VK_MOUSE_BUTTON4;
        if (awtButton == 5) return VK_MOUSE_BUTTON5;
        return 0;
    }

    public static int[] toWindowsHotkey(int encoded) {
        int vk = decodeVk(encoded);
        int mods = decodeMods(encoded);
        if (vk >= 0x1000 || vk == VK_SCROLL_UP || vk == VK_SCROLL_DOWN) return null;
        int fsModifiers = Config.MOD_NOREPEAT;
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0) fsModifiers |= Config.MOD_ALT;
        if ((mods & InputEvent.CTRL_DOWN_MASK) != 0) fsModifiers |= Config.MOD_CONTROL;
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0) fsModifiers |= Config.MOD_SHIFT;
        return new int[]{vk, fsModifiers};
    }
}