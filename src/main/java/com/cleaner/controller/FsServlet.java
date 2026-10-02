package com.cleaner.controller;

import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folder picker support: lists drives, or the sub-folders of one folder. Directories only, names only -
 * file contents are never exposed and nothing is written.
 */
@WebServlet("/api/fs")
public class FsServlet extends BaseServlet {
    private static final int MAX_ENTRIES = 1000;

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String p = req.getParameter("path");
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, String>> dirs = new ArrayList<>();
        if (p == null || p.isBlank()) {
            out.put("path", "");
            out.put("parent", null);
            dirs.add(entry("Home", System.getProperty("user.home")));
            for (File root : File.listRoots()) dirs.add(entry(root.getPath(), root.getPath()));
            out.put("dirs", dirs);
            ok(resp, out);
            return;
        }
        Path dir = parse(p);
        if (!Files.isDirectory(dir) || !Files.isReadable(dir)) throw new ApiException(400, "Unable to access this folder.");
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path c : ds) {
                if (children.size() >= MAX_ENTRIES) break;
                children.add(c);
            }
        } catch (IOException e) {
            throw new ApiException(400, "Unable to access this folder.");
        }
        children.sort(Comparator.comparing(c -> c.getFileName().toString().toLowerCase()));
        for (Path c : children) dirs.add(entry(c.getFileName().toString(), c.toString()));
        out.put("path", dir.toString());
        out.put("parent", dir.getParent() == null ? "" : dir.getParent().toString());
        out.put("dirs", dirs);
        ok(resp, out);
    }

    private static Path parse(String p) {
        try {
            Path dir = Paths.get(p.trim()).normalize();
            if (!dir.isAbsolute()) throw new ApiException(400, "Please use a full path.");
            return dir;
        } catch (InvalidPathException e) {
            throw new ApiException(400, "That folder path is not valid.");
        }
    }

    private static Map<String, String> entry(String name, String path) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("path", path);
        return m;
    }
}
