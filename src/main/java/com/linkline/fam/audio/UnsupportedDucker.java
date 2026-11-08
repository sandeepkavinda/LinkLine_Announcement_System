package com.linkline.fam.audio;

/**
 * Used on systems without the Windows Core Audio API (for example when developing on Linux or macOS).
 * It does nothing and reports itself as unsupported, so the UI can say so honestly.
 */
public final class UnsupportedDucker implements SessionDucker {

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public String description() {
        return "Not available on this operating system (Windows Core Audio required)";
    }

    @Override
    public Handle begin(double factor) {
        return new Handle() {
            @Override
            public void restore() {
            }

            @Override
            public int sessionCount() {
                return 0;
            }
        };
    }

    @Override
    public void recoverFromJournal() {
    }
}
