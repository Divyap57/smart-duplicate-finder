package com.cleaner.controller;

import com.cleaner.dao.FileDao;
import com.cleaner.model.FileRecord;
import com.cleaner.util.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * POST {"id": fileId} - shows the file in the OS file manager. The server runs on the user's own machine, so
 * this opens Explorer/Finder there. Only paths already stored in the database can be opened.
 */
@WebServlet("/api/open-location")
public class OpenLocationServlet extends BaseServlet {
    public static class Target {
        public long id;
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        FileRecord f = FileDao.byId(body(req, Target.class).id);
        if (f == null || !"ACTIVE".equals(f.status)) throw new ApiException(404, "File not found.");
        Path p = Paths.get(f.path);
        if (!Files.exists(p)) throw new ApiException(404, "File no longer exists.");

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select," + p).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", p.toString()).start();
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(new File(f.dir));
            } else {
                throw new ApiException(501, "Opening a folder is not supported on this system.");
            }
        } catch (java.io.IOException e) {
            LOG.warning("Could not open file manager: " + e.getMessage());
            throw new ApiException(500, "Could not open the folder.");
        }
        ok(resp, Map.of("ok", true));
    }
}
