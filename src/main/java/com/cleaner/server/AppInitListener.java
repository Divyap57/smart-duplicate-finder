package com.cleaner.server;

import com.cleaner.dao.ScanDao;
import com.cleaner.service.AuthService;
import com.cleaner.util.Db;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs inside the web application: creates the schema, seeds the development admin account and cleans up
 * scans that a previous crash left in RUNNING state. Doing this here (not in main) guarantees the database
 * is initialised by the same class loader that serves requests.
 */
@WebListener
public class AppInitListener implements ServletContextListener {
    private static final Logger LOG = Logger.getLogger(AppInitListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            Db.init();
            AuthService.seedDefaultAdmin();
            int stale = ScanDao.failStale();
            if (stale > 0) LOG.info("Marked " + stale + " interrupted scan(s) as FAILED.");
            LOG.info("Database ready.");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Database initialisation failed", e);
            System.setProperty("sdf.startupError", String.valueOf(e.getMessage()));
            throw new IllegalStateException("Database initialisation failed: " + e.getMessage(), e);
        }
    }
}
