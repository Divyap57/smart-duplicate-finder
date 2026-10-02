package com.cleaner.controller;

import com.cleaner.service.QuarantineService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** POST {"ids":[quarantine ids],"confirm":true} - the only place a file is permanently deleted. */
@WebServlet("/api/delete")
public class DeleteServlet extends BaseServlet {
    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        IdsBody b = body(req, IdsBody.class);
        ok(resp, QuarantineService.deletePermanently(b.ids, b.confirm, username(req), ip(req)));
    }
}
