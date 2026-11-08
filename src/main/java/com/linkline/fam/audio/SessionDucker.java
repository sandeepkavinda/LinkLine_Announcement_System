package com.linkline.fam.audio;

/**
 * Temporarily lowers the volume of other applications while an announcement plays.
 * Implementations work on individual audio sessions, never on the master volume.
 */
public interface SessionDucker {

    /** An active ducking operation. {@link #restore()} must always be called. */
    interface Handle {
        /** Puts every captured session back to its original volume. Safe to call more than once. */
        void restore();

        /** Number of sessions that were lowered. */
        int sessionCount();
    }

    /** True when this implementation can really control Windows audio sessions. */
    boolean isSupported();

    String description();

    /**
     * Captures the volume of every other audio session, lowers each to {@code factor} of its original
     * value (0.5 = 50%) and keeps doing so for sessions that start while the handle is open.
     */
    Handle begin(double factor) throws Exception;

    /** Restores sessions left lowered by a previous FAM run that did not shut down cleanly. */
    void recoverFromJournal();
}
