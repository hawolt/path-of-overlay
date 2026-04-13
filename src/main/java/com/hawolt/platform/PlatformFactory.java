package com.hawolt.platform;

import com.hawolt.platform.linux.LinuxPlatform;
import com.hawolt.platform.windows.WindowsPlatform;

public final class PlatformFactory {

    private PlatformFactory() {
    }

    public static Platform create() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new WindowsPlatform();
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            return new LinuxPlatform();
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported operating system: " + System.getProperty("os.name"));
        }
    }
}