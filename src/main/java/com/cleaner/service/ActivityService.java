package com.cleaner.service;

import com.cleaner.dao.ActivityDao;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ActivityService {
    private static final Logger LOG = Logger.getLogger(ActivityService.class.getName());

    private ActivityService() { }

    /** Writes an audit entry. A logging failure must never break the action being logged. */
    public static void log(String user, String action, String description, String ip) {
        try {
            ActivityDao.insert(user == null ? "system" : user, action, description, ip);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write activity log entry " + action, e);
        }
    }
}
