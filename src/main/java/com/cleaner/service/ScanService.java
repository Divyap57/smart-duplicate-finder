package com.cleaner.service;

import com.cleaner.dao.ScanDao;
import com.cleaner.model.AppSettings;
import com.cleaner.model.FileRecord;
import com.cleaner.model.Scan;
import com.cleaner.util.ApiException;
import com.cleaner.util.ScanCancelledException;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs scans on a background thread so the web server never blocks. One scan at a time. */
public final class ScanService {
    private static final Logger LOG = Logger.getLogger(ScanService.class.getName());
    private static final ScanService INSTANCE = new ScanService();

    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scan-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile ScanState state;

    private ScanService() { }

    public static ScanService get() {
        return INSTANCE;
    }

    /** Validates the folder (never trusts the browser) and starts a scan. */
    public synchronized ScanState start(String folder, String user, String ip) throws Exception {
        if (state != null && state.isActive()) throw new ApiException(409, "A scan is already running.");
        Path root = validateFolder(folder);
        AppSettings settings = SettingsService.get();
        long id = ScanDao.create(root.toString(), user);
        ScanState st = new ScanState(id, root.toString());
        state = st;
        ActivityService.log(user, "SCAN_STARTED", "Scan #" + id + " started on " + root, ip);
        pool.submit(() -> run(st, root, settings, user, ip));
        return st;
    }

    public ScanState current() {
        return state;
    }

    public void pause() {
        ScanState s = requireActive();
        s.pause();
    }

    public void resume() {
        requireActive().resume();
    }

    public void cancel() {
        requireActive().cancel();
    }

    private ScanState requireActive() {
        ScanState s = state;
        if (s == null || !s.isActive()) throw new ApiException(409, "No scan is running.");
        return s;
    }

    public static Path validateFolder(String folder) {
        if (folder == null || folder.isBlank()) throw new ApiException(400, "Please choose a folder to scan.");
        Path p;
        try {
            p = Paths.get(folder.trim()).normalize();
        } catch (InvalidPathException e) {
            throw new ApiException(400, "That folder path is not valid.");
        }
        if (!p.isAbsolute()) throw new ApiException(400, "Please enter a full folder path, for example C:\\Users\\you\\Documents.");
        if (!java.nio.file.Files.isDirectory(p) || !java.nio.file.Files.isReadable(p)) {
            throw new ApiException(400, "Unable to access this folder.");
        }
        return p;
    }

    private void run(ScanState st, Path root, AppSettings cfg, String user, String ip) {
        Scan row = new Scan();
        row.id = st.scanId;
        try {
            Set<Path> excluded = new HashSet<>();
            for (String e : cfg.excludedDirs) excluded.add(Paths.get(e).toAbsolutePath().normalize());
            excluded.add(Paths.get(cfg.quarantineDir).toAbsolutePath().normalize()); // never scan the quarantine itself
            FileScanner.Options opt = new FileScanner.Options(excluded, cfg.minSize, cfg.maxSize,
                    cfg.scanHidden, cfg.scanSystem, cfg.ignoreSystemDirs);

            FileScanner.Result found = FileScanner.scan(root, opt, st, new FileScanner.Listener() {
                @Override
                public void onDirectory(Path dir) {
                    st.currentFolder = dir.toString();
                    st.foldersScanned++;
                }

                @Override
                public void onFile(Path file) {
                    st.currentFile = file.getFileName() == null ? file.toString() : file.getFileName().toString();
                    st.filesScanned++;
                }
            });
            st.filesScanned = found.files.size();
            st.foldersScanned = found.folders;
            st.totalBytes = found.totalBytes;
            st.skipped = found.skipped;

            st.phase = "HASHING";
            DuplicateDetector detector = new DuplicateDetector(cfg.hashAlgorithm, st, new DuplicateDetector.Progress() {
                @Override
                public void begin(int candidates) {
                    st.candidates = candidates;
                }

                @Override
                public void current(FileRecord f) {
                    st.currentFile = f.name;
                    st.currentFolder = f.dir;
                }

                @Override
                public void advance() {
                    st.hashed++;
                }

                @Override
                public void skipped(FileRecord f, IOException e) {
                    st.skipped++;
                    LOG.log(Level.FINE, "File could not be read: " + f.path, e);
                }

                @Override
                public void group(List<FileRecord> g) {
                    st.duplicateGroups++;
                    st.duplicateFiles += g.size();
                    st.recoverableBytes += g.get(0).size * (g.size() - 1);
                }
            });
            List<List<FileRecord>> groups = detector.find(found.files);

            st.phase = "SAVING";
            st.currentFile = "";
            st.currentFolder = "Saving results...";
            ScanDao.saveResults(st.scanId, found.files, groups);

            row.status = "COMPLETED";
            row.message = st.skipped > 0 ? "Scan completed with some inaccessible files." : "Scan completed.";
            fill(row, st, found.files.size(), found.folders);
            ScanDao.finish(row);
            st.message = row.message;
            st.phase = "DONE";
            st.endMs = System.currentTimeMillis();
            st.status = "COMPLETED";
            ActivityService.log(user, "SCAN_COMPLETED", "Scan #" + st.scanId + " finished: " + st.filesScanned
                    + " files, " + st.duplicateGroups + " duplicate groups", ip);
        } catch (ScanCancelledException e) {
            finishAbnormally(st, row, "CANCELLED", "Scan was cancelled.", user, ip, "SCAN_CANCELLED");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Scan #" + st.scanId + " could not read the folder", e);
            finishAbnormally(st, row, "FAILED", "Unable to access this folder.", user, ip, "SCAN_FAILED");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Scan #" + st.scanId + " failed", e);
            finishAbnormally(st, row, "FAILED", "The scan failed. See the server log for details.", user, ip, "SCAN_FAILED");
        }
    }

    private void finishAbnormally(ScanState st, Scan row, String status, String message, String user, String ip, String action) {
        try {
            row.status = status;
            row.message = message;
            fill(row, st, st.filesScanned, st.foldersScanned);
            ScanDao.finish(row);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Could not record the end of scan #" + st.scanId, e);
        }
        st.message = message;
        st.phase = "DONE";
        st.endMs = System.currentTimeMillis();
        st.status = status;
        ActivityService.log(user, action, "Scan #" + st.scanId + " " + status.toLowerCase() + ": " + message, ip);
    }

    private static void fill(Scan row, ScanState st, long files, long folders) {
        row.totalFiles = files;
        row.totalFolders = folders;
        row.totalBytes = st.totalBytes;
        row.duplicateFiles = st.duplicateFiles;
        row.duplicateGroups = st.duplicateGroups;
        row.recoverableBytes = st.recoverableBytes;
        row.skippedFiles = st.skipped;
    }

    /** Snapshot for the progress endpoint; "IDLE" when nothing has run since startup. */
    public Map<String, Object> progress() {
        ScanState s = state;
        return s == null ? Map.of("status", "IDLE") : s.snapshot();
    }
}
