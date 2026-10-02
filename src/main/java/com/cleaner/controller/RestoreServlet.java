package com.cleaner.controller;

import com.cleaner.service.QuarantineService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** POST {"ids":[quarantine ids]} - moves files back to where they came from. */
@WebServlet("/api/restore")
public class RestoreServlet extends BaseServlet {
    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        IdsBody b = body(req, IdsBody.class);
        ok(resp, QuarantineService.restore(b.ids, username(req), ip(req)));
    }
}
