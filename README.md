# Online Feedback Form - DevOps Lab

**Set B, Question 2** | **Naman Srivastava** | **Roll number: 2401331540166**

A minimal Java 21 HTTP application with one HTML feedback form. Fields: name, email,
feedback category, rating (1-5), comments, and submit. A successful submission shows
a confirmation page. No database or saved feedback; no personal information is logged.

## Run locally in Docker

From this folder, with Docker Desktop running:

```sh
docker compose up --build -d feedback
curl http://localhost:8082/health
```

Open <http://localhost:8082>. Fill every field and click **Submit feedback**.
The container is bound to the host loopback interface, runs as UID 10001, drops
Linux capabilities, and has a read-only root filesystem. Stop with:

```sh
docker compose stop feedback
```

## Maven build and tests

With Java 21 and Maven 3.9 installed:

```sh
mvn --batch-mode --no-transfer-progress clean package
java -jar target/online-feedback.jar
```

If Java/Maven are absent on the host, use the same build in Docker:

```sh
docker run --rm -v "$PWD:/app" -v feedback-lab-maven-cache:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-21 mvn --batch-mode --no-transfer-progress clean package
```

`package` runs Checkstyle, compiles Java with all compiler warnings treated as
errors, runs JUnit HTTP tests, and writes **target/online-feedback.jar**.
The Java standard library is the only runtime dependency. JUnit is test-only.

## GitHub feature branch and pull request

The repository URL and actual pull request/run links are recorded in
**evidence/RESULTS.md**. The implementation branch is `codex/online-feedback`.
The private repository was created for this practical, rather than modifying an
unrelated existing project.

To repeat the workflow in your own repository:

```sh
git switch -c codex/online-feedback
git add .
git commit -m "Add feedback application and DevOps workflow"
git push -u origin codex/online-feedback
```

Open a pull request into `main`. The checked-in `.github/workflows/maven.yml`
runs on every push, pull request and manual dispatch. It uses Java 21, Maven
caching, read-only repository permissions, immutable action SHAs, and uploads
the executable JAR and JUnit reports. Wait for **Maven Build** to succeed.

## Jenkins pipeline

`Jenkinsfile` runs checkout, Maven lint/build/tests, JUnit publishing and JAR
archiving. On an existing Jenkins instance, configure a **Pipeline script from
SCM**, select your Git repository and branch, and use script path `Jenkinsfile`.
The agent needs Java 21, Maven 3.9 and the Git, Pipeline and JUnit plugins.

A reproducible local Jenkins setup is also included. Generate a local admin
password once if `.jenkins-password` is absent:

```sh
python3 - <<'PY'
from pathlib import Path
import secrets
p = Path('.jenkins-password')
if not p.exists():
    p.write_text(secrets.token_urlsafe(32) + '\n')
    p.chmod(0o600)
PY
docker compose --profile ci up --build -d jenkins
```

Open <http://localhost:8083>, sign in as **naman-srivastava**, and use the password from
`.jenkins-password`. The generated password is excluded from Git and the ZIP.
The first start automatically creates and runs the **online-feedback-srivastava** job.

For this self-contained local demonstration, Jenkins reads the checked-in
Jenkinsfile and copies only `src`, `pom.xml` and `checkstyle.xml` from a read-only
source mount. It does not need GitHub credentials or access to the Docker socket.
When `LAB_SOURCE_DIR` is unset, the same Jenkinsfile uses normal `checkout scm`.
The local job reads its pipeline definition at initial provisioning; on later
Jenkinsfile changes, update the job definition or configure Pipeline from SCM.

Stop Jenkins without removing build history:

```sh
docker compose --profile ci stop jenkins
```

## Security and scope

The server enforces lengths, categories, rating and email syntax; rejects
unknown/duplicate fields, malformed encoding and bodies over 16 KiB; and serves
only explicit routes. User data is never rendered, persisted or logged.
Host and Origin checks reject non-local and cross-site submissions. There are
no sessions, cookies, authentication, uploads or database connections in the app.
CSP, frame protection, no-sniff and no-store headers are set. HTTP is intentional
for a loopback-only lab; HSTS is not emitted on this HTTP endpoint.

The host-to-container port must remain 8082 for these exact local Host/Origin
checks. The app is a lab demonstration, not a public production service.

## Submission evidence

- `evidence/Online-Feedback-Lab-Evidence.pdf`: ordered checkpoint report, student details and screenshots.
- `evidence/screenshots/`: individual screenshots.
- `evidence/logs/`: original build, test, security and CI logs.
- `evidence/RESULTS.md`: exact observed outcomes and links.

The evidence PDF and individual screenshots must be uploaded to the examiner's
submission location. No submission destination was supplied in the request.
Screenshots of log transcripts are identified as transcripts, and use actual
saved output. No CI outcome is simulated.

This is a separate project for Naman Srivastava. The original Naman Rai project
and its ports 8080/8081 are preserved. This copy uses app port 8082 and Jenkins
port 8083, independent Docker images, a Compose project and a Jenkins volume.
