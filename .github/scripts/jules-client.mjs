/**
 * Jules Autonomous Maintainer Client for Cruise-pos (Android)
 * 
 * Provides integration between GitHub Actions and Google Jules REST API:
 * 1. Discovering connected source repositories via Jules Sources API.
 * 2. Session deduplication to prevent repetitive runs and automation loops.
 * 3. Sanitizing and bounding log output to prevent credential leakage.
 * 4. Constructing scoped Android/Kotlin audit prompts and CI failure repair prompts.
 * 5. Creating Jules sessions with AUTO_CREATE_PR mode.
 */

import { appendFileSync } from 'node:fs';

export const JULES_API_BASE = 'https://jules.googleapis.com/v1alpha';

/**
 * Redacts sensitive tokens, credentials, and secrets from text.
 * @param {string} text 
 * @param {string[]} extraSecrets 
 * @returns {string}
 */
export function redactSensitiveData(text, extraSecrets = []) {
  if (!text || typeof text !== 'string') return '';

  let sanitized = text;

  for (const secret of extraSecrets) {
    if (secret && typeof secret === 'string' && secret.trim().length >= 4) {
      const escaped = secret.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      sanitized = sanitized.replace(new RegExp(escaped, 'g'), '[REDACTED_SECRET]');
    }
  }

  sanitized = sanitized.replace(/AIza[0-9A-Za-z\-_]{35}/g, '[REDACTED_GOOGLE_API_KEY]');
  sanitized = sanitized.replace(/ghp_[0-9A-Za-z]{36,}/g, '[REDACTED_GITHUB_TOKEN]');
  sanitized = sanitized.replace(/gho_[0-9A-Za-z]{36,}/g, '[REDACTED_GITHUB_OAUTH]');
  sanitized = sanitized.replace(/github_pat_[0-9A-Za-z_]{70,}/g, '[REDACTED_GITHUB_PAT]');
  sanitized = sanitized.replace(/(Bearer\s+)[A-Za-z0-9\-_.~+/=]{16,}/gi, '$1[REDACTED_BEARER_TOKEN]');
  sanitized = sanitized.replace(/(Basic\s+)[A-Za-z0-9+/=]{16,}/gi, '$1[REDACTED_BASIC_AUTH]');

  sanitized = sanitized.replace(
    /(["']?(?:password|passwd|secret|api[_-]?key|access[_-]?token|keystore_password|key_password)["']?\s*[:=]\s*["']?)(?!\[REDACTED)([^"'\s\n]{4,})(["']?)/gi,
    '$1[REDACTED_VALUE]$3'
  );

  return sanitized;
}

/**
 * Truncates text to a maximum line count and byte length while preserving failure context.
 * @param {string} text 
 * @param {number} maxLines 
 * @param {number} maxBytes 
 * @returns {string}
 */
export function truncateLog(text, maxLines = 250, maxBytes = 25000) {
  if (!text) return '';
  const lines = text.split('\n');
  let selectedLines = lines;

  if (lines.length > maxLines) {
    const headCount = Math.min(30, Math.floor(maxLines * 0.15));
    const tailCount = maxLines - headCount - 1;
    selectedLines = [
      ...lines.slice(0, headCount),
      `\n... [TRUNCATED ${lines.length - maxLines} LINES OF INTERMEDIATE LOGS] ...\n`,
      ...lines.slice(-tailCount)
    ];
  }

  let result = selectedLines.join('\n');
  if (Buffer.byteLength(result, 'utf8') > maxBytes) {
    result = result.slice(-maxBytes);
    result = `[TRUNCATED BYTES]...\n` + result;
  }

  return result;
}

/**
 * Discovers the connected Jules source corresponding to the GitHub repository.
 * Uses official Sources API: GET https://jules.googleapis.com/v1alpha/sources
 * 
 * @param {string} apiKey 
 * @param {string} repoOwner 
 * @param {string} repoName 
 * @param {object} [options] 
 * @returns {Promise<{ sourceName: string, defaultBranch: string, rawSource: object }>}
 */
export async function discoverSource(apiKey, repoOwner, repoName, options = {}) {
  const baseUrl = options.baseUrl || JULES_API_BASE;
  const url = `${baseUrl}/sources?pageSize=100`;

  let response;
  try {
    response = await (options.fetchFn || fetch)(url, {
      method: 'GET',
      headers: {
        'X-Goog-Api-Key': apiKey,
        'Content-Type': 'application/json'
      }
    });
  } catch (err) {
    throw new Error(`Failed to connect to Jules Sources API at ${url}: ${err.message}`);
  }

  if (response.status === 401 || response.status === 403) {
    throw new Error(`Jules API Authentication failed (HTTP ${response.status}). Verify that JULES_API_KEY secret is valid and has Jules API permissions.`);
  }

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Jules Sources API returned HTTP ${response.status}: ${errorText}`);
  }

  const data = await response.json();
  const sources = data.sources || [];

  const targetOwner = repoOwner.toLowerCase().trim();
  const targetRepo = repoName.toLowerCase().trim();

  const matched = sources.find(s => {
    const gh = s.githubRepo;
    if (!gh) return false;
    return (
      (gh.owner || '').toLowerCase().trim() === targetOwner &&
      (gh.repo || '').toLowerCase().trim() === targetRepo
    );
  });

  if (!matched) {
    const available = sources.map(s => {
      const gh = s.githubRepo;
      return gh ? `${gh.owner}/${gh.repo} (${s.name})` : s.name;
    });

    throw new Error(
      `No connected Jules source matches repository "${repoOwner}/${repoName}".\n` +
      `Available connected sources (${sources.length}): ${available.length ? available.join(', ') : 'None'}.\n` +
      `Ensure the repository is connected to Jules via the web interface at https://jules.google.`
    );
  }

  const defaultBranch = matched.githubRepo?.defaultBranch?.displayName || 'main';

  return {
    sourceName: matched.name,
    defaultBranch,
    rawSource: matched
  };
}

/**
 * Checks existing sessions from the Jules API to prevent duplicate sessions.
 * 
 * @param {string} apiKey 
 * @param {object} criteria 
 * @param {object} [options]
 * @returns {Promise<{ isDuplicate: boolean, existingSession: object|null }>}
 */
export async function checkDuplicateSession(apiKey, criteria, options = {}) {
  const baseUrl = options.baseUrl || JULES_API_BASE;
  const url = `${baseUrl}/sessions?pageSize=50`;

  let response;
  try {
    response = await (options.fetchFn || fetch)(url, {
      method: 'GET',
      headers: {
        'X-Goog-Api-Key': apiKey,
        'Content-Type': 'application/json'
      }
    });
  } catch (err) {
    console.warn(`Warning: Could not fetch existing sessions for deduplication: ${err.message}`);
    return { isDuplicate: false, existingSession: null };
  }

  if (!response.ok) {
    console.warn(`Warning: Sessions API returned HTTP ${response.status} during deduplication check.`);
    return { isDuplicate: false, existingSession: null };
  }

  const data = await response.json();
  const sessions = data.sessions || [];

  const { runId, commitSha, auditType, maxAgeHours = 48 } = criteria;
  const cutoffTime = Date.now() - (maxAgeHours * 60 * 60 * 1000);

  for (const s of sessions) {
    const createdTime = s.createTime ? new Date(s.createTime).getTime() : 0;
    if (createdTime < cutoffTime) continue;

    const title = (s.title || '').toLowerCase();
    const prompt = (s.prompt || '').toLowerCase();

    if (runId && (title.includes(`run ${runId}`) || prompt.includes(`run id: ${runId}`) || prompt.includes(`run-id: ${runId}`))) {
      return { isDuplicate: true, existingSession: s };
    }

    if (commitSha && (prompt.includes(commitSha.toLowerCase())) && ['QUEUED', 'IN_PROGRESS', 'PLANNING'].includes(s.state)) {
      return { isDuplicate: true, existingSession: s };
    }

    if (auditType && title.includes('weekly repository audit') && ['QUEUED', 'IN_PROGRESS', 'COMPLETED'].includes(s.state)) {
      return { isDuplicate: true, existingSession: s };
    }
  }

  return { isDuplicate: false, existingSession: null };
}

/**
 * Creates a Jules session using the official Sessions API:
 * POST https://jules.googleapis.com/v1alpha/sessions
 * 
 * @param {string} apiKey 
 * @param {object} payload 
 * @param {object} [options]
 * @returns {Promise<object>}
 */
export async function createSession(apiKey, payload, options = {}) {
  const baseUrl = options.baseUrl || JULES_API_BASE;
  const url = `${baseUrl}/sessions`;

  const requestBody = {
    prompt: payload.prompt,
    title: payload.title || 'Repository Maintenance Task',
    sourceContext: {
      source: payload.sourceName,
      githubRepoContext: {
        startingBranch: payload.startingBranch || 'main'
      }
    },
    requirePlanApproval: payload.requirePlanApproval ?? false,
    automationMode: payload.automationMode || 'AUTO_CREATE_PR'
  };

  let response;
  try {
    response = await (options.fetchFn || fetch)(url, {
      method: 'POST',
      headers: {
        'X-Goog-Api-Key': apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody)
    });
  } catch (err) {
    throw new Error(`Failed to send request to Jules Sessions API: ${err.message}`);
  }

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`Jules Sessions API error HTTP ${response.status}: ${errorBody}`);
  }

  return await response.json();
}

