#!/usr/bin/env node

/**
 * Jules Automatic CI Failure Investigation & Repair Runner
 */

import {
  discoverSource,
  checkDuplicateSession,
  generateFixCiPrompt,
  createSession,
  redactSensitiveData,
  truncateLog,
  writeStepSummary,
  sanitizeLog
} from './jules-client.mjs';

/**
 * Fetches failed jobs, step information, and logs for a specific GitHub Actions workflow run.
 * 
 * @param {string} githubToken - GitHub Personal Access Token or Actions Token.
 * @param {string} owner - Repository owner.
 * @param {string} repo - Repository name.
 * @param {string|number} runId - GitHub Actions workflow run ID.
 * @returns {Promise<{ failedJobs: Array<{ id: number, name: string, failedStep: string, htmlUrl: string }>, combinedLogs: string }>}
 */
async function fetchFailedJobsAndLogs(githubToken, owner, repo, runId) {
  const headers = {
    'Authorization': `Bearer ${githubToken}`,
    'Accept': 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'jules-autonomous-maintainer'
  };

  let nextUrl = `https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}/jobs?filter=latest&per_page=100`;
  const allJobs = [];
  const logSections = [];

  while (nextUrl) {
    const jobsRes = await fetch(nextUrl, { headers });

    if (!jobsRes.ok) {
      const txt = await jobsRes.text();
      console.warn(sanitizeLog(`Could not retrieve jobs page for run ${runId} (HTTP ${jobsRes.status}): ${txt}`));
      logSections.push(`[WARNING] Failed to fetch jobs page (${nextUrl}): HTTP ${jobsRes.status} - ${txt}`);
      break;
    }

    const jobsData = await jobsRes.json();
    if (Array.isArray(jobsData.jobs)) {
      allJobs.push(...jobsData.jobs);
    }

    // Check Link header for rel="next"
    const linkHeader = jobsRes.headers.get('link') || '';
    const nextMatch = linkHeader.match(/<([^>]+)>;\s*rel="next"/i);
    nextUrl = nextMatch ? nextMatch[1] : null;
  }

  const failedJobs = [];

  for (const job of allJobs) {
    if (job.conclusion === 'failure') {
      const failedStep = (job.steps || []).find(s => s.conclusion === 'failure');
      const stepName = failedStep ? failedStep.name : 'Unknown step';

      failedJobs.push({
        id: job.id,
        name: job.name,
        failedStep: stepName,
        htmlUrl: job.html_url
      });

      // Fetch logs for failed job
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
    combinedLogs: logSections.length > 0 ? logSections.join('\n\n') : 'No failed job logs available.'
  };
}

/**
 * Main entrypoint for investigating CI failures and dispatching a repair task to Google Jules.
 * @returns {Promise<void>}
 */
async function main() {
  console.log('=== Starting Google Jules CI Failure Investigation ===');

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
  let commitSha = process.env.HEAD_SHA;
  let branch = process.env.HEAD_BRANCH;
  const defaultBranch = process.env.DEFAULT_BRANCH || 'main';

  const isWorkflowDispatch = process.env.GITHUB_EVENT_NAME === 'workflow_dispatch';

  // If commitSha or branch is missing, or event was workflow_dispatch, query GitHub API to get accurate failed run metadata
  if (!commitSha || !branch || isWorkflowDispatch || commitSha === 'HEAD') {
    try {
      console.log(`Fetching workflow run details for run ${runId} from GitHub API...`);
      const runRes = await fetch(`https://api.github.com/repos/${repoOwner}/${repoName}/actions/runs/${runId}`, {
        headers: {
          'Authorization': `Bearer ${githubToken}`,
          'Accept': 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
          'User-Agent': 'jules-autonomous-maintainer'
        }
      });
      if (runRes.ok) {
        const runInfo = await runRes.json();
        commitSha = runInfo.head_sha || commitSha;
        branch = runInfo.head_branch || branch;
        console.log(sanitizeLog(`Resolved from Actions API -> commitSha: ${commitSha}, branch: ${branch}`));
      } else {
        const errMsg = `Could not fetch run metadata for run ${runId} (HTTP ${runRes.status}).`;
        if (isWorkflowDispatch) {
          throw new Error(`${errMsg} Failed to resolve target commit and branch for manual dispatch.`);
        }
        console.warn(sanitizeLog(`${errMsg} Using fallback values.`));
      }
    } catch (err) {
      if (isWorkflowDispatch) {
        throw err;
      }
      console.warn(sanitizeLog(`Error fetching run metadata: ${err.message}. Using fallback values.`));
    }
  }

  commitSha = commitSha || 'HEAD';
  branch = branch || 'main';

  console.log(sanitizeLog(`Failed Run ID: ${runId}`));
  console.log(sanitizeLog(`Run URL: ${runUrl}`));
  console.log(sanitizeLog(`Commit SHA: ${commitSha}`));
  console.log(sanitizeLog(`Branch: ${branch} (Default: ${defaultBranch})`));

  // Step 1: Safety & Deduplication check
  console.log('Checking for existing Jules repair sessions for this run...');
  const { isDuplicate, existingSession } = await checkDuplicateSession(apiKey, {
    runId,
    commitSha,
    maxAgeHours: 48
  });

  if (isDuplicate && existingSession) {
    const msg = `Skipping repair. An existing Jules session "${existingSession.title || existingSession.name}" already exists (${existingSession.state}) for run ID ${runId}.`;
    console.log(sanitizeLog(msg));
    writeStepSummary(`### ⏭️ Jules CI Repair Skipped
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
  const extraSecrets = [apiKey, githubToken, process.env.SONAR_TOKEN].filter(Boolean);
  const redactedLogs = redactSensitiveData(combinedLogs, extraSecrets);
  const boundedLogs = truncateLog(redactedLogs, 250, 25000);

  // Step 4: Discover connected Jules source
  console.log('Discovering connected Jules source via Sources API...');
  const { sourceName } = await discoverSource(apiKey, repoOwner, repoName);
  console.log(sanitizeLog(`Discovered source: "${sourceName}"`));

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

  const title = `Fix CI Failure: Run ${runId} (${branch} @ ${commitSha.substring(0, 7)})`;

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

  console.log(sanitizeLog(`Successfully created Jules repair session! ID: ${session.id || session.name}`));
  const sessionUrl = session.url || `https://jules.google.com/session/${session.id || ''}`;
  console.log(sanitizeLog(`Session URL: ${sessionUrl}`));

  // Step 7: Output Step Summary
  const failedJobsSummary = failedJobs.map(j => `- **${j.name}** (Failed step: \`${j.failedStep}\`)`).join('\n') || 'None recorded';

  writeStepSummary(`### 🛠️ Google Jules CI Failure Investigation Triggered

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

*Jules will inspect the diagnostic logs, reproduce the failure root cause, apply a minimal fix and regression test, verify with \`./mvnw test\`, and submit a Pull Request if resolvable.*
`);
}

main().catch(err => {
  console.error(sanitizeLog(`Error during Jules CI repair: ${err.message}`));
  writeStepSummary(`### ❌ Jules CI Repair Launch Failed
\`\`\`
${err.message}
\`\`\`
`);
  process.exit(1);
});
