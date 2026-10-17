# Contributing to Cruise Activity & Service Management System (POS / Mobile)

Thank you for contributing! All team members and AI coding assistants must adhere to the engineering standards described here.

---

## 1. Jira Ticket & Subtask Requirements

- **Jira Instance:** `https://huytran20052022.atlassian.net/` | Project Key: `KAN`
- All POS & offline sync features must map to a Jira Story/Task.
- Jira issues must only be moved to `Done` after PR approval, passing CI, and merge to `main`.

---

## 2. Git Workflow & Branching

- We use **GitHub Flow**.
- Branch naming convention:
  - `feat/KAN-xxx-...` (Feature)
  - `fix/KAN-xxx-...` (Bug fix)
  - `test/KAN-xxx-...` (Testing)
  - `chore/KAN-xxx-...` (CI/Build/Dependencies)
- **Direct push to `main` is strictly forbidden.**

---

## 3. Commit & Pull Request Guidelines

- Commit message structure:
  ```
  [KAN-xxx] <type>(<scope>): <imperative summary>
  ```
- PR Title format: `[KAN-xxx] type: concise description`
- Fill out all required sections in `.github/PULL_REQUEST_TEMPLATE.md`.
- Automated GitHub Actions validate the title and body format on every PR.
