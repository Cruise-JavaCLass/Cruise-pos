#!/usr/bin/env node

/**
 * Jules Automatic Android CI Failure Investigation & Repair Runner
 */

import {
  discoverSource,
  checkDuplicateSession,
  generateFixCiPrompt,
  createSession,
  redactSensitiveData,
  truncateLog,
  writeStepSummary
} from './jules-client.mjs';

async function fetchFailedJobsAndLogs(githubToken, owner, repo, runId) {
  const headers = {
    'Authorization': `Bearer ${githubToken}`,
    'Accept': 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'jules-autonomous-maintainer-pos'
  };

  const jobsUrl = `https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}/jobs?filter=latest`;
  const jobsRes = await fetch(jobsUrl, { headers });

  if (!jobsRes.ok) {
    const txt = await jobsRes.text();
    console.warn(`Could not retrieve jobs for run ${runId} (HTTP ${jobsRes.status}): ${txt}`);
    return { failedJobs: [], combinedLogs: 'No log output could be retrieved from GitHub Actions API.' };
  }

  const jobsData = await jobsRes.json();
  const failedJobs = [];
  const logSections = [];

  for (const job of (jobsData.jobs || [])) {
    if (job.conclusion === 'failure') {
      const failedStep = (job.steps || []).find(s => s.conclusion === 'failure');
      const stepName = failedStep ? failedStep.name : 'Unknown step';

      failedJobs.push({
        id: job.id,
        name: job.name,
        failedStep: stepName,
        htmlUrl: job.html_url
      });

      try {
        const logUrl = `https://api.github.com/repos/${owner}/${repo}/actions/jobs/${job.id}/logs`;
        const logRes = await fetch(logUrl, { headers, redirect: 'follow' });
        if (logRes.ok) {
          const rawLog = await logRes.text();
          logSections.push(`=== Job: ${job.name} (Failed Step: ${stepName}) ===\n${rawLog}`);
        } else {
          logSections.push(`=== Job: ${job.name} ===\nFailed to fetch logs (HTTP ${logRes.status})`);
        }
      } catch (err) {
        logSections.push(`=== Job: ${job.name} ===\nError fetching log: ${err.message}`);
      }
    }
  }

  return {
    failedJobs,
    combinedLogs: logSections.join('\n\n')
  };
}

async function main() {
  console.log('=== Starting Google Jules Android CI Failure Investigation ===');

  const apiKey = process.env.JULES_API_KEY;
  if (!apiKey || !apiKey.trim()) {
    throw new Error('JULES_API_KEY is not configured or empty. Please set this GitHub secret.');
  }

  const githubToken = process.env.GITHUB_TOKEN;
  if (!githubToken || !githubToken.trim()) {
    throw new Error('GITHUB_TOKEN is not available. Please pass secrets.GITHUB_TOKEN to the workflow step.');
  }

  const repository = process.env.GITHUB_REPOSITORY || '';
  const [repoOwner, repoName] = repository.split('/');
  if (!repoOwner || !repoName) {
    throw new Error(`Invalid GITHUB_REPOSITORY format: "${repository}".`);
  }

  const runId = process.env.RUN_ID;
  if (!runId) {
    throw new Error('RUN_ID is required to investigate a CI failure.');
  }

  const runUrl = process.env.RUN_URL || `https://github.com/${repository}/actions/runs/${runId}`;
  const commitSha = process.env.HEAD_SHA || 'HEAD';
  const branch = process.env.HEAD_BRANCH || 'main';
  const defaultBranch = process.env.DEFAULT_BRANCH || 'main';

  console.log(`Failed Run ID: ${runId}`);
  console.log(`Run URL: ${runUrl}`);
  console.log(`Commit SHA: ${commitSha}`);
  console.log(`Branch: ${branch} (Default: ${defaultBranch})`);

  // Step 1: Safety & Deduplication check
  console.log('Checking for existing Jules repair sessions for this run...');
  const { isDuplicate, existingSession } = await checkDuplicateSession(apiKey, {
    runId,
    commitSha,
    maxAgeHours: 48
  });

  if (isDuplicate && existingSession) {
    const msg = `Skipping repair. An existing Jules session "${existingSession.title || existingSession.name}" already exists (${existingSession.state}) for run ID ${runId}.`;
    console.log(msg);
    writeStepSummary(`### ⏭️ Jules Android CI Repair Skipped
- **Reason:** Duplicate session already exists for this CI failure.
- **Existing Session:** [${existingSession.title || existingSession.id}](${existingSession.url || '#'})
- **State:** \`${existingSession.state}\`
- **Run ID:** \`${runId}\`
`);
    return;
  }

  // Step 2: Retrieve failure logs from GitHub
  console.log(`Retrieving failed job details and logs for run ${runId}...`);
  const { failedJobs, combinedLogs } = await fetchFailedJobsAndLogs(githubToken, repoOwner, repoName, runId);
  console.log(`Identified ${failedJobs.length} failed job(s).`);

  // Step 3: Redact sensitive information & truncate
  const extraSecrets = [apiKey, githubToken].filter(Boolean);
  const redactedLogs = redactSensitiveData(combinedLogs, extraSecrets);
  const boundedLogs = truncateLog(redactedLogs, 250, 25000);

  // Step 4: Discover connected Jules source
  console.log('Discovering connected Jules source via Sources API...');
  const { sourceName } = await discoverSource(apiKey, repoOwner, repoName);
  console.log(`Discovered source: "${sourceName}"`);

  // Step 5: Construct CI repair prompt
  console.log('Generating CI repair prompt...');
  const prompt = generateFixCiPrompt({
    runUrl,
    runId,
    commitSha,
    branch,
    failedJobs,
    failureSummary: boundedLogs
  });

  const title = `Fix Android CI Failure: Run ${runId} (${branch} @ ${commitSha.substring(0, 7)})`;

  // Step 6: Create Jules session
  console.log(`Creating Jules repair session with automationMode: AUTO_CREATE_PR...`);
  const session = await createSession(apiKey, {
    prompt,
    title,
    sourceName,
    startingBranch: branch,
    requirePlanApproval: false,
    automationMode: 'AUTO_CREATE_PR'
  });

  console.log(`Successfully created Jules repair session! ID: ${session.id || session.name}`);
  const sessionUrl = session.url || `https://jules.google.com/session/${session.id || ''}`;
  console.log(`Session URL: ${sessionUrl}`);

  // Step 7: Output Step Summary
  const failedJobsSummary = failedJobs.map(j => `- **${j.name}** (Failed step: \`${j.failedStep}\`)`).join('\n') || 'None recorded';

  writeStepSummary(`### 🛠️ Google Jules Android CI Failure Investigation Triggered

| Parameter | Value |
| :--- | :--- |
| **Session Title** | \`${title}\` |
| **Session ID** | \`${session.id || session.name}\` |
| **Status** | \`${session.state || 'QUEUED'}\` |
| **Failed Run URL** | [View Actions Run](${runUrl}) |
| **Commit SHA** | \`${commitSha}\` |
| **Branch** | \`${branch}\` |
| **Jules Session Link** | [Open in Jules Console](${sessionUrl}) |

#### Failed Jobs Detected:
${failedJobsSummary}

*Jules will inspect Android build/test logs, fix the failure, verify with \`./gradlew test\`, and open a Pull Request.*
`);
}

main().catch(err => {
  console.error(`Error during Jules Android CI repair: ${err.message}`);
  writeStepSummary(`### ❌ Jules Android CI Repair Launch Failed
\`\`\`
${err.message}
\`\`\`
`);
  process.exit(1);
});
