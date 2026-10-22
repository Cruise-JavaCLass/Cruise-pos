#!/usr/bin/env node

/**
 * Jules Weekly / On-Demand Repository Audit Runner
 */

import {
  discoverSource,
  checkDuplicateSession,
  generateAuditPrompt,
  createSession,
  writeStepSummary,
  sanitizeLog
} from './jules-client.mjs';

/**
 * Main entrypoint for launching scheduled or on-demand Google Jules repository audits.
 * @returns {Promise<void>}
 */
async function main() {
  console.log('=== Starting Google Jules Repository Audit ===');

  const apiKey = process.env.JULES_API_KEY;
  if (!apiKey || !apiKey.trim()) {
    throw new Error('JULES_API_KEY is not configured or empty. Please set this GitHub secret.');
  }

  const repository = process.env.GITHUB_REPOSITORY || '';
  const [repoOwner, repoName] = repository.split('/');
  if (!repoOwner || !repoName) {
    throw new Error(`Invalid GITHUB_REPOSITORY format: "${repository}". Expected "owner/repo".`);
  }

  const scope = (process.env.AUDIT_SCOPE || 'full').toLowerCase().trim();
  const defaultBranch = process.env.DEFAULT_BRANCH || 'main';

  console.log(sanitizeLog(`Repository: ${repoOwner}/${repoName}`));
  console.log(sanitizeLog(`Audit Scope: ${scope}`));
  console.log(sanitizeLog(`Target Branch: ${defaultBranch}`));

  // Step 1: Discover connected Jules source
  console.log('Discovering connected Jules source via Sources API...');
  const { sourceName, defaultBranch: discoveredBranch } = await discoverSource(apiKey, repoOwner, repoName);
  const startingBranch = defaultBranch || discoveredBranch || 'main';
  console.log(sanitizeLog(`Discovered source: "${sourceName}" (Starting branch: "${startingBranch}")`));

  // Step 2: Check for duplicate audit in the last 20 hours (daily cadence)
  console.log('Checking for recent duplicate audit sessions...');
  const { isDuplicate, existingSession } = await checkDuplicateSession(apiKey, {
    auditType: scope,
    maxAgeHours: 20
  });

  if (isDuplicate && existingSession && process.env.FORCE_AUDIT !== 'true') {
    const msg = `Skipping duplicate audit session. A recent session "${existingSession.title || existingSession.name}" already exists (${existingSession.state}) created at ${existingSession.createTime}. Set FORCE_AUDIT=true to override.`;
    console.log(sanitizeLog(msg));
    writeStepSummary(`### ⏭️ Jules Repository Audit Skipped
- **Reason:** Duplicate session detected within the last 20 hours.
- **Existing Session:** [${existingSession.title || existingSession.id}](${existingSession.url || '#'})
- **State:** \`${existingSession.state}\`
- **Created At:** ${existingSession.createTime}
`);
    return;
  }

  // Step 3: Construct prompt
  console.log('Generating audit prompt...');
  const prompt = generateAuditPrompt({ scope, defaultBranch: startingBranch, repoName });
  const title = `Daily Repository Audit: ${scope.toUpperCase()} (${new Date().toISOString().split('T')[0]})`;

  // Step 4: Create Jules session
  console.log(`Creating Jules session with automationMode: AUTO_CREATE_PR...`);
  const session = await createSession(apiKey, {
    prompt,
    title,
    sourceName,
    startingBranch,
    requirePlanApproval: false,
    automationMode: 'AUTO_CREATE_PR'
  });

  console.log(sanitizeLog(`Successfully created Jules session! ID: ${session.id || session.name}`));
  const sessionUrl = session.url || `https://jules.google.com/session/${session.id || ''}`;
  console.log(sanitizeLog(`Session URL: ${sessionUrl}`));

  // Step 5: Output GitHub Step Summary
  writeStepSummary(`### 🚀 Google Jules Repository Audit Launched

| Parameter | Value |
| :--- | :--- |
| **Session Title** | \`${title}\` |
| **Session ID** | \`${session.id || session.name}\` |
| **Status** | \`${session.state || 'QUEUED'}\` |
| **Audit Scope** | \`${scope}\` |
| **Target Branch** | \`${startingBranch}\` |
| **Jules Source** | \`${sourceName}\` |
| **Session Link** | [Open in Jules Console](${sessionUrl}) |

*Jules will inspect the repository, propose targeted improvements, run project tests (\`./mvnw test\`), and open a Pull Request when ready.*
`);
}

main().catch(err => {
  console.error(sanitizeLog(`Error executing Jules audit: ${err.message}`));
  writeStepSummary(`### ❌ Jules Audit Execution Failed
\`\`\`
${err.message}
\`\`\`
`);
  process.exit(1);
});
