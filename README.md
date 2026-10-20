# Anonymous Student Review System

A complete, end-to-end anonymous academic feedback platform built in **Pure Java** using only the standard JDK and the JDK's built-in `com.sun.net.httpserver.HttpServer`. The only external dependency is the SQLite JDBC driver.

Three roles, cleanly separated:

| Role | Can do |
|------|--------|
| **Super Admin** | Create / disable / delete HOD (and admin) accounts, reset passwords |
| **HOD** | Create review forms (teacher + course + custom questions), get a unique anonymous link, open/close forms, view averages per question and the anonymous comment list |
| **Student (anonymous)** | Open the shared link, submit 1–5 ratings + an optional comment. **No account, no login, no identity stored.** |

---

## 1. Tech Stack & Constraints Honored

- **Backend:** Pure Java (JDK 17+). Web layer = `com.sun.net.httpserver.HttpServer`. No Spring, Tomcat, Jetty, or Servlet API.
- **Database:** SQLite via standard JDBC (`java.sql.*`) using `PreparedStatement` for every query (SQL-injection safe). The SQLite JDBC JAR is the **only** external dependency (plus its SLF4J logging shim, only to silence driver logs).
- **Security:** Passwords hashed with **SHA-256** via `java.security.MessageDigest`, salted per user. Session auth uses an HTTP-only random token cookie.
- **Frontend:** Vanilla HTML/CSS/JS served as static files by the Java server; the backend doubles as a JSON REST API under `/api`.
- **Architecture:** Server/router (`WebServer`) → `HttpHandler` classes (`handler/`) → DAO layer (`dao/`) → models (`model/`), with `util/` and `config/` helpers.

---

## 2. Directory Layout

```
idp1/
├── start.ps1                    # ONE command: compiles + runs the app
├── README.md
├── lib/                         # external JARs (SQLite JDBC + SLF4J)
│   ├── sqlite-jdbc-3.46.1.3.jar
│   ├── slf4j-api-2.0.16.jar
│   └── slf4j-simple-2.0.16.jar
├── public/                      # static frontend (served by the Java server)
│   ├── index.html               # staff login
│   ├── dashboard.html/.js       # HOD dashboard (forms + analytics)
│   ├── admin.html/.js           # super-admin console (account management)
│   ├── review.html/.js          # public anonymous review form
│   ├── styles.css               # shared styling
│   └── app.js                   # shared fetch/helpers
└── src/com/reviewapp/
    ├── server/
    │   ├── Main.java            # entry point
    │   ├── WebServer.java       # HttpServer bootstrap + route table
    │   ├── BaseApiHandler.java  # JSON/error-handling base for handlers
    │   ├── Services.java        # session + authorization services
    │   └── ApiException.java    # typed HTTP errors
    ├── handler/
    │   ├── AuthHandler.java          # /api/auth/{login,logout,me}
    │   ├── AdminHandler.java         # /api/admin/users ...
    │   ├── FormHandler.java          # /api/forms ...
    │   ├── PublicReviewHandler.java  # /api/public/forms/{token} ...
    │   └── StaticFileHandler.java    # serves public/ (filesystem or classpath)
    ├── dao/
    │   ├── SchemaInitializer.java    # creates tables + seeds admin
    │   ├── UserDao.java
    │   ├── SessionDao.java
    │   ├── FormDao.java
    │   ├── QuestionDao.java
    │   └── ResponseDao.java          # anonymous, transactional submissions
    ├── model/
    │   ├── Role.java, User.java, Session.java
    │   ├── Form.java, Question.java, QuestionStat.java
    ├── config/
    │   ├── AppConfig.java            # port, DB path, cookie name, TTL
    │   └── Database.java             # JDBC connection factory
    └── util/
        ├── Json.java                 # dependency-free JSON encoder/decoder
        ├── PasswordUtil.java         # salted SHA-256 hashing
        ├── HttpUtil.java             # request/response/cookie helpers
        └── TokenGenerator.java       # secure random tokens
```

---

## 3. Setup & Execution Guide

### Prerequisites
- JDK 17 or newer (`java -version`, `javac -version`). This project was tested on JDK 23.

### Step 1 — Download the SQLite JDBC driver JAR
The JARs are already present in `lib/`. To re-download them manually (PowerShell):

```powershell
New-Item -ItemType Directory -Force -Path lib | Out-Null
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.3/sqlite-jdbc-3.46.1.3.jar" -OutFile "lib/sqlite-jdbc-3.46.1.3.jar"
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"      -OutFile "lib/slf4j-api-2.0.16.jar"
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar" -OutFile "lib/slf4j-simple-2.0.16.jar"
```

> The SLF4J jars are **not** used by application code — they only satisfy the SQLite driver's optional SLF4J binding so you don't see "no SLF4J provider" noise. The SQLite JAR alone is the only real dependency.

### Step 2 — Run (one command)

