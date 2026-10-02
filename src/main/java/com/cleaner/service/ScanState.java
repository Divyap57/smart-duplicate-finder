package com.cleaner.service;

import com.cleaner.util.ScanCancelledException;
import com.cleaner.util.ScanControl;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live state of the current scan. Written by the single scanning thread, read by progress requests,
 * hence plain volatile fields. Also acts as the pause/cancel switch.
 */
public final class ScanState implements ScanControl {
    public final long scanId;
    public final String folder;
    public final long startMs = System.currentTimeMillis();

    public volatile String status = "RUNNING"; // RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    public volatile String phase = "DISCOVERING"; // DISCOVERING, HASHING, SAVING, DONE
    public volatile String currentFile = "";
    public volatile String currentFolder = "";
    public volatile String message = "";
    public volatile long endMs;
    public volatile long filesScanned;
    public volatile long foldersScanned;
    public volatile long totalBytes;
    public volatile long candidates;
    public volatile long hashed;
    public volatile long duplicateFiles;
    public volatile long duplicateGroups;
    public volatile long recoverableBytes;
    public volatile long skipped;

    private volatile boolean paused;
    private volatile boolean cancelled;

    public ScanState(long scanId, String folder) {
        this.scanId = scanId;
        this.folder = folder;
    }

    @Override
    public void checkpoint() {
        while (paused && !cancelled) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScanCancelledException();
            }
        }
        if (cancelled) throw new ScanCancelledException();
    }

    public boolean isActive() {
        return "RUNNING".equals(status) || "PAUSED".equals(status);
    }

    public void pause() {
        if ("RUNNING".equals(status)) {
            paused = true;
            status = "PAUSED";
        }
    }

    public void resume() {
        if ("PAUSED".equals(status)) {
            paused = false;
            status = "RUNNING";
        }
    }

    public void cancel() {
        cancelled = true;
        paused = false;
    }

    public Map<String, Object> snapshot() {
        long end = isActive() ? System.currentTimeMillis() : endMs;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scanId", scanId);
        m.put("folder", folder);
        m.put("status", status);
        m.put("phase", phase);
        m.put("filesScanned", filesScanned);
        m.put("foldersScanned", foldersScanned);
        m.put("totalBytes", totalBytes);
        m.put("candidates", candidates);
        m.put("hashed", hashed);
        // Unknown until discovery has finished.
        m.put("remaining", "DISCOVERING".equals(phase) ? null : Math.max(0, candidates - hashed));
        m.put("percent", "DISCOVERING".equals(phase) ? -1
                : "DONE".equals(phase) ? 100
                : candidates == 0 ? 100 : (int) Math.min(100, hashed * 100 / candidates));
        m.put("currentFile", currentFile);
        m.put("currentFolder", currentFolder);
        m.put("duplicateFiles", duplicateFiles);
        m.put("duplicateGroups", duplicateGroups);
        m.put("recoverableBytes", recoverableBytes);
        m.put("skipped", skipped);
        m.put("elapsedMs", Math.max(0, end - startMs));
        m.put("message", message);
        return m;
    }
}
