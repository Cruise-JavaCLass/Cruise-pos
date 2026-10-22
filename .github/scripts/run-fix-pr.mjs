#!/usr/bin/env node

/**
 * Jules PR Auto-Fixer Runner (SonarCloud / CodeRabbit / CI Failure)
 * 
 * Fetches failure details, logs, or review comments on a PR and creates a Jules session
 * targeting the PR's head branch to self-heal the pull request.
 */

import {
  discoverSource,
  createSession,
  writeStepSummary,
  sanitizeLog,
  truncateLog,
  redactSensitiveData
} from './jules-client.mjs';

async function main() {
  console.log('=== Starting Google Jules PR Self-Healing Auto-Fixer ===');

  const apiKey = process.env.JULES_API_KEY;
  if (!apiKey || !apiKey.trim()) {
    throw new Error('JULES_API_KEY is not configured or empty. Please set this GitHub secret.');
  }

  const repository = process.env.GITHUB_REPOSITORY || '';
  const [repoOwner, repoName] = repository.split('/');
  if (!repoOwner || !repoName) {
    throw new Error(`Invalid GITHUB_REPOSITORY format: "${repository}". Expected "owner/repo".`);
  }

  const prNumber = process.env.PR_NUMBER;
  const prBranch = process.env.PR_BRANCH;
  const failureType = process.env.FAILURE_TYPE || 'quality-check';
  const rawDetails = process.env.FAILURE_DETAILS || 'Automated quality checks failed on this PR.';
  const prUrl = process.env.PR_URL || `https://github.com/${repository}/pull/${prNumber}`;

  if (!prBranch) {
    throw new Error('PR_BRANCH environment variable is missing.');
  }

  const details = truncateLog(redactSensitiveData(rawDetails, [process.env.SONAR_TOKEN]));

  console.log(sanitizeLog(`Repository: ${repoOwner}/${repoName}`));
  console.log(sanitizeLog(`PR #${prNumber} on branch: ${prBranch}`));
  console.log(sanitizeLog(`Trigger Type: ${failureType}`));

  // Step 1: Discover connected Jules source
  console.log('Discovering connected Jules source via Sources API...');
  const { sourceName } = await discoverSource(apiKey, repoOwner, repoName);
  console.log(sanitizeLog(`Discovered source: "${sourceName}"`));

  // Step 2: Build prompt targeting the PR branch
  const prompt = `You are the autonomous senior AI repository maintainer for "${repoName}".

### Task: Self-Heal PR #${prNumber} on branch \`${prBranch}\`
**PR Link:** ${prUrl}
**Failure Type / Findings:** ${failureType}

**Report / Failure Details:**
\`\`\`
${details}
\`\`\`

### Resolution Instructions:
1. **Analyze Failure Context:** Identify specifically what failed in SonarCloud Quality Gate, CodeRabbit code review, or the build/test suite.
2. **Implement Targeted Fix:** Checkout \`${prBranch}\` and modify the code cleanly to resolve all cited issues, security flags, or code smells without regressions.
3. **Run Verification:** Execute project tests (\`./mvnw test\`) to verify that all unit/integration tests pass and code compiles cleanly.
4. **Push to PR:** Commit and push directly to the branch \`${prBranch}\` so the PR checks re-trigger and pass.`;

  const sessionTitle = `Auto-Fix PR #${prNumber} (${failureType}): ${prBranch.slice(0, 40)}`;

  // Step 3: Create Jules session on the PR branch
  console.log(`Creating Jules session targeting startingBranch: ${prBranch}...`);
  const session = await createSession(apiKey, {
    prompt,
    title: sessionTitle,
    sourceName,
    startingBranch: prBranch,
    requirePlanApproval: false,
    automationMode: 'AUTO_CREATE_PR'
  });

  console.log(sanitizeLog(`Successfully created Jules session! ID: ${session.id || session.name}`));
  const sessionUrl = session.url || `https://jules.google.com/session/${session.id || ''}`;

  writeStepSummary(`### 🛠️ Google Jules PR Self-Healing Triggered

| Parameter | Value |
| :--- | :--- |
| **Pull Request** | [#${prNumber}](${prUrl}) |
| **Target Branch** | \`${prBranch}\` |
| **Issue Type** | \`${failureType}\` |
| **Session ID** | \`${session.id || session.name}\` |
| **Jules Console** | [Open in Jules](${sessionUrl}) |

*Jules is actively resolving the SonarCloud/CodeRabbit/CI findings directly on \`${prBranch}\`.*
`);
}

main().catch(err => {
  console.error(sanitizeLog(`Error executing Jules PR fixer: ${err.message}`));
  writeStepSummary(`### ❌ Jules PR Auto-Fixer Failed
\`\`\`
${err.message}
\`\`\`
`);
  process.exit(1);
});
