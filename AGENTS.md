# AI Coding Agent & Contributor Directives (AGENTS.md)

This repository (`Cruise-pos`) contains the POS / Android tablet application for the **Cruise Activity and Service Management System**.
All AI coding agents and human developers working in this repository must strictly adhere to the policies established in this document.

---

## 1. Jira Project & Issue Key Rules

- **Jira Project Key:** `KAN` (Domain: `https://huytran20052022.atlassian.net/`)
- **Hierarchy:** Epic -> Story/Task -> Subtask.
- **Rule of Record:** Every feature branch, commit message, and PR title MUST explicitly reference a valid Jira issue key (`KAN-xxx`).

---

## 2. Branching Strategy (GitHub Flow)

- **Default & Protected Branch:** `main`.
- **Branch Naming Standard:**
  - `feat/KAN-xxx-short-description`
  - `fix/KAN-xxx-short-description`
  - `test/KAN-xxx-short-description`
  - `chore/KAN-xxx-short-description`

---

## 3. Commit Message Conventions

Follow Conventional Commits with Jira issue reference:
```
[<JIRA-KEY>] <type>(<scope>): <concise description in imperative mood>
```

---

## 4. Pull Request Standards & Title Format

- **PR Title Format:** `[KAN-xxx] type: concise description`
- **PR Description:** Follow `.github/PULL_REQUEST_TEMPLATE.md` with Summary, Changes, Testing, Jira Link, and Notes.

---

## 5. Testing & Code Quality Gates

- **Mobile/POS Stack:** Android SDK, Kotlin, Gradle, Room (offline storage), Retrofit/OkHttp, JUnit.
- **Mandatory Local Verification:**
  ```powershell
  ./gradlew test
  ```