/**
 * Generates prompt for weekly / on-demand Android POS repository audit.
 * 
 * @param {object} params
 * @returns {string}
 */
export function generateAuditPrompt({ scope = 'full', defaultBranch = 'main', repoName = 'Cruise-pos' }) {
  const scopeDescriptions = {
    bugs: 'Detect potential Android runtime bugs, Kotlin NullPointerExceptions, Coroutine scope leaks, ViewModel state race conditions, and unhandled offline transaction states.',
    tests: 'Identify untested ViewModels, use cases, repository layers, and missing JUnit 4/5 unit tests for offline transaction persistence and calculation rules.',
    security: 'Inspect for insecure local storage of payment/transaction data, missing certificate pinning, hardcoded secrets, and Android manifest export vulnerabilities.',
    'code-quality': 'Audit for Jetpack Compose recomposition inefficiencies, Kotlin idioms, Clean Architecture separation of concerns, and Gradle dependency best practices.',
    documentation: 'Verify that README architecture, POS hardware compatibility, and API gateway URLs match the actual source implementation.',
    full: 'Perform a comprehensive Android repository audit balancing correctness, unit test coverage, Kotlin idioms, offline reliability, and Jetpack Compose best practices.'
  };

  const focus = scopeDescriptions[scope] || scopeDescriptions.full;

  return `You are acting as the autonomous senior AI repository maintainer for the Android POS project "${repoName}".

### Audit Scope: ${scope.toUpperCase()}
${focus}

### Project Architecture & Conventions:
- **Platform:** Android (API Level 24+ / targetSdk 36).
- **Language:** Kotlin with Jetpack Compose & Material 3.
- **Build System:** Gradle (Kotlin DSL \`build.gradle.kts\`).
- **Verification Commands:**
  - Unit tests: \`./gradlew test --stacktrace\`
  - Check / Lint: \`./gradlew check\`

### Maintainer Guidelines & Instructions:
1. **Analyze First:** Inspect \`app/src/main/java/\`, Jetpack Compose UI, ViewModels, and existing unit tests before making changes.
2. **Select Exactly ONE Task:** Choose ONE well-scoped, high-value problem that provides tangible value. Do NOT attempt to refactor the entire Android project.
3. **High Value Priority:** Prioritize reproducible bugs, missing unit tests for transaction logic, unhandled network timeouts, or Coroutine cancellation bugs over cosmetic styling.
4. **Implement & Test:** Implement the change cleanly in Kotlin. Add or update JUnit unit tests to verify behavior.
5. **Run Verification Commands:** Run \`./gradlew test\` to ensure all unit tests compile and pass. Report actual test outcomes.
6. **No Speculative Changes:** Avoid adding heavy third-party libraries without necessity.
7. **Create a Pull Request:**
   - Automation Mode: AUTO_CREATE_PR.
   - Target starting branch: \`${defaultBranch}\`.
   - Provide a structured description detailing problem statement, changes made, and test results.`;
}

