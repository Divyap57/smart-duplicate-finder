package com.cleaner.controller;

import com.cleaner.dao.FileDao;
import com.cleaner.dao.QuarantineDao;
import com.cleaner.model.FileRecord;
import com.cleaner.model.QuarantineItem;
import com.cleaner.util.ApiException;
import com.cleaner.util.FileUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GET /api/files/{id} */
@WebServlet("/api/files/*")
public class FileDetailsServlet extends BaseServlet {
    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        FileRecord f = FileDao.byId(pathId(req));
        if (f == null) throw new ApiException(404, "File not found.");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", f);
        m.put("category", FileUtil.category(f.extension));
        m.put("existsOnDisk", "ACTIVE".equals(f.status) && Files.isRegularFile(Paths.get(f.path)));
        m.put("groupId", FileDao.latestGroupOf(f.id));

        List<Map<String, Object>> copies = new ArrayList<>();
        if (f.hash != null) {
            for (FileRecord c : FileDao.allByHash(f.hash)) {
                if (c.id == f.id) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", c.id);
                r.put("path", c.path);
                r.put("status", c.status);
                copies.add(r);
            }
        }
        m.put("otherCopies", copies);

        QuarantineItem q = QuarantineDao.activeForFile(f.id);
        m.put("quarantineId", q == null ? null : q.id);
        m.put("quarantinedAt", q == null ? null : q.quarantinedAt);
        ok(resp, m);
    }
}
