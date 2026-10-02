package com.cleaner.service;

import com.cleaner.model.FileRecord;
import com.cleaner.util.FileUtil;
import com.cleaner.util.ScanControl;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recursive, failure-tolerant file walker. One unreadable file or folder never aborts the scan. */
public final class FileScanner {
    private static final int MAX_PATH_CHARS = 1000;
    private static final Set<String> SYSTEM_DIR_NAMES = Set.of(
            "$recycle.bin", "system volume information", "$windows.~bt", "$windows.~ws", "recovery", "config.msi");
    private static final Set<String> SYSTEM_ROOT_CHILDREN = Set.of("windows", "proc", "sys", "dev", "run", "system");

    public record Options(Set<Path> excluded, long minSize, long maxSize, boolean scanHidden,
                          boolean scanSystem, boolean ignoreSystemDirs) {
        public static Options defaults() {
            return new Options(Set.of(), 0, 0, false, false, true);
        }
    }

    public static final class Result {
        public final List<FileRecord> files = new ArrayList<>();
        public long folders;
        public long skipped;
        public long totalBytes;
    }

    /** Progress callbacks, invoked from the scanning thread. */
    public interface Listener {
        void onDirectory(Path dir);

        void onFile(Path file);
    }

    private FileScanner() { }

    public static Result scan(Path root, Options opt, ScanControl control, Listener listener) throws IOException {
        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new IOException("Unable to access this folder.");
        }
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException e) {
            realRoot = root.toAbsolutePath().normalize();
        }
        final Path start = realRoot;
        Result result = new Result();

        FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                control.checkpoint();
                if (!dir.equals(start) && skipDirectory(dir, opt)) return FileVisitResult.SKIP_SUBTREE;
                result.folders++;
                if (listener != null) listener.onDirectory(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                control.checkpoint();
                if (attrs.isSymbolicLink() || !attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                if (listener != null) listener.onFile(file);
                long size = attrs.size();
                if (size < opt.minSize() || (opt.maxSize() > 0 && size > opt.maxSize())) return FileVisitResult.CONTINUE;
                if (!opt.scanHidden() && isHidden(file)) return FileVisitResult.CONTINUE;
                if (!opt.scanSystem() && isSystemFile(file)) return FileVisitResult.CONTINUE;
                String path = file.toString();
                if (path.length() > MAX_PATH_CHARS || file.getFileName() == null) {
                    result.skipped++;
                    return FileVisitResult.CONTINUE;
                }
                FileRecord f = new FileRecord();
                f.path = path;
                f.dir = file.getParent() == null ? "" : file.getParent().toString();
                f.name = file.getFileName().toString();
                f.extension = FileUtil.extension(f.name);
                f.mime = FileUtil.mime(f.extension);
                f.size = size;
                f.createdMs = attrs.creationTime().toMillis();
                f.modifiedMs = attrs.lastModifiedTime().toMillis();
                f.accessedMs = attrs.lastAccessTime().toMillis();
                result.files.add(f);
                result.totalBytes += size;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                result.skipped++; // permission denied, locked, vanished mid-scan ...
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) result.skipped++;
                return FileVisitResult.CONTINUE;
            }
        };
        // No FOLLOW_LINKS: symbolic links are never traversed, so loops are impossible.
        Files.walkFileTree(start, EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE, visitor);
        return result;
    }

    static boolean skipDirectory(Path dir, Options opt) {
        for (Path ex : opt.excluded()) {
            if (dir.startsWith(ex)) return true;
        }
        if (!opt.scanHidden() && isHidden(dir)) return true;
        if (opt.ignoreSystemDirs()) {
            Path name = dir.getFileName();
            if (name != null) {
                String n = name.toString().toLowerCase(Locale.ROOT);
                if (SYSTEM_DIR_NAMES.contains(n)) return true;
                if (dir.getParent() != null && dir.getParent().equals(dir.getRoot()) && SYSTEM_ROOT_CHILDREN.contains(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isHidden(Path p) {
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isSystemFile(Path p) {
        try {
            return Files.readAttributes(p, DosFileAttributes.class).isSystem();
        } catch (UnsupportedOperationException | IOException e) {
            return false; // not a DOS filesystem, or unreadable attributes
        }
    }
}
