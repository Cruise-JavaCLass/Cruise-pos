/**
 * Comprehensive Unit Tests for Jules Autonomous Maintainer (Cruise-pos - Android)
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
    const input = 'Key AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q in request';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q/);
    assert.match(output, /\[REDACTED_GOOGLE_API_KEY\]/);
  });

  await t.test('Redacts GitHub Personal Access Tokens and OAuth tokens', () => {
    const input = 'Tokens: ghp_1234567890abcdef1234567890abcdef123456';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /ghp_/);
    assert.match(output, /\[REDACTED_GITHUB_TOKEN\]/);
  });

  await t.test('Redacts Bearer and Basic authentication headers', () => {
    const input = 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDc';
    const output = redactSensitiveData(input);
    assert.doesNotMatch(output, /eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9/);
    assert.match(output, /Bearer \[REDACTED_BEARER_TOKEN\]/);
  });

  await t.test('Redacts explicit keystore and keystore password secrets', () => {
    const secret = 'super_secret_keystore_pass_123';
    const input = `keystore_password=${secret}`;
    const output = redactSensitiveData(input, [secret]);
    assert.doesNotMatch(output, /super_secret_keystore_pass_123/);
    assert.match(output, /\[REDACTED_SECRET\]/);
  });
});

test('2. Log Truncation & Bounding Tests', async (t) => {
  await t.test('Preserves short logs unmodified', () => {
    const shortLog = 'Gradle build successful\nTest passed';
    assert.equal(truncateLog(shortLog, 10, 1000), shortLog);
  });

  await t.test('Truncates logs exceeding maxLines with context kept', () => {
    const lines = [];
    for (let i = 1; i <= 300; i++) lines.push(`Gradle Task Line ${i}`);
    const output = truncateLog(lines.join('\n'), 50, 20000);
    assert.match(output, /Gradle Task Line 1/);
    assert.match(output, /Gradle Task Line 300/);
    assert.match(output, /\[TRUNCATED 250 LINES OF INTERMEDIATE LOGS\]/);
  });
});

test('3. Jules Source Discovery Tests', async (t) => {
  await t.test('Discovers exact matching source case-insensitively', async () => {
    const mockSources = [
      {
        name: 'sources/github-Cruise-JavaCLass-Cruise-pos',
        githubRepo: {
          owner: 'Cruise-JavaCLass',
          repo: 'Cruise-pos',
          defaultBranch: { displayName: 'main' }
        }
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sources: mockSources })
    });

    const result = await discoverSource('test-key', 'cruise-javaclass', 'cruise-pos', { fetchFn: mockFetch });
    assert.equal(result.sourceName, 'sources/github-Cruise-JavaCLass-Cruise-pos');
    assert.equal(result.defaultBranch, 'main');
  });

  await t.test('Handles authentication error (HTTP 401)', async () => {
    const mockFetch = async () => ({
      ok: false,
      status: 401,
      text: async () => 'Unauthorized'
    });

    await assert.rejects(
      async () => discoverSource('invalid-key', 'Cruise-JavaCLass', 'Cruise-pos', { fetchFn: mockFetch }),
      /Jules API Authentication failed \(HTTP 401\)/
    );
  });
});

test('4. Session Deduplication Tests', async (t) => {
  await t.test('Detects duplicate CI repair session by run ID', async () => {
    const mockSessions = [
      {
        id: 'sess-pos-1',
        title: 'Fix Android CI Failure: Run 5555555',
        state: 'IN_PROGRESS',
        createTime: new Date().toISOString()
      }
    ];

    const mockFetch = async () => ({
      ok: true,
      status: 200,
      json: async () => ({ sessions: mockSessions })
    });

    const result = await checkDuplicateSession('test-key', { runId: '5555555' }, { fetchFn: mockFetch });
    assert.equal(result.isDuplicate, true);
    assert.equal(result.existingSession.id, 'sess-pos-1');
  });
});

test('5. Session Creation API Request Tests', async (t) => {
  await t.test('Builds valid request payload according to Jules spec', async () => {
    let capturedBody = {};
    const mockFetch = async (url, options) => {
      capturedBody = JSON.parse(options.body);
      return {
        ok: true,
        status: 200,
        json: async () => ({ id: 'sess-pos-ok', state: 'QUEUED' })
      };
    };

    const session = await createSession('test-key', {
      prompt: 'Fix ViewModel unit test',
      title: 'Weekly Android Audit',
      sourceName: 'sources/github-Cruise-JavaCLass-Cruise-pos',
      startingBranch: 'main'
    }, { fetchFn: mockFetch });

    assert.equal(capturedBody.prompt, 'Fix ViewModel unit test');
    assert.equal(capturedBody.automationMode, 'AUTO_CREATE_PR');
    assert.equal(session.id, 'sess-pos-ok');
  });
});

test('6. Prompt Generation Tests', async (t) => {
  await t.test('Generates audit prompt referencing Kotlin, Jetpack Compose, and ./gradlew test', () => {
    const prompt = generateAuditPrompt({ scope: 'full', defaultBranch: 'main', repoName: 'Cruise-pos' });
    assert.match(prompt, /Kotlin with Jetpack Compose/);
    assert.match(prompt, /\.\/gradlew test/);
    assert.match(prompt, /AUTO_CREATE_PR/);
  });

  await t.test('Generates CI failure prompt with Gradle and Android references', () => {
    const prompt = generateFixCiPrompt({
      runUrl: 'https://github.com/Cruise-JavaCLass/Cruise-pos/actions/runs/666',
      runId: '666',
      commitSha: 'b4dc0d3',
      branch: 'main',
      failedJobs: [{ name: 'Build & Test', failedStep: 'Run Unit Tests' }],
      failureSummary: 'FAILURE: Build failed with an exception in build.gradle.kts'
    });

    assert.match(prompt, /666/);
    assert.match(prompt, /b4dc0d3/);
    assert.match(prompt, /\.\/gradlew test/);
  });
});

test('7. Workflow YAML Structure & Safety Policy Verification', () => {
  const auditPath = new URL('../workflows/jules-audit.yml', import.meta.url);
  const fixCiPath = new URL('../workflows/jules-fix-ci.yml', import.meta.url);
  const ciPath = new URL('../workflows/ci.yml', import.meta.url);

  assert.ok(existsSync(ciPath), 'ci.yml must exist in Cruise-pos');
  assert.ok(existsSync(auditPath), 'jules-audit.yml must exist in Cruise-pos');
  assert.ok(existsSync(fixCiPath), 'jules-fix-ci.yml must exist in Cruise-pos');

  const auditContent = readFileSync(auditPath, 'utf8');
  const fixCiContent = readFileSync(fixCiPath, 'utf8');

  assert.match(auditContent, /cron:\s*['"]0 3 \* \* 1['"]/);
  assert.match(fixCiContent, /Android CI Pipeline/);
  assert.match(fixCiContent, /github\.event\.workflow_run\.conclusion == 'failure'/);
  assert.match(auditContent, /contents:\s*read/);
  assert.match(fixCiContent, /contents:\s*read/);
});

test('8. CI Failure Filtering & Safety Decision Tests', () => {
  const validEvent = {
    eventName: 'workflow_run',
    workflowRun: {
      name: 'Android CI Pipeline',
      conclusion: 'failure',
      headBranch: 'main'
    }
  };
  assert.equal(shouldTriggerCiRepair(validEvent, 'main').shouldTrigger, true);

  const ignoredWorkflow = {
    eventName: 'workflow_run',
    workflowRun: {
      name: 'Backend CI Pipeline',
      conclusion: 'failure',
      headBranch: 'main'
    }
  };
  assert.equal(shouldTriggerCiRepair(ignoredWorkflow, 'main').shouldTrigger, false);

  const nonDefaultBranch = {
    eventName: 'workflow_run',
    workflowRun: {
      name: 'Android CI Pipeline',
      conclusion: 'failure',
      headBranch: 'feature/untrusted'
    }
  };
  assert.equal(shouldTriggerCiRepair(nonDefaultBranch, 'main').shouldTrigger, false);
});
