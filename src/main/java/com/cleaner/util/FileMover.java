package com.cleaner.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Filesystem half of quarantine / restore. Never overwrites an existing file. */
public final class FileMover {
    private FileMover() { }

    /** Moves {@code src} into {@code quarantineDir} under a unique name and returns the new path. */
    public static Path moveIn(Path src, Path quarantineDir, String name) throws IOException {
        Files.createDirectories(quarantineDir);
        Path dest = quarantineDir.resolve(UUID.randomUUID().toString().substring(0, 8) + "_" + FileUtil.safeName(name));
        Files.move(src, dest); // no REPLACE_EXISTING; copies + deletes across volumes
        return dest;
    }

    /** Moves a quarantined file back. Recreates the folder; picks a free name if the original is taken. */
    public static Path moveBack(Path quarantined, Path original) throws IOException {
        Path parent = original.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path dest = freeName(original);
        Files.move(quarantined, dest);
        return dest;
    }

    /** photo.jpg -> photo (restored).jpg -> photo (restored 2).jpg ... */
    public static Path freeName(Path wanted) {
        if (!Files.exists(wanted)) return wanted;
        String name = wanted.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            Path p = wanted.resolveSibling(base + (i == 1 ? " (restored)" : " (restored " + i + ")") + ext);
            if (!Files.exists(p)) return p;
        }
    }
}
