package com.cleaner.util;

import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FileUtil {
    private static final Map<String, Set<String>> CATEGORIES = Map.of(
            "Images", Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "svg", "heic", "ico", "raw", "cr2", "nef"),
            "Videos", Set.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg"),
            "Audio", Set.of("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"),
            "Documents", Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "md", "epub"),
            "Archives", Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso"),
            "Code", Set.of("java", "js", "py", "html", "css", "json", "xml", "c", "cpp", "h", "cs", "ts", "sql", "class", "jar"),
            "Executables", Set.of("exe", "msi", "dll", "apk", "bat", "sh"));

    private FileUtil() { }

    public static String extension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i <= 0 || i == fileName.length() - 1) return "";
        String e = fileName.substring(i + 1).toLowerCase(Locale.ROOT);
        return e.length() > 32 ? e.substring(0, 32) : e;
    }

    public static String category(String extension) {
        String e = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Set<String>> c : CATEGORIES.entrySet()) {
            if (c.getValue().contains(e)) return c.getKey();
        }
        return "Other";
    }

    public static String mime(String extension) {
        String m = extension.isEmpty() ? null : URLConnection.guessContentTypeFromName("x." + extension);
        return m != null ? m : "application/octet-stream";
    }

    /** Strips path separators and control characters so a name can never escape a directory. */
    public static String safeName(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return n.isEmpty() ? "file" : n;
    }
}
