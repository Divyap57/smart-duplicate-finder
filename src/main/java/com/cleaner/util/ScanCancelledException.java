package com.cleaner.util;

public class ScanCancelledException extends RuntimeException {
    public ScanCancelledException() {
        super("Scan cancelled");
    }
}