/**
 * Generates prompt for investigating and repairing Android CI failure.
 * 
 * @param {object} params
 * @returns {string}
 */
export function generateFixCiPrompt({
  runUrl,
  runId,
  commitSha,
  branch,
  failedJobs = [],
  failureSummary = ''
}) {
  const jobsList = failedJobs.map(j => `- Job: "${j.name}" (Failed Step: ${j.failedStep || 'Unknown'})`).join('\n');

  return `You are acting as the autonomous AI repository maintainer investigating a failed Android CI run on "${branch}".

### Failed CI Run Metadata:
- **Originating Run URL:** ${runUrl}
- **Run ID:** ${runId}
- **Target Branch:** ${branch}
- **Commit SHA:** ${commitSha}

### Failed Jobs & Steps:
${jobsList || '- Details in diagnostic log below'}

### Sanitized Diagnostic Log Excerpt:
\`\`\`
${failureSummary || 'No log details available.'}
\`\`\`

### Instructions:
1. **Root Cause Analysis:** Inspect the repository at commit \`${commitSha}\` and identify why Gradle compilation, Kotlin compiler, or Android unit tests failed.
2. **Determine Fix Feasibility:**
   - **Infrastructure / Missing SDK / External Outage:** If caused by an unavailable Gradle plugin portal or runner issue, report the diagnosis rather than making code changes.
   - **Code / Test / Gradle Failure:** Implement the minimal targeted fix.
3. **Implement Minimal Fix:** Fix the issue in \`build.gradle.kts\` or Kotlin source files and ensure regression tests pass.
4. **Verify Locally:** Run \`./gradlew test\` to verify that compilation and tests succeed.
5. **Create Pull Request:**
   - Automation Mode: AUTO_CREATE_PR.
   - Explain the root cause, what was changed, and include the test results in the PR description referencing Run ID \`${runId}\`.`;
}

