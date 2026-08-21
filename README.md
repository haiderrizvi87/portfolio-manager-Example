# Team Setup Guide

Follow this once, in order, before touching the code. Each teammate does this on their own
machine — this project uses a separate local database per person (see "Why everyone needs
their own MySQL" at the bottom), so there's no shared server to configure.

---

## 1. Install prerequisites

| Tool | Version | Check if already installed |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| MySQL Community Server | see Step 2 below | `mysql --version` (if on PATH) |
| IntelliJ IDEA | any recent version | — |

If JDK is missing or older than 17, install it before continuing — Gradle will fail otherwise.

---

## 2. Install MySQL — matching the version the project was built against

To avoid "works on my machine" issues, install the **same MySQL version** as the rest of the
team, not just whatever's newest.

**Version currently in use by the team:** `________________`
*(whoever set this up first: run `mysql --version` and fill this in before sharing this file)*

### Option A — quick install via winget (Windows Package Manager, built into Windows 10/11)

```powershell
winget install Oracle.MySQL
```

This installs a recent 8.0.x release. Fine for this project, since nothing here depends on a
specific patch version — just close enough to "8.0.x" for everyone.

### Option B — exact version match (if you want it byte-identical)

1. Go to [dev.mysql.com/downloads/mysql](https://dev.mysql.com/downloads/mysql/)
2. Click **"Looking for previous GA versions?"** near the bottom
3. Select the exact version number from the table above
4. Download the Windows installer and run it — choose the standard/default setup
5. **Set a root password when prompted — write it down, you'll need it in Step 5**

### Verify the install

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --version
```

If `mysql` isn't recognized directly in PowerShell (common — it's not always added to PATH
automatically), use the full path above, or add
`C:\Program Files\MySQL\MySQL Server 8.0\bin` to your system PATH via
**Environment Variables → Path → Edit → New**, then restart your terminal.

---

## 3. Clone the repository

In IntelliJ: **Git → Clone...** (or **VCS → Get from Version Control**), paste the repo URL.

Or via terminal:
```powershell
git clone <repo-url>
```

**Watch out for nested folders** — if you end up with `portfolio-manager\portfolio-manager\...`,
open the **inner** folder in IntelliJ (the one directly containing `build.gradle`), not the outer
wrapper.

---

## 4. Open in IntelliJ and let Gradle sync

**File → Open** → select the project folder (the one with `build.gradle` in it).

IntelliJ should detect it as a Gradle project automatically and start syncing — this needs
internet access the first time, to download `mysql-connector-j` and `gson` from Maven Central.

Check **File → Project Structure → Project → SDK** is set to Java 17+.

---

## 5. Set your own MySQL password in the code

Open `src/main/java/com/simplywealth/portfolio/config/DatabaseConfig.java` and change:

```java
private static final String PASSWORD = "CHANGE_ME";
```

to whatever **your own** MySQL root password is (the one you set in Step 2).
**Never commit your real password back to the repo** — leave a placeholder like `CHANGE_ME`
in place before pushing anything.

---

## 6. Run it — database and tables create themselves automatically

Right-click `Main.java` → **Run 'Main.main()'**.

You should see:
```
Portfolio Manager backend running on http://localhost:8080
```

The app automatically creates the `portfolio_manager` database and both tables the first time
it runs, if they don't already exist — no manual SQL commands needed.

*(If this throws a `SQLException`, it's almost always the password in Step 5 not matching your
actual MySQL root password.)*

---

## 7. Open the frontend

Right-click `frontend/index.html` in the project tree → **Open in Browser**.

**Do not visit `localhost:8080` directly in your browser** — that's the backend API only, with
no homepage. You'll get a `404 Not Found`, which is expected. The actual app is the HTML file.

---

## 8. Optional: verify everything works without touching MySQL at all

```powershell
.\gradlew.bat runVerification
```
(If `gradlew` isn't recognized, generate it via IntelliJ's Gradle panel first:
**Tasks → build → wrapper**.)

Expect: `===== RESULTS: 22 passed, 0 failed =====`. This runs against fake in-memory data, so
it's a good first check that your Gradle/JDK setup itself is working before debugging anything
database-related.

---

## Why everyone needs their own MySQL

This project is built as single-user, no-authentication (see the spec, NFR1) — each person's
`DatabaseConfig.java` connects to `localhost:3306` on their **own** machine. Nobody's holdings,
searches, or recorded investments will appear on anyone else's machine, and that's expected, not
a bug — you're each running a fully independent copy of the app.

## Common gotchas we hit setting this up

- **PowerShell doesn't support `mysql < file.sql`** — the `<` redirect operator isn't
  implemented in PowerShell. Use `Get-Content file.sql | mysql -u root -p` instead.
- **`gradle`/`gradlew` "not recognized"** — use IntelliJ's Gradle tool window (right-hand
  panel) instead of the terminal, or generate the wrapper first via
  **Gradle panel → Tasks → build → wrapper**.
- **IntelliJ's Database tool window shows "No data sources"** — that's a separate, optional
  GUI feature and doesn't connect itself automatically. Set it up via
  **Create data source → MySQL**, entering the same host/port/database/credentials as
  `DatabaseConfig.java`, if you want to browse tables visually.

