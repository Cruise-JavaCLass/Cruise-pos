# Engineering Standards & Governance Policy

This policy outlines the official working rules for Jira, Git, GitHub Pull Requests, GitHub Actions, testing, and AI-assisted development for the **Cruise Activity and Service Management System**.

---

## 1. Jira Task & Subtask Rules

### Hierarchy
- **Epic:** Major business capability (e.g. Shore Excursion Management, POS Cashless System, Passenger Web Portal).
- **Task / Story:** Deliverable functional unit (e.g. `KAN-185`: Implement Excursion Status Tracking).
- **Subtask:** Specific, actionable implementation or test step.

### Subtask Structure for Stories
When breaking down a story, define relevant subtasks:
1. **Implementation:** Backend entity/DTO/service or Frontend UI components.
2. **Unit Testing:** Business rules, status transitions, or component tests.
3. **Integration Testing:** API endpoints, MockMvc, or E2E flows where applicable.
4. **Documentation & Validation:** OpenAPI/Swagger docs or validation suites when necessary.

### Issue Lifecycle & Status Transitions
- `To Do` -> `In Progress` (when active branch and implementation starts).
- `In Progress` -> `In Review` (when Pull Request is created and CI is running).
- `In Review` -> `Done` (ONLY after PR is approved, passes all CI status checks, and is squashed/merged into `main`).
- **Never mark Done prematurely** while PR is still open or checks are pending.

---

## 2. Git Branching & Protection Rules

### Branch Standards
- `feat/KAN-xxx-short-description`
- `fix/KAN-xxx-short-description`
- `test/KAN-xxx-short-description`
- `docs/KAN-xxx-short-description`
- `refactor/KAN-xxx-short-description`
- `chore/KAN-xxx-short-description`

### Branch Protection on `main`
- Direct commits to `main` are disabled.
- Require Pull Request before merging.
- Require linear history via **Squash and merge**.

---

## 3. Commit Standards

- Format: `[KAN-xxx] <type>(<scope>): <concise description>`
- Conventional types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `perf`, `ci`.
- Keep commits focused and atomic.
- Never commit secrets, credentials, or local environment files.

---

## 4. Pull Request Standards & Automation

### PR Title
- Must match: `[KAN-xxx] type: concise description`
- Example: `[KAN-185] feat: implement pos offline transaction storage`

### PR Description
Must adhere to `.github/PULL_REQUEST_TEMPLATE.md`:
- `### Summary`: 1–3 clear sentences.
- `### Changes`: Bullet list of modifications.
- `### Testing`: Real test executions and exact counts/results.
- `### Jira`: Direct link to Jira issue (`https://huytran20052022.atlassian.net/browse/KAN-xxx`).
- `### Notes`: Architectural context, migrations, or `None`.

### Validation Workflow (`pr-validation.yml`)
Runs automatically on every PR open/edit/synchronize to check:
1. Title adheres to regex.
2. Body does not contain literal `\n` or `\\n` escape sequences.
3. Required headings are present.
4. Jira issue key is present and not an unresolved placeholder like `KAN-XXX`.