/**
 * Determines whether a workflow event qualifies for automatic CI repair.
 * 
 * @param {object} event 
 * @param {string} defaultBranch 
 * @returns {{ shouldTrigger: boolean, reason: string }}
 */
export function shouldTriggerCiRepair(event, defaultBranch = 'main') {
  if (!event) return { shouldTrigger: false, reason: 'No event provided' };

  if (event.eventName === 'workflow_dispatch') {
    return { shouldTrigger: true, reason: 'Manual workflow_dispatch trigger' };
  }

  if (event.eventName !== 'workflow_run') {
    return { shouldTrigger: false, reason: `Ignored event: ${event.eventName}` };
  }

  const run = event.workflowRun || {};
  if (run.name !== 'Android CI Pipeline') {
    return { shouldTrigger: false, reason: `Ignored workflow: "${run.name}" (only "Android CI Pipeline" is monitored)` };
  }

  if (run.conclusion !== 'failure') {
    return { shouldTrigger: false, reason: `Ignored conclusion: "${run.conclusion}" (only "failure" is repaired)` };
  }

  if (run.headBranch !== defaultBranch) {
    return { shouldTrigger: false, reason: `Ignored branch: "${run.headBranch}" (only default branch "${defaultBranch}" is auto-repaired)` };
  }

  return { shouldTrigger: true, reason: 'Eligible failure on default branch' };
}

/**
 * Writes step summary to GitHub Actions environment file ($GITHUB_STEP_SUMMARY).
 * @param {string} markdown 
 */
export function writeStepSummary(markdown) {
  const summaryFile = process.env.GITHUB_STEP_SUMMARY;
  if (summaryFile) {
    try {
      appendFileSync(summaryFile, markdown + '\n', 'utf8');
    } catch (err) {
      console.warn('Could not write to GITHUB_STEP_SUMMARY:', err.message);
    }
  }
}
