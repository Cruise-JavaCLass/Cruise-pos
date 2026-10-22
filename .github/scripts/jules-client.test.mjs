/**
 * Comprehensive Unit Tests for Jules Autonomous Maintainer
 * 
 * Verifies:
 * - Sensitive credential & token redaction
 * - Log truncation and bounding
 * - Jules Source discovery and matching logic
 * - API error handling (401/403/404/500)
 * - Session deduplication and loop prevention
 * - Session creation request format and payload
 * - Audit and Fix-CI prompt generation
 * - Workflow YAML structure and safety filter policies
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import {
  redactSensitiveData,
  truncateLog,
  discoverSource,
  checkDuplicateSession,
  createSession,
  generateAuditPrompt,
  generateFixCiPrompt,
  shouldTriggerCiRepair
} from './jules-client.mjs';

test('1. Sensitive Data Redaction Tests', async (t) => {
  await t.test('Redacts Google API Keys', () => {
    const input = 'Call failed with key AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q in request';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q/);
    assert.match(output, /\[REDACTED_GOOGLE_API_KEY\]/);
  });

  await t.test('Redacts GitHub Personal Access Tokens and OAuth tokens', () => {
    const input = 'Tokens: ghp_1234567890abcdef1234567890abcdef123456 and gho_abcdef1234567890abcdef1234567890123456';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /ghp_/);
    assert.doesNotMatch(output, /gho_/);
    assert.match(output, /\[REDACTED_GITHUB_TOKEN\]/);
    assert.match(output, /\[REDACTED_GITHUB_OAUTH\]/);
  });

  await t.test('Redacts Bearer and Basic authentication headers', () => {
    const input = 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDc\nAuth: Basic dXNlcm5hbWU6cGFzc3dvcmQ=';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9/);
    assert.doesNotMatch(output, /dXNlcm5hbWU6cGFzc3dvcmQ=/);
    assert.match(output, /Bearer \[REDACTED_BEARER_TOKEN\]/);
    assert.match(output, /Basic \[REDACTED_BASIC_AUTH\]/);
  });

  await t.test('Redacts explicit secrets passed via extraSecrets', () => {
    const customSecret = 'SUPER_SECRET_SONAR_TOKEN_999';
    const input = `export SONAR_TOKEN=${customSecret} and run maven`;
    const output = redactSensitiveData(input, [customSecret]);
    assert.doesNotMatch(output, /SUPER_SECRET_SONAR_TOKEN_999/);
    assert.match(output, /\[REDACTED_SECRET\]/);
  });

  await t.test('Redacts key-value password and secret properties in environment dumps', () => {
    const input = 'password: MySuperSecretPassword123!\nsonar_token: sqp_0123456789abcdef0123456789abcdef01234567';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /MySuperSecretPassword123!/);
    assert.doesNotMatch(output, /sqp_0123456789abcdef/);
  });
});

test('2. Log Truncation & Bounding Tests', async (t) => {
  await t.test('Preserves short logs unmodified', () => {
    const shortLog = 'Line 1\nLine 2\nLine 3';
    const output = truncateLog(shortLog, 10, 1000);
    assert.equal(output, shortLog);
  });

  await t.test('Truncates logs exceeding maxLines while preserving header and error tail', () => {
    const lines = [];
    for (let i = 1; i <= 500; i++) {
      lines.push(`Log line ${i}: Executing step`);
    }
    const longLog = lines.join('\n');
    const output = truncateLog(longLog, 100, 50000);

    assert.match(output, /Log line 1:/);
    assert.match(output, /Log line 500:/);
    assert.match(output, /\[TRUNCATED 400 LINES OF INTERMEDIATE LOGS\]/);
  });

  await t.test('Restricts maximum byte size', () => {
    const hugeText = 'A'.repeat(50000);
    const output = truncateLog(hugeText, 100, 1000);
    assert.ok(Buffer.byteLength(output, 'utf8') <= 1200);
    assert.match(output, /\[TRUNCATED BYTES\]/);
  });
});

test('3. Jules Source Discovery Tests', async (t) => {
  await t.test('Discovers exact matching source case-insensitively', async () => {
    const mockSources = [
      {
        name: 'sources/github-other-repo',
        githubRepo: { owner: 'other', repo: 'repo', defaultBranch: { displayName: 'main' } }
      },
      {
        name: 'sources/github-Cruise-JavaCLass-Cruise-api',
        githubRepo: {
          owner: 'Cruise-JavaCLass',
          repo: 'Cruise-api',
          defaultBranch: { displayName: 'main' }
        }
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sources: mockSources })
    });

    const result = await discoverSource('test-key', 'cruise-javaclass', 'cruise-api', { fetchFn: mockFetch });
    assert.equal(result.sourceName, 'sources/github-Cruise-JavaCLass-Cruise-api');
    assert.equal(result.defaultBranch, 'main');
  });

  await t.test('Discovers source across multiple pages using nextPageToken', async () => {
    let callCount = 0;
    const mockFetch = async (url) => {
      callCount++;
      const parsedUrl = new URL(url);
      const token = parsedUrl.searchParams.get('pageToken');

      if (!token) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            sources: [
              { name: 'sources/page1-repo', githubRepo: { owner: 'other', repo: 'other' } }
            ],
            nextPageToken: 'token-page-2'
          })
        };
      } else if (token === 'token-page-2') {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            sources: [
              {
                name: 'sources/github-Cruise-JavaCLass-Cruise-api',
                githubRepo: {
                  owner: 'Cruise-JavaCLass',
                  repo: 'Cruise-api',
                  defaultBranch: { displayName: 'main' }
                }
              }
            ]
          })
        };
      }
      return { ok: true, status: 200, json: async () => ({ sources: [] }) };
    };

    const result = await discoverSource('test-key', 'Cruise-JavaCLass', 'Cruise-api', { fetchFn: mockFetch });
    assert.equal(result.sourceName, 'sources/github-Cruise-JavaCLass-Cruise-api');
    assert.equal(callCount, 2);
  });

  await t.test('Detects repeating pageToken in discoverSource and throws descriptive error', async () => {
    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        sources: [
          { name: 'sources/looping-repo', githubRepo: { owner: 'loop', repo: 'loop' } }
        ],
        nextPageToken: 'looping-token'
      })
    });

    await assert.rejects(
      async () => discoverSource('test-key', 'Cruise-JavaCLass', 'Cruise-api', { fetchFn: mockFetch }),
      /Infinite pagination loop detected in Jules Sources API: repeating pageToken "looping-token"/
    );
  });

  await t.test('Throws descriptive error when source is not connected', async () => {
    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({
        sources: [
          { name: 'sources/github-foo-bar', githubRepo: { owner: 'foo', repo: 'bar' } }
        ]
      })
    });

    await assert.rejects(
      async () => discoverSource('test-key', 'Cruise-JavaCLass', 'NonExistent', { fetchFn: mockFetch }),
      /No connected Jules source matches repository "Cruise-JavaCLass\/NonExistent"/
    );
  });

  await t.test('Handles authentication error (HTTP 401/403) with clear guidance', async () => {
    const mockFetch = async () => ({
      ok: false,
      status: 401,
      text: async () => 'Unauthorized'
    });

    await assert.rejects(
      async () => discoverSource('invalid-key', 'Cruise-JavaCLass', 'Cruise-api', { fetchFn: mockFetch }),
      /Jules API Authentication failed \(HTTP 401\)/
    );
  });
});

test('4. Session Deduplication Tests', async (t) => {
  await t.test('Detects duplicate CI repair session by run ID', async () => {
    const mockSessions = [
      {
        id: 'sess-100',
        title: 'Fix CI Failure: Run 123456789 (main @ abc1234)',
        prompt: 'Investigate run id: 123456789',
        state: 'IN_PROGRESS',
        createTime: new Date().toISOString()
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sessions: mockSessions })
    });

    const result = await checkDuplicateSession('test-key', { runId: '123456789' }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, true);
    assert.equal(result.existingSession.id, 'sess-100');
  });

  await t.test('Detects duplicate weekly audit session in the last 24 hours', async () => {
    const mockSessions = [
      {
        id: 'sess-200',
        title: 'Weekly Repository Audit: FULL (2026-10-16)',
        state: 'QUEUED',
        createTime: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString() // 2 hours ago
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sessions: mockSessions })
    });

    const result = await checkDuplicateSession('test-key', { auditType: 'weekly', maxAgeHours: 24 }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, true);
    assert.equal(result.existingSession.id, 'sess-200');
  });

  await t.test('Deduplicates weekly audit independently by scope', async () => {
    const mockSessions = [
      {
        id: 'sess-full',
        title: 'Weekly Repository Audit: FULL (2026-10-16)',
        state: 'QUEUED',
        createTime: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sessions: mockSessions })
    });

    // Checking 'security' audit should NOT duplicate 'full' audit
    const secResult = await checkDuplicateSession('test-key', { auditType: 'security', maxAgeHours: 24 }, { fetchFn: mockFetch });
    assert.equal(secResult.isDuplicate, false);

    // Checking 'full' audit SHOULD duplicate 'full' audit
    const fullResult = await checkDuplicateSession('test-key', { auditType: 'full', maxAgeHours: 24 }, { fetchFn: mockFetch });
    assert.equal(fullResult.isDuplicate, true);
    assert.equal(fullResult.existingSession.id, 'sess-full');
  });

  await t.test('Deduplicates weekly audit when session is in PLANNING state', async () => {
    const mockSessions = [
      {
        id: 'sess-planning',
        title: 'Weekly Repository Audit: SECURITY (2026-10-17)',
        state: 'PLANNING',
        createTime: new Date(Date.now() - 1 * 60 * 60 * 1000).toISOString()
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sessions: mockSessions })
    });

    const result = await checkDuplicateSession('test-key', { auditType: 'security', maxAgeHours: 24 }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, true);
    assert.equal(result.existingSession.id, 'sess-planning');
  });

  await t.test('Searches multiple session pages and stops paging immediately upon finding duplicate', async () => {
    let callCount = 0;
    const mockFetch = async (url) => {
      callCount++;
      const parsedUrl = new URL(url);
      const token = parsedUrl.searchParams.get('pageToken');

      if (!token) {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            sessions: [
              { id: 'sess-p1', title: 'Other run 111', createTime: new Date().toISOString(), state: 'COMPLETED' }
            ],
            nextPageToken: 'sess-page-2'
          })
        };
      } else if (token === 'sess-page-2') {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            sessions: [
              { id: 'sess-p2-target', title: 'Fix CI Failure: Run 777', createTime: new Date().toISOString(), state: 'IN_PROGRESS' }
            ],
            nextPageToken: 'sess-page-3'
          })
        };
      }
      return { ok: true, status: 200, json: async () => ({ sessions: [] }) };
    };

    const result = await checkDuplicateSession('test-key', { runId: '777' }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, true);
    assert.equal(result.existingSession.id, 'sess-p2-target');
    assert.equal(callCount, 2, 'Must stop paging as soon as duplicate is found on page 2');
  });

  await t.test('Detects repeating pageToken in checkDuplicateSession and aborts safely', async () => {
    let callCount = 0;
    const mockFetch = async () => {
      callCount++;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          sessions: [
            { id: 'sess-repeat', title: 'Unrelated title', createTime: new Date().toISOString(), state: 'COMPLETED' }
          ],
          nextPageToken: 'stuck-token'
        })
      };
    };

    const result = await checkDuplicateSession('test-key', { runId: '888' }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, false);
    assert.equal(result.existingSession, null);
    assert.equal(callCount, 2, 'Must abort pagination after seeing repeating stuck-token');
  });

  await t.test('Allows session creation when no duplicate exists', async () => {
    const mockSessions = [
      {
        id: 'sess-300',
        title: 'Fix CI Failure: Run 999999999',
        state: 'COMPLETED',
        createTime: new Date(Date.now() - 72 * 60 * 60 * 1000).toISOString() // 3 days ago
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sessions: mockSessions })
    });

    const result = await checkDuplicateSession('test-key', { runId: '123456789' }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, false);
    assert.equal(result.existingSession, null);
  });
});

test('5. Session Creation API Request Tests', async (t) => {
  await t.test('Builds exact request payload according to official Jules Sessions API spec', async () => {
    let capturedUrl = '';
    let capturedHeaders = {};
    let capturedBody = {};

    const mockFetch = async (url, options) => {
      capturedUrl = url;
      capturedHeaders = options.headers;
      capturedBody = JSON.parse(options.body);
      return {
        ok: true,
        status: 200,
        json: async () => ({
          name: 'sessions/test-123',
          id: 'test-123',
          title: capturedBody.title,
          state: 'QUEUED',
          url: 'https://jules.google.com/session/test-123'
        })
      };
    };

    const session = await createSession('test-api-key', {
      prompt: 'Refactor auth controller tests',
      title: 'Weekly Audit: TESTS',
      sourceName: 'sources/github-Cruise-JavaCLass-Cruise-api',
      startingBranch: 'main',
      requirePlanApproval: false,
      automationMode: 'AUTO_CREATE_PR'
    }, { fetchFn: mockFetch });

    assert.equal(capturedUrl, 'https://jules.googleapis.com/v1alpha/sessions');
    assert.equal(capturedHeaders['X-Goog-Api-Key'], 'test-api-key');
    assert.equal(capturedHeaders['Content-Type'], 'application/json');

    assert.equal(capturedBody.prompt, 'Refactor auth controller tests');
    assert.equal(capturedBody.title, 'Weekly Audit: TESTS');
    assert.equal(capturedBody.sourceContext.source, 'sources/github-Cruise-JavaCLass-Cruise-api');
    assert.equal(capturedBody.sourceContext.githubRepoContext.startingBranch, 'main');
    assert.equal(capturedBody.requirePlanApproval, false);
    assert.equal(capturedBody.automationMode, 'AUTO_CREATE_PR');

    assert.equal(session.id, 'test-123');
    assert.equal(session.state, 'QUEUED');
  });

  await t.test('Throws descriptive error when Jules API returns HTTP error', async () => {
    const mockFetch = async () => ({
      ok: false,
      status: 400,
      text: async () => JSON.stringify({ error: { message: 'Invalid starting branch' } })
    });

    await assert.rejects(
      async () => createSession('test-api-key', {
        prompt: 'test',
        sourceName: 'sources/test',
        startingBranch: 'invalid-branch'
      }, { fetchFn: mockFetch }),
      /Jules Sessions API error HTTP 400/
    );
  });
});

test('6. Prompt Generation Tests', async (t) => {
  await t.test('Generates audit prompt with correct scope instructions and verification commands', () => {
    const scopes = ['bugs', 'tests', 'security', 'code-quality', 'documentation', 'full'];

    for (const scope of scopes) {
      const prompt = generateAuditPrompt({ scope, defaultBranch: 'main', repoName: 'Cruise-api' });

      assert.match(prompt, new RegExp(`Audit Scope: ${scope.toUpperCase()}`, 'i'));
      assert.match(prompt, /Java 21 LTS with Spring Boot 3\.x/);
      assert.match(prompt, /\.\/mvnw test/);
      assert.match(prompt, /AUTO_CREATE_PR/);
      assert.match(prompt, /Select Exactly ONE Task/);
      assert.match(prompt, /SonarCloud Quality Gate/);
    }
  });

  await t.test('Generates CI failure repair prompt containing run metadata and instructions', () => {
    const prompt = generateFixCiPrompt({
      runUrl: 'https://github.com/Cruise-JavaCLass/Cruise-api/actions/runs/12345',
      runId: '12345',
      commitSha: 'a1b2c3d4e5f6',
      branch: 'main',
      failedJobs: [{ name: 'Build & Test', failedStep: 'Verify Build' }],
      failureSummary: 'Compilation failure in ShoreExcursionService.java: [642,105]'
    });

    assert.match(prompt, /12345/);
    assert.match(prompt, /a1b2c3d4e5f6/);
    assert.match(prompt, /Build & Test/);
    assert.match(prompt, /Compilation failure in ShoreExcursionService/);
    assert.match(prompt, /Infrastructure \/ External Outage \/ Missing Secret/);
    assert.match(prompt, /\.\/mvnw test/);
  });
});

test('7. Workflow YAML Structure & Safety Policy Verification', () => {
  const auditPath = new URL('../workflows/jules-audit.yml', import.meta.url);
  const fixCiPath = new URL('../workflows/jules-fix-ci.yml', import.meta.url);

  assert.ok(existsSync(auditPath), 'jules-audit.yml must exist');
  assert.ok(existsSync(fixCiPath), 'jules-fix-ci.yml must exist');

  const auditContent = readFileSync(auditPath, 'utf8');
  const fixCiContent = readFileSync(fixCiPath, 'utf8');

  // Audit workflow checks
  assert.match(auditContent, /cron:\s*['"]0 3 \* \* \*['"]/);
  assert.match(auditContent, /workflow_dispatch:/);
  assert.match(auditContent, /concurrency:/);
  assert.match(auditContent, /JULES_API_KEY/);
  assert.match(auditContent, /node \.github\/scripts\/run-audit\.mjs/);

  // Fix-CI workflow checks
  assert.match(fixCiContent, /workflow_run:/);
  assert.match(fixCiContent, /Backend CI Pipeline/);
  assert.match(fixCiContent, /types:\s*\[completed\]|types:\s*\n\s*-\s*completed/);
  assert.match(fixCiContent, /github\.event\.workflow_run\.conclusion == 'failure'/);
  assert.match(fixCiContent, /github\.event\.repository\.default_branch/);
  assert.match(fixCiContent, /concurrency:/);
  assert.match(fixCiContent, /node \.github\/scripts\/run-fix-ci\.mjs/);

  // Safe permissions check (minimal required, no admin or write-all)
  assert.doesNotMatch(auditContent, /permissions:\s*write-all/);
  assert.doesNotMatch(fixCiContent, /permissions:\s*write-all/);
  assert.match(auditContent, /contents:\s*read/);
  assert.match(fixCiContent, /contents:\s*read/);
});

test('8. CI Failure Filtering & Safety Decision Tests', async (t) => {
  await t.test('Triggers repair for failure in Backend CI Pipeline on default branch', () => {
    const event = {
      eventName: 'workflow_run',
      workflowRun: {
        name: 'Backend CI Pipeline',
        conclusion: 'failure',
        headBranch: 'main'
      }
    };
    const decision = shouldTriggerCiRepair(event, 'main');
    assert.equal(decision.shouldTrigger, true);
  });

  await t.test('Always triggers when manual workflow_dispatch is dispatched', () => {
    const event = { eventName: 'workflow_dispatch' };
    const decision = shouldTriggerCiRepair(event, 'main');
    assert.equal(decision.shouldTrigger, true);
  });

  await t.test('Ignores successful, cancelled, skipped, or neutral runs', () => {
    const conclusions = ['success', 'cancelled', 'skipped', 'neutral'];
    for (const conclusion of conclusions) {
      const event = {
        eventName: 'workflow_run',
        workflowRun: {
          name: 'Backend CI Pipeline',
          conclusion,
          headBranch: 'main'
        }
      };
      const decision = shouldTriggerCiRepair(event, 'main');
      assert.equal(decision.shouldTrigger, false);
      assert.match(decision.reason, /Ignored conclusion/);
    }
  });

  await t.test('Ignores failure runs from non-default branches (untrusted PR branches)', () => {
    const event = {
      eventName: 'workflow_run',
      workflowRun: {
        name: 'Backend CI Pipeline',
        conclusion: 'failure',
        headBranch: 'feature/untrusted-external-contribution'
      }
    };
    const decision = shouldTriggerCiRepair(event, 'main');
    assert.equal(decision.shouldTrigger, false);
    assert.match(decision.reason, /Ignored branch/);
  });

  await t.test('Ignores self-runs of maintainer workflows to prevent infinite recursion', () => {
    const maintainerWorkflows = ['Jules Repository Audit', 'Jules CI Failure Repair'];
    for (const name of maintainerWorkflows) {
      const event = {
        eventName: 'workflow_run',
        workflowRun: {
          name,
          conclusion: 'failure',
          headBranch: 'main'
        }
      };
      const decision = shouldTriggerCiRepair(event, 'main');
      assert.equal(decision.shouldTrigger, false);
      assert.match(decision.reason, /Ignored workflow/);
    }
  });
});

