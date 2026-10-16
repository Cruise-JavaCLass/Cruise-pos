# Autonomous GitHub Repository Maintainer Powered by Google Jules (Cruise-pos)

## 1. Overview & Architecture

This Android POS mobile application is maintained autonomously by **Google Jules**, connecting GitHub Actions with the official **Google Jules REST API (`v1alpha`)**:

1. **Android CI Pipeline (`.github/workflows/ci.yml`):**
   Executes `./gradlew test --stacktrace` on push/PR to `main` to verify Kotlin compilation and unit tests.
2. **Weekly & On-Demand Android Audit (`.github/workflows/jules-audit.yml`):**
   Scans Kotlin source files, ViewModels, Room offline persistence, and Jetpack Compose state management. Proposes clean improvements, runs `./gradlew test`, and opens a structured Pull Request.
3. **Automatic CI Failure Repair (`.github/workflows/jules-fix-ci.yml`):**
   Monitors the `Android CI Pipeline` on `main`. When an Android build or unit test fails, Jules investigates the diagnostic logs, fixes the issue in Kotlin or Gradle DSL, verifies with `./gradlew test`, and submits a PR.

---

## 2. Setup & Configuration

### 2.1. Add Repository Secret
In GitHub (**Settings** > **Secrets and variables** > **Actions**), add:
- `JULES_API_KEY`: Official Google Jules API key (starts with `AIza...`).

### 2.2. Connect to Google Jules
Ensure `Cruise-JavaCLass/Cruise-pos` is connected in [Google Jules Console](https://jules.google). The workflow automatically queries `GET /v1alpha/sources` to identify the source.

---

## 3. Workflows

### 3.1. Weekly Audit (`jules-audit.yml`)
- **Schedule:** Every Monday at **03:00 UTC** (`cron: '0 3 * * 1'`).
- **Manual Trigger:** Run via **Actions** > **Jules Repository Audit** with options: `full`, `bugs`, `tests`, `security`, `code-quality`, `documentation`.
- **Deduplication:** Prevents opening duplicate audits if an audit occurred within the past 24 hours (bypassable with `force_audit: true`).

### 3.2. CI Failure Repair (`jules-fix-ci.yml`)
- **Trigger:** Automated on failed completion of `Android CI Pipeline` on branch `main`.
- **Security:**
  - Ignores success/cancelled runs.
  - Ignores untrusted external PR branches.
  - Redacts sensitive credentials (Google API keys, GitHub tokens, Bearer tokens, Android keystore passwords).
  - Truncates failure logs to 250 lines / 25 KB.

---

## 4. Safety & Review Guidelines

1. **No Direct Push:** Jules only creates branches (`jules/...`) and opens Pull Requests (`automationMode: AUTO_CREATE_PR`).
2. **Human Review Required:** All Jules PRs must pass CI checks and be reviewed and approved by human engineers before merging.