The simplest way — compile **and** start in a single step:

```powershell
.\start.ps1              # runs on http://localhost:8080
.\start.ps1 -Port 9090   # or choose another port
```

That's it. Open **http://localhost:8080**.

<details>
<summary>Prefer the raw <code>javac</code> / <code>java</code> commands? (per the project spec)</summary>

```powershell
# Compile
$cp = "lib\sqlite-jdbc-3.46.1.3.jar;lib\slf4j-api-2.0.16.jar;lib\slf4j-simple-2.0.16.jar"
$files = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }
javac -d out -cp $cp $files

# Run
java -DPORT=8080 -cp "out;$cp" com.reviewapp.server.Main
```
(Linux/macOS: use `:` instead of `;` as the classpath separator and `$(find src -name '*.java')`.)
</details>

### Step 3 — Sign in
Default super-admin account (seeded on first run):
```
username: admin
password: admin123
```
> Change this password after first login in a real deployment.

---

## 4. Typical Walkthrough

1. **Sign in** as `admin` at `http://localhost:8080/`.
2. In the **Admin Console**, click **+ New Account**, fill in a department + username/password, and create an **HOD**.
3. **Sign out**, then sign in as the HOD at the same login page → you land on the **HOD Dashboard**.
4. Open **Create Form**, enter the teacher & course, tweak the default 5 rating questions (or add your own), and submit.
5. Copy the generated **anonymous review link** (e.g. `http://localhost:8080/review.html?token=AB12...`) and share it.
6. **Open the link in any browser** (no login needed), rate each question and optionally add a comment, then submit.
7. Back in the HOD dashboard, click **View analytics** to see the overall average, per-question averages, response count, and the anonymous comment list.

### Configuration (optional environment variables)
| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `DB_PATH` | `review.db` | SQLite file location |
| `PUBLIC_DIR` | `public` | Static assets directory |
| `SESSION_TTL_SECONDS` | `28800` (8h) | Login session lifetime |
| `BASE_URL` | `http://localhost:<PORT>` | Base URL printed on startup |

---

## 5. REST API Summary

All endpoints are JSON. Authentication is via the `REVIEW_SESSION` HTTP-only cookie set by `/api/auth/login`.

| Method & Path | Auth | Purpose |
|---------------|------|---------|
| `POST /api/auth/login` | — | Body `{username,password}` → sets session cookie |
| `POST /api/auth/logout` | cookie | Clears session |
| `GET  /api/auth/me` | cookie | Current user |
| `GET  /api/admin/users` | Super Admin | List HOD/admin accounts |
| `POST /api/admin/users` | Super Admin | Create account `{username,password,role,department,fullName}` |
| `PUT  /api/admin/users/{id}` | Super Admin | `{active}` and/or `{password}` |
| `DELETE /api/admin/users/{id}` | Super Admin | Delete HOD |
| `GET  /api/admin/overview` | Super Admin | Platform counts |
| `GET  /api/forms` | HOD | Own forms + response counts + links |
| `POST /api/forms` | HOD | `{teacherName,courseName,title,questions[]}` → form + token/link |
| `GET  /api/forms/{id}` | HOD (owner) | Form detail + questions |
| `PUT  /api/forms/{id}` | HOD (owner) | `{open:true/false}` |
| `DELETE /api/forms/{id}` | HOD (owner) | Delete form + responses |
| `GET  /api/forms/{id}/analytics` | HOD (owner) | Averages per question, overall average, comments |
| `GET  /api/public/forms/{token}` | **public** | Form metadata + questions |
| `POST /api/public/forms/{token}/submit` | **public** | `{answers:{qid:1-5,...}, comment}` |

---

## 6. Anonymity & Security Notes

- The `responses` table stores **only** `form_id`, `comment`, and `submitted_at` — there is **no** student id, IP address, or user-agent column, and none is written. The public handlers never read or persist identity data.
- Passwords are stored as `hexSalt:hexHash` (SHA-256), verified in constant time with `MessageDigest.isEqual`.
- Every SQL statement uses `PreparedStatement` with bound parameters.
- Session ids and form tokens come from `SecureRandom`.
- Protected endpoints enforce both authentication and role/ownership checks (an HOD cannot read another HOD's forms or analytics).
- Static file serving resolves paths under the `public/` root and rejects traversal outside it.

---

## 7. Verified Behaviors (end-to-end tested)

- Super Admin login, HOD account creation, enable/disable, password reset, delete.
- HOD form creation → unique token → public fetch → two anonymous submissions → validation of out-of-range / missing answers → analytics averages (`4.33`, `4.0`, `4.5`, `4.5` correct) → comments listed.
- Form close (public endpoint returns `410`).
- Unauthenticated access → `401`; wrong role → `403`; cross-HOD access → `403`; malformed JSON → `400`; SQL-injection login attempt → `401`; invalid token → `404`; logout invalidates session; path traversal safely falls back to `index.html`.
