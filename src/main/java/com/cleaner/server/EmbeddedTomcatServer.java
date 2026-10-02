package com.cleaner.server;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

/**
 * Starts Tomcat 10.1 inside this JVM and deploys the web application from src/main/webapp.
 * target/classes is mounted as /WEB-INF/classes, so Tomcat's normal annotation scanning registers every
 * @WebServlet, @WebFilter and @WebListener class - no manual registration needed.
 * Run with:  mvn compile exec:java   (or run this class from your IDE)
 */
public final class EmbeddedTomcatServer {
    public static final String CONTEXT_PATH = "/smart-duplicate-finder";

    private EmbeddedTomcatServer() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false"); // needed for "open file location" on some systems
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", System.getProperty("port", "8080")));

        File webapp = new File(System.getProperty("user.dir"), "src/main/webapp");
        File classes = new File(System.getProperty("user.dir"), "target/classes");
        if (!webapp.isDirectory() || !classes.isDirectory()) {
            System.err.println("Run this from the project folder after 'mvn compile' (missing "
                    + (webapp.isDirectory() ? classes : webapp) + ").");
            System.exit(1);
        }

        Path base = Files.createTempDirectory("sdf-tomcat");
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(port);
        tomcat.getConnector(); // create the default HTTP connector
        tomcat.getHost().setAutoDeploy(false);

        Context ctx = tomcat.addWebapp(CONTEXT_PATH, webapp.getAbsolutePath());
        // Under "mvn exec:java" the dependencies live in Maven's class loader, not the system one.
        ctx.setParentClassLoader(EmbeddedTomcatServer.class.getClassLoader());
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes", classes.getAbsolutePath(), "/"));
        ctx.setResources(resources);

        tomcat.start();

        if (ctx.getState() != LifecycleState.STARTED) {
            String why = System.getProperty("sdf.startupError");
            System.err.println();
            System.err.println("The application failed to start" + (why != null ? ": " + why : "."));
            System.err.println("Check that MySQL is running and that the credentials in src/main/resources/db.properties");
            System.err.println("(or DB_URL / DB_USER / DB_PASSWORD) are correct. Full details are in the log above.");
            tomcat.stop();
            tomcat.destroy();
            System.exit(1);
        }

        System.out.println();
        System.out.println("Smart Duplicate File Finder is running:");
        System.out.println("  http://localhost:" + port + CONTEXT_PATH);
        System.out.println("Press Ctrl+C to stop.");
        tomcat.getServer().await();
    }
}
