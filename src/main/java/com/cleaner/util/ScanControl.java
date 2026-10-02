package com.cleaner.util;

/** Hook called by long-running work so it can pause or be cancelled. */
@FunctionalInterface
public interface ScanControl {
    ScanControl NONE = () -> { };

    /** Blocks while paused; throws {@link ScanCancelledException} when cancelled. */
    void checkpoint();
}
