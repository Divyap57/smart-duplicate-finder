# Smart Duplicate File Finder & Storage Cleaner

A local web application that scans folders, finds duplicate files by content hash, shows how much space you can
recover, and moves unwanted copies to a **quarantine** (never straight to deletion) so everything can be restored.

Java 17 · Jakarta Servlet 6 · embedded Tomcat 10.1 · MySQL 8 · Maven · Bootstrap 5 · Chart.js

> The app runs on **your own computer** and reads/moves files on **that** computer. Do not expose it to a network.

---

## Requirements

| Tool    | Version                                   |
|---------|-------------------------------------------|
| Java    | JDK 17 or newer (built with `release 17`) |
| Maven   | 3.8+                                      |
| MySQL   | 8.0+ (running locally)                    |
| Browser | Any modern browser, with internet access  |

The pages load Bootstrap, Chart.js and Font Awesome from public CDNs, so the browser needs internet access.

## MySQL setup

1. Install and start MySQL 8 (service name `MySQL80` on Windows).
2. Make sure you know a user that may create a database. The default config uses `root` / `root`.

The schema is created **automatically** the first time the app starts (database `smart_duplicate_finder`).
To create it by hand instead, run `database/schema.sql` in MySQL Workbench or:

```
mysql -u root -p < database/schema.sql
```

## Configuration

Database settings live in `src/main/resources/db.properties`:

```
db.url=jdbc:mysql://localhost:3306/smart_duplicate_finder?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
db.user=root
db.password=root
```

**Change `db.password` to your MySQL password.** You can also override without editing files, using environment
variables `DB_URL`, `DB_USER`, `DB_PASSWORD` (PowerShell: `$env:DB_PASSWORD="secret"`).

The port defaults to `8080`; change it with `PORT` (`$env:PORT="9090"`).

Scan options (excluded folders, size limits, hash algorithm, quarantine location, hidden/system files) are edited in the
app under **Settings** and stored in the database.

## Build

```
mvn clean package
```

Expected: `BUILD SUCCESS`.
The tests that need MySQL (`DaoTest`, `QuarantineServiceTest`, `AuthServiceTest`) use a separate throw-away database,
`smart_duplicate_finder_test`, with the same user/password (override with `TEST_DB_URL`, `TEST_DB_USER`,
`TEST_DB_PASSWORD`). If MySQL cannot be reached they are **skipped**, not failed, and the skip reason is printed.
Make sure MySQL is running and the password is right so that they actually run. If you do not want to run tests while
getting started: `mvn clean package -DskipTests`.

## Run

```
mvn compile exec:java
```

Then open **http://localhost:8080/smart-duplicate-finder**

Run it from the project folder (the one containing `pom.xml`): the embedded Tomcat serves `src/main/webapp` and mounts
`target/classes`, so every `@WebServlet`, `@WebFilter` and `@WebListener` class is picked up by Tomcat's normal annotation
scanning. You can also run `com.cleaner.server.EmbeddedTomcatServer` directly from IntelliJ (working directory = project root).

If MySQL is unreachable the server stops with a message saying so, instead of starting half-broken.

## Default login

| Username | Password   |
|----------|------------|
| `admin`  | `admin123` |

This development account is created on first start if the users table is empty. Other people can create their own
account from **Create an account** on the sign-in page (username, email, password of 8+ characters); sign-up accounts
get the `USER` role. Note that all accounts see the same scan results and quarantine, since the app has one shared file index.
Databases created before sign-up existed get the `users.email` column added automatically at startup. Passwords are stored as salted
PBKDF2-HMAC-SHA256 hashes. Change the password in the `users` table before using the app for anything real.

## How to scan

1. Open **Scan Files**, type a folder (for example `C:\Users\you\Documents` or `D:\`) or click **Browse…**.
2. Click **Start scan**. Progress, current file and folder, duplicates found and recoverable space update live.
   You can **Pause**, **Resume** or **Cancel**; the server keeps working while you navigate to other pages.
3. When it finishes, open **Duplicate Files**. Groups are listed by recoverable space; use search, type, size and sort
   controls, expand a group, tick the copies you want to remove (or use **Auto-select**: keep first / oldest / newest /
   shortest path), then **Quarantine selected**.

How duplicates are found: files are grouped by size first (different size = different file), big files get a cheap
64 KB partial hash, and what is left is compared by full SHA-256 (or SHA-512). Names are never compared.
Empty files are ignored. Symbolic links are never followed. Unreadable files are counted and skipped, and the scan
finishes with "Scan completed with some inaccessible files."

## How quarantine works

* **Quarantine** moves the file to `~/.smart-duplicate-finder/quarantine/` (changeable in Settings) under a unique name and
  records the original path, size, hash, time and user in MySQL. Nothing is deleted.
* **Restore** moves it back. Missing folders are recreated; if something now occupies the original name the file is
  restored as `name (restored).ext`. Existing files are never overwritten.
* **Delete permanently** (Quarantine page) is the only operation that destroys data. It asks for confirmation and only
  works on files that are currently in quarantine.
* Safety rules enforced on the server, whatever the browser sends:
  * the browser only sends database ids, never paths;
  * at least one copy of every duplicate group always stays (and must still exist on disk);
  * a file that changed since the scan is not moved (it is re-hashed first);
  * the quarantine folder is never scanned, and the quarantine location can only be changed while it is empty.

## Security notes

Login with session cookies (HttpOnly, new session id on login), CSRF token on every state-changing request, PBKDF2 password
hashes, failed-login throttling, prepared statements everywhere, all text rendered through HTML escaping, and
`X-Frame-Options` / `nosniff` headers. All pages and `/api/*` endpoints except the login page require a signed-in session.

## Project layout

```
pom.xml
database/schema.sql              MySQL schema (also executed at startup)
src/main/java/com/cleaner
  controller/                    servlets (JSON API)
  service/                       scanning, duplicate detection, quarantine, stats, settings, auth
  dao/                           JDBC data access (prepared statements)
  model/ util/ filter/           data classes, helpers, AuthFilter
  server/                        EmbeddedTomcatServer, AppInitListener
src/main/webapp                  HTML pages, css/, js/, WEB-INF/web.xml
src/test/java                    JUnit 5 tests
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `The application failed to start: ... Access denied` | Wrong MySQL user/password. Fix `db.properties` or set `DB_USER` / `DB_PASSWORD`. |
| `Communications link failure` | MySQL is not running or not on port 3306. Start the `MySQL80` service / fix `db.url`. |
| `Port 8080 already in use` | Stop the other program or set `PORT=9090`. |
| `Run this from the project folder after 'mvn compile'` | `cd` into the folder with `pom.xml` and run `mvn compile exec:java`. |
| Page is unstyled / charts missing | The browser could not reach the CDNs (jsdelivr, cdnjs). Check your internet connection. |
| A servlet URL gives 404 | The classes were not compiled: run `mvn compile` first, and run from the project folder. |
| "Unable to access this folder." | The folder does not exist or Windows denies access; try another folder or run as a user that can read it. |
| Tests are reported as skipped | MySQL was not reachable for the tests (see Build above). |
| Everything is slow on `C:\` | Use Settings to exclude big folders; system folders are ignored by default. |
| `BUILD FAILURE: package jakarta.servlet does not exist` | Dependencies were not downloaded: check your internet connection / Maven proxy settings. |

Server errors are written to the console log with full details; the browser only ever sees a short, friendly message.
