package com.hawolt.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface WindowsNative extends StdCallLibrary {

    WindowsNative INSTANCE = Native.load("user32", WindowsNative.class, W32APIOptions.DEFAULT_OPTIONS);

    HWND FindWindowW(String lpClassName, String lpWindowName);

    boolean GetWindowRect(HWND hwnd, WinDef.RECT rect);

    Pointer GetWindowLongPtr(HWND hwnd, int nIndex);

    Pointer SetWindowLongPtr(HWND hwnd, int nIndex, Pointer dwNewLong);

    boolean RegisterHotKey(HWND hWnd, int id, int fsModifiers, int vk);

    boolean UnregisterHotKey(HWND hWnd, int id);

    boolean PostThreadMessageW(int idThread, int msg, Pointer wParam, Pointer lParam);

}