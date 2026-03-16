#!/usr/bin/env bun

import {spawnSync} from "node:child_process"
import {existsSync, mkdirSync, readFileSync, renameSync, rmSync, statSync, unlinkSync, writeFileSync} from "node:fs"
import {dirname, resolve} from "node:path"
import process from "node:process"

const USAGE_EXIT_CODE = 2;
const COMMAND_EXIT_CODE = 3;
const CHECKPOINT_VERSION = 1;
const CHECKPOINT_FILE_NAME = "clone-from-local-cache.state.json";
const BOOTSTRAP_CHECKPOINT_FILE_SUFFIX = ".clone-from-local-cache.bootstrap.json";
const FETCH_STRATEGY_NEGOTIATION_FIRST = "negotiation-first";

const STEPS = {
  cloneLocal: "clone_local",
  setOriginUrl: "set_origin_url",
  prepareNormalizationConfig: "prepare_normalization_config",
  fetchPruneOrigin: "fetch_prune_origin",
  removeInheritedRefsAndTags: "remove_inherited_refs_and_tags",
  checkoutResetClean: "checkout_reset_clean",
  pruneLocalBranches: "prune_local_branches",
  syncLocalConfig: "sync_local_config",
};

const STEP_SEQUENCE = [
  STEPS.cloneLocal,
  STEPS.setOriginUrl,
  STEPS.prepareNormalizationConfig,
  STEPS.removeInheritedRefsAndTags,
  STEPS.fetchPruneOrigin,
  STEPS.checkoutResetClean,
  STEPS.pruneLocalBranches,
  STEPS.syncLocalConfig,
];

const ANSI = {
  reset: "\u001b[0m",
  blue: "\u001b[34m",
  cyan: "\u001b[36m",
  dim: "\u001b[2m",
  green: "\u001b[32m",
  red: "\u001b[31m",
  yellow: "\u001b[33m",
};

const STRUCTURAL_EXCLUDED_CONFIG_KEYS = new Set([
  "core.bare",
  "core.repositoryformatversion",
  "core.worktree",
]);

const SPARSE_EXCLUDED_CONFIG_KEYS = new Set([
  "core.sparsecheckout",
  "core.sparsecheckoutcone",
  "index.sparse",
]);

const STRUCTURAL_EXCLUDED_CONFIG_PREFIXES = [
  "extensions.",
];

class UsageError extends Error {
  constructor(message) {
    super(message);
    this.name = "UsageError";
  }
}

class GitError extends Error {
  constructor(args, cwd, status, stderr, cause) {
    const command = formatCommand("git", args);
    const place = cwd ? ` (cwd: ${cwd})` : "";
    const details = stderr.trim();
    const reason = details.length > 0 ? `\n${details}` : "";
    super(`Command failed: ${command}${place}${reason}`);
    this.name = "GitError";
    this.args = args;
    this.cwd = cwd;
    this.status = status;
    this.stderr = stderr;
    this.cause = cause;
  }
}

function printUsage() {
  console.log([
    "Usage:",
    "  bun community/tools/clone-from-local-cache.mjs [--dry-run] [--quiet] [--no-color] [--status] [--reset-state] [--fetch-diagnostics] <source-repo-path> <target-repo-path>",
    "",
    "Description:",
    "  Clone from a local source repository using git --local for speed.",
    "  Then normalize target repository to remote truth and mirror local git config.",
    "  Progress is checkpointed in target/.git/clone-from-local-cache.state.json for resume.",
    "  Clone-step failures are checkpointed in <target>.clone-from-local-cache.bootstrap.json.",
    "",
    "Flags:",
    "  --quiet        Disable verbose command logging (verbose is enabled by default)",
    "  --verbose      Explicitly enable verbose command logging",
    "  --dry-run      Print planned actions without creating target clone",
    "  --status       Print checkpoint status for target and exit",
    "  --reset-state  Remove checkpoint state files from target and exit",
    "  --fetch-diagnostics  Print fetch negotiation and timing diagnostics",
    "  --no-color     Disable ANSI colors",
    "  --color        Force-enable ANSI colors",
    "  --help         Print this help",
  ].join("\n"));
}

function detectColorSupport() {
  const env = process["env"] ?? {};
  if (Object.prototype.hasOwnProperty.call(env, "NO_COLOR")) {
    return false;
  }
  const forceColor = env["FORCE_COLOR"];
  if (forceColor !== undefined) {
    return forceColor !== "0";
  }
  const stdout = process["stdout"];
  return Boolean(stdout && stdout["isTTY"] === true);
}

function colorize(text, color, useColor) {
  if (!useColor) {
    return text;
  }
  return `${color}${text}${ANSI.reset}`;
}

function printInfo(message, useColor) {
  console.log(colorize(message, ANSI.blue, useColor));
}

function printWarning(message, useColor) {
  console.log(colorize(message, ANSI.yellow, useColor));
}

function printSuccess(message, useColor) {
  console.log(colorize(message, ANSI.green, useColor));
}

function printError(message, useColor) {
  console.error(colorize(message, ANSI.red, useColor));
}

function printKeyValue(label, value, useColor) {
  console.log(`${colorize(`${label}:`, ANSI.blue, useColor)} ${value}`);
}

function printStepUpdate(stepId, phase, message, useColor) {
  const prefix = colorize(`[step ${stepId}]`, ANSI.dim, useColor);
  const phaseColor = phase === "FAIL"
    ? ANSI.red
    : phase === "OK"
      ? ANSI.green
      : phase === "SKIP"
        ? ANSI.yellow
        : ANSI.cyan;
  const phaseText = colorize(phase, phaseColor, useColor);
  if (message.length === 0) {
    console.log(`${prefix} ${phaseText}`);
    return;
  }
  console.log(`${prefix} ${phaseText} ${message}`);
}

function parseArguments(argv) {
  let dryRun = false;
  let verbose = true;
  let useColor = detectColorSupport();
  let fetchDiagnostics = false;
  let statusOnly = false;
  let resetState = false;
  const positional = [];

  for (const arg of argv) {
    if (arg === "--quiet" || arg === "--no-verbose") {
      verbose = false;
      continue;
    }
    if (arg === "--dry-run") {
      dryRun = true;
      continue;
    }
    if (arg === "--verbose") {
      verbose = true;
      continue;
    }
    if (arg === "--status") {
      statusOnly = true;
      continue;
    }
    if (arg === "--reset-state") {
      resetState = true;
      continue;
    }
    if (arg === "--fetch-diagnostics") {
      fetchDiagnostics = true;
      continue;
    }
    if (arg === "--no-color") {
      useColor = false;
      continue;
    }
    if (arg === "--color") {
      useColor = true;
      continue;
    }
    if (arg === "--help") {
      printUsage();
      process.exit(0);
    }
    if (arg.startsWith("--")) {
      throw new UsageError(`Unknown option: ${arg}`);
    }
    positional.push(arg);
  }

  if (statusOnly && resetState) {
    throw new UsageError("--status and --reset-state cannot be used together");
  }
  if (dryRun && (statusOnly || resetState)) {
    throw new UsageError("--dry-run cannot be combined with --status or --reset-state");
  }

  if (positional.length !== 2) {
    throw new UsageError("Expected exactly 2 positional arguments: <source-repo-path> <target-repo-path>");
  }

  return {
    sourceRepoPath: resolve(positional[0]),
    targetRepoPath: resolve(positional[1]),
    dryRun,
    fetchDiagnostics,
    resetState,
    statusOnly,
    useColor,
    verbose,
  };
}

function formatCommand(command, args) {
  return [command, ...args.map(quoteArg)].join(" ");
}

function quoteArg(value) {
  return /^[-_./:@a-zA-Z0-9]+$/.test(value) ? value : JSON.stringify(value);
}

function isObject(value) {
  return typeof value === "object" && value !== null;
}

function runGit(args, options = {}) {
  const {
    cwd,
    allowFailure = false,
    verbose = false,
    useColor = false,
    streamOutput = false,
    captureStreamOutput = false,
  } = options;
  if (verbose) {
    const commandPrefix = colorize("$", ANSI.dim, useColor);
    const commandText = colorize(formatCommand("git", args), ANSI.cyan, useColor);
    console.log(`${commandPrefix} ${commandText}`);
  }

  const shouldCaptureStreamOutput = streamOutput && captureStreamOutput;
  const stdio = streamOutput
    ? shouldCaptureStreamOutput
      ? ["ignore", "inherit", "pipe"]
      : "inherit"
    : ["ignore", "pipe", "pipe"];
  const spawnOptions = {
    cwd,
    encoding: "utf8",
    stdio,
  };
  if (shouldCaptureStreamOutput) {
    spawnOptions["maxBuffer"] = 1024 * 1024 * 128;
  }

  const result = spawnSync("git", args, spawnOptions);

  if (result.error) {
    throw new GitError(args, cwd, -1, "", result.error);
  }

  const status = result.status ?? -1;
  const stdout = result.stdout ?? "";
  const stderr = result.stderr ?? "";

  if (shouldCaptureStreamOutput) {
    const processStderr = process["stderr"];
    if (stderr.length > 0 && processStderr) {
      processStderr.write(stderr);
    }
  }

  if (status !== 0 && !allowFailure) {
    throw new GitError(args, cwd, status, stderr);
  }

  return {
    status,
    stdout,
    stderr,
  };
}

function ensureDirectoryExists(path, description) {
  if (!existsSync(path)) {
    throw new UsageError(`${description} does not exist: ${path}`);
  }

  let stats;
  try {
    stats = statSync(path);
  }
  catch (error) {
    throw new UsageError(`Cannot access ${description}: ${path}. ${String(error)}`);
  }

  if (!stats.isDirectory()) {
    throw new UsageError(`${description} is not a directory: ${path}`);
  }
}

function ensureSourceIsGitRepository(sourceRepoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", sourceRepoPath, "rev-parse", "--is-inside-work-tree"], {
    allowFailure: true,
    useColor,
    verbose,
  });
  if (result.status !== 0 || result.stdout.trim() !== "true") {
    throw new UsageError(`Source path is not a git work tree: ${sourceRepoPath}`);
  }
}

function detectOriginUrl(sourceRepoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", sourceRepoPath, "remote", "get-url", "origin"], {
    allowFailure: true,
    useColor,
    verbose,
  });
  if (result.status !== 0) {
    throw new UsageError(`Source repo does not have origin remote configured: ${sourceRepoPath}`);
  }
  const url = result.stdout.trim();
  if (url.length === 0) {
    throw new UsageError(`Origin URL is empty in source repo: ${sourceRepoPath}`);
  }
  return url;
}

function detectOriginDefaultBranch(sourceRepoPath, options) {
  const {verbose, useColor} = options;
  const headRefResult = runGit(
    ["-C", sourceRepoPath, "symbolic-ref", "--quiet", "--short", "refs/remotes/origin/HEAD"],
    {allowFailure: true, useColor, verbose},
  );
  if (headRefResult.status === 0) {
    const value = headRefResult.stdout.trim();
    if (value.startsWith("origin/")) {
      return value.slice("origin/".length);
    }
    if (value.length > 0) {
      return value;
    }
  }

  const remoteShowResult = runGit(["-C", sourceRepoPath, "remote", "show", "origin"], {
    allowFailure: true,
    useColor,
    verbose,
  });
  if (remoteShowResult.status === 0) {
    const match = remoteShowResult.stdout.match(/^\s*HEAD branch:\s*(.+)\s*$/m);
    if (match) {
      const branch = match[1].trim();
      if (branch.length > 0 && branch !== "(unknown)") {
        return branch;
      }
    }
  }

  throw new UsageError(
    [
      `Cannot determine default branch for origin in ${sourceRepoPath}.`,
      "Run: git -C <source-repo-path> remote set-head origin --auto",
    ].join(" "),
  );
}

function buildCloneArgs(sourceRepoPath, targetRepoPath) {
  return [
    "clone",
    "--local",
    "--progress",
    sourceRepoPath,
    targetRepoPath,
  ];
}

function parseNonEmptyLines(output) {
  return output
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
}

function listRefNames(repoPath, refPrefix, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "for-each-ref", "--format=%(refname)", refPrefix], {verbose, useColor});
  return parseNonEmptyLines(result.stdout);
}

function listLocalBranches(repoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "for-each-ref", "--format=%(refname:short)", "refs/heads"], {verbose, useColor});
  return parseNonEmptyLines(result.stdout);
}

function unsetLocalConfigKey(repoPath, key, options) {
  const {verbose, useColor} = options;
  runGit(["-C", repoPath, "config", "--local", "--unset-all", key], {
    allowFailure: true,
    verbose,
    useColor,
  });
}

function prepareTargetConfigForNormalizationFetch(targetRepoPath, options) {
  const {verbose, useColor} = options;

  // Ensure normalization fetch can see all heads, regardless of cloned local fetch config.
  unsetLocalConfigKey(targetRepoPath, "remote.origin.fetch", options);
  runGit(["-C", targetRepoPath, "config", "--local", "--add", "remote.origin.fetch", "+refs/heads/*:refs/remotes/origin/*"], {verbose, useColor});

  // Avoid partial-clone fetch behavior inherited from source clone during normalization.
  unsetLocalConfigKey(targetRepoPath, "remote.origin.promisor", options);
  unsetLocalConfigKey(targetRepoPath, "remote.origin.partialclonefilter", options);

  // Prevent sparse-checkout inherited config from creating unexpectedly sparse working tree.
  for (const key of SPARSE_EXCLUDED_CONFIG_KEYS) {
    unsetLocalConfigKey(targetRepoPath, key, options);
  }
}

function hasRef(repoPath, refName, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "show-ref", "--verify", "--quiet", refName], {
    allowFailure: true,
    verbose,
    useColor,
  });
  return result.status === 0;
}

function buildFetchArgs(targetRepoPath, defaultBranch, options) {
  const args = ["-C", targetRepoPath, "fetch", "--progress", "--prune"];
  let negotiationTip = "";
  const candidateTip = `refs/remotes/origin/${defaultBranch}`;
  if (hasRef(targetRepoPath, candidateTip, options)) {
    args.push(`--negotiation-tip=${candidateTip}`);
    negotiationTip = candidateTip;
  }
  args.push("origin");
  return {
    args,
    negotiationTip,
  };
}

function ensureRemoteBranchFetched(targetRepoPath, defaultBranch, options) {
  const {verbose, useColor} = options;
  const remoteRef = `refs/remotes/origin/${defaultBranch}`;
  if (!hasRef(targetRepoPath, remoteRef, {verbose, useColor})) {
    throw new UsageError(
      [
        `Missing origin/${defaultBranch} after fetch in ${targetRepoPath}.`,
        `Ensure fetch spec includes refs/heads/${defaultBranch}.`,
      ].join(" "),
    );
  }
}

function pruneLocalBranchesExceptDefault(targetRepoPath, defaultBranch, options) {
  const {verbose, useColor} = options;
  const localBranches = listLocalBranches(targetRepoPath, options);
  let removedCount = 0;
  for (const branchName of localBranches) {
    if (branchName === defaultBranch) {
      continue;
    }
    runGit(["-C", targetRepoPath, "branch", "-D", branchName], {verbose, useColor});
    removedCount++;
  }
  return removedCount;
}

function listLocalConfigEntries(repoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "config", "--local", "--null", "--list"], {verbose, useColor});
  return parseConfigEntries(result.stdout);
}

function parseConfigEntries(output) {
  if (output.length === 0) {
    return [];
  }

  const entries = [];
  for (const chunk of output.split("\0")) {
    if (chunk.length === 0) {
      continue;
    }

    const newlineIndex = chunk.indexOf("\n");
    if (newlineIndex >= 0) {
      const key = chunk.slice(0, newlineIndex).trim().toLowerCase();
      const value = chunk.slice(newlineIndex + 1);
      if (key.length > 0) {
        entries.push({key, value});
      }
      continue;
    }

    const equalIndex = chunk.indexOf("=");
    if (equalIndex >= 0) {
      const key = chunk.slice(0, equalIndex).trim().toLowerCase();
      const value = chunk.slice(equalIndex + 1);
      if (key.length > 0) {
        entries.push({key, value});
      }
      continue;
    }

    const key = chunk.trim().toLowerCase();
    if (key.length > 0) {
      entries.push({key, value: ""});
    }
  }
  return entries;
}

function shouldSyncConfigKey(key) {
  if (SPARSE_EXCLUDED_CONFIG_KEYS.has(key)) {
    return false;
  }
  if (STRUCTURAL_EXCLUDED_CONFIG_KEYS.has(key)) {
    return false;
  }
  return !STRUCTURAL_EXCLUDED_CONFIG_PREFIXES.some((prefix) => key.startsWith(prefix));
}

function groupEntriesByKey(entries) {
  const grouped = new Map();
  for (const {key, value} of entries) {
    if (!shouldSyncConfigKey(key)) {
      continue;
    }
    const current = grouped.get(key);
    if (current) {
      current.push(value);
    }
    else {
      grouped.set(key, [value]);
    }
  }
  return grouped;
}

function valuesEqual(left, right) {
  if (left.length !== right.length) {
    return false;
  }
  for (let i = 0; i < left.length; i++) {
    if (left[i] !== right[i]) {
      return false;
    }
  }
  return true;
}

function syncLocalConfig(sourceRepoPath, targetRepoPath, options) {
  const {verbose, useColor} = options;
  const sourceConfig = groupEntriesByKey(listLocalConfigEntries(sourceRepoPath, options));
  const targetConfig = groupEntriesByKey(listLocalConfigEntries(targetRepoPath, options));

  // Convergent sync: for each non-excluded key, target values are replaced with source values.
  const keys = new Set([...sourceConfig.keys(), ...targetConfig.keys()]);
  const sortedKeys = [...keys].sort();
  let changedKeys = 0;

  for (const key of sortedKeys) {
    const sourceValues = sourceConfig.get(key) ?? [];
    const targetValues = targetConfig.get(key) ?? [];
    if (valuesEqual(sourceValues, targetValues)) {
      continue;
    }

    changedKeys++;
    if (targetValues.length > 0) {
      runGit(["-C", targetRepoPath, "config", "--local", "--unset-all", key], {
        allowFailure: true,
        useColor,
        verbose,
      });
    }
    for (const value of sourceValues) {
      runGit(["-C", targetRepoPath, "config", "--local", "--add", key, value], {verbose, useColor});
    }
  }

  return changedKeys;
}

function getCheckpointStatePath(targetRepoPath) {
  return resolve(targetRepoPath, ".git", CHECKPOINT_FILE_NAME);
}

function getBootstrapCheckpointPath(targetRepoPath) {
  return `${targetRepoPath}${BOOTSTRAP_CHECKPOINT_FILE_SUFFIX}`;
}

function createBootstrapCheckpointState(sourceRepoPath, targetRepoPath) {
  const now = new Date().toISOString();
  return {
    version: CHECKPOINT_VERSION,
    sourceRepoPath,
    targetRepoPath,
    currentStep: STEPS.cloneLocal,
    lastError: null,
    createdAt: now,
    updatedAt: now,
  };
}

function createCheckpointState(sourceRepoPath, targetRepoPath, originUrl, defaultBranch) {
  const now = new Date().toISOString();
  return {
    version: CHECKPOINT_VERSION,
    sourceRepoPath,
    targetRepoPath,
    originUrl,
    defaultBranch,
    currentStep: null,
    completedSteps: [],
    stepResults: {},
    lastError: null,
    createdAt: now,
    updatedAt: now,
  };
}

function normalizeLastErrorState(rawLastError) {
  if (!isObject(rawLastError)) {
    return null;
  }
  return {
    step: typeof rawLastError["step"] === "string" ? rawLastError["step"] : "",
    message: typeof rawLastError["message"] === "string" ? rawLastError["message"] : "",
    stderrSnippet: typeof rawLastError["stderrSnippet"] === "string" ? rawLastError["stderrSnippet"] : "",
    timestamp: typeof rawLastError["timestamp"] === "string" ? rawLastError["timestamp"] : "",
    resumeHint: typeof rawLastError["resumeHint"] === "string" ? rawLastError["resumeHint"] : "",
  };
}

function normalizeCheckpointState(parsed, stateFilePath) {
  if (!isObject(parsed)) {
    throw new UsageError(`Checkpoint state is malformed: ${stateFilePath}`);
  }
  if (parsed["version"] !== CHECKPOINT_VERSION) {
    throw new UsageError(`Unsupported checkpoint version in ${stateFilePath}. Use --reset-state to discard it.`);
  }
  const completedSteps = Array.isArray(parsed["completedSteps"])
    ? parsed["completedSteps"].filter((value) => typeof value === "string" && value.length > 0)
    : [];
  const stepResults = isObject(parsed["stepResults"]) ? parsed["stepResults"] : {};
  const lastError = normalizeLastErrorState(parsed["lastError"]);

  return {
    version: CHECKPOINT_VERSION,
    sourceRepoPath: typeof parsed["sourceRepoPath"] === "string" ? parsed["sourceRepoPath"] : "",
    targetRepoPath: typeof parsed["targetRepoPath"] === "string" ? parsed["targetRepoPath"] : "",
    originUrl: typeof parsed["originUrl"] === "string" ? parsed["originUrl"] : "",
    defaultBranch: typeof parsed["defaultBranch"] === "string" ? parsed["defaultBranch"] : "",
    currentStep: typeof parsed["currentStep"] === "string" && parsed["currentStep"].length > 0 ? parsed["currentStep"] : null,
    completedSteps,
    stepResults,
    lastError,
    createdAt: typeof parsed["createdAt"] === "string" ? parsed["createdAt"] : "",
    updatedAt: typeof parsed["updatedAt"] === "string" ? parsed["updatedAt"] : "",
  };
}

function readCheckpointState(stateFilePath) {
  let rawContents;
  try {
    rawContents = readFileSync(stateFilePath, "utf8");
  }
  catch (error) {
    throw new UsageError(`Cannot read checkpoint state ${stateFilePath}: ${String(error)}`);
  }

  let parsed;
  try {
    parsed = JSON.parse(rawContents);
  }
  catch {
    throw new UsageError(`Checkpoint state is not valid JSON: ${stateFilePath}. Use --reset-state to discard it.`);
  }
  return normalizeCheckpointState(parsed, stateFilePath);
}

function readCheckpointStateIfExists(stateFilePath) {
  if (!existsSync(stateFilePath)) {
    return null;
  }
  return readCheckpointState(stateFilePath);
}

function normalizeBootstrapCheckpointState(parsed, stateFilePath) {
  if (!isObject(parsed)) {
    throw new UsageError(`Bootstrap checkpoint is malformed: ${stateFilePath}`);
  }
  if (parsed["version"] !== CHECKPOINT_VERSION) {
    throw new UsageError(`Unsupported bootstrap checkpoint version in ${stateFilePath}. Use --reset-state to discard it.`);
  }

  const lastError = normalizeLastErrorState(parsed["lastError"]);

  return {
    version: CHECKPOINT_VERSION,
    sourceRepoPath: typeof parsed["sourceRepoPath"] === "string" ? parsed["sourceRepoPath"] : "",
    targetRepoPath: typeof parsed["targetRepoPath"] === "string" ? parsed["targetRepoPath"] : "",
    currentStep: typeof parsed["currentStep"] === "string" && parsed["currentStep"].length > 0 ? parsed["currentStep"] : STEPS.cloneLocal,
    lastError,
    createdAt: typeof parsed["createdAt"] === "string" ? parsed["createdAt"] : "",
    updatedAt: typeof parsed["updatedAt"] === "string" ? parsed["updatedAt"] : "",
  };
}

function readBootstrapCheckpointState(stateFilePath) {
  let rawContents;
  try {
    rawContents = readFileSync(stateFilePath, "utf8");
  }
  catch (error) {
    throw new UsageError(`Cannot read bootstrap checkpoint ${stateFilePath}: ${String(error)}`);
  }

  let parsed;
  try {
    parsed = JSON.parse(rawContents);
  }
  catch {
    throw new UsageError(`Bootstrap checkpoint is not valid JSON: ${stateFilePath}. Use --reset-state to discard it.`);
  }
  return normalizeBootstrapCheckpointState(parsed, stateFilePath);
}

function readBootstrapCheckpointStateIfExists(stateFilePath) {
  if (!existsSync(stateFilePath)) {
    return null;
  }
  return readBootstrapCheckpointState(stateFilePath);
}

function writeJsonFileAtomically(filePath, payload) {
  const payloadJson = `${JSON.stringify(payload, null, 2)}\n`;
  const tempFilePath = `${filePath}.${process["pid"]}.tmp`;
  writeFileSync(tempFilePath, payloadJson, "utf8");
  renameSync(tempFilePath, filePath);
}

function writeCheckpointState(stateFilePath, state) {
  const stateDirectoryPath = dirname(stateFilePath);
  if (!existsSync(stateDirectoryPath)) {
    mkdirSync(stateDirectoryPath, {recursive: true});
  }

  const normalizedCompletedSteps = [...new Set(state.completedSteps)];
  const updatedAt = new Date().toISOString();
  const payload = {
    ...state,
    completedSteps: normalizedCompletedSteps,
    updatedAt,
    version: CHECKPOINT_VERSION,
  };
  writeJsonFileAtomically(stateFilePath, payload);

  state.completedSteps = normalizedCompletedSteps;
  state.updatedAt = updatedAt;
}

function writeBootstrapCheckpointState(stateFilePath, state) {
  const directoryPath = dirname(stateFilePath);
  if (!existsSync(directoryPath)) {
    mkdirSync(directoryPath, {recursive: true});
  }

  const updatedAt = new Date().toISOString();
  const payload = {
    ...state,
    updatedAt,
    version: CHECKPOINT_VERSION,
  };
  writeJsonFileAtomically(stateFilePath, payload);

  state.updatedAt = updatedAt;
}

function deleteCheckpointStateIfExists(stateFilePath) {
  if (!existsSync(stateFilePath)) {
    return false;
  }
  unlinkSync(stateFilePath);
  return true;
}

function deleteBootstrapCheckpointStateIfExists(stateFilePath) {
  if (!existsSync(stateFilePath)) {
    return false;
  }
  unlinkSync(stateFilePath);
  return true;
}

function buildResumeCommand(sourceRepoPath, targetRepoPath) {
  return formatCommand("bun", ["community/tools/clone-from-local-cache.mjs", sourceRepoPath, targetRepoPath]);
}

function getStderrSnippet(error) {
  if (!(error instanceof GitError)) {
    return "";
  }
  const details = error.stderr.trim();
  if (details.length === 0) {
    return "";
  }
  return details.split(/\r?\n/).slice(0, 12).join("\n");
}

function summarizeError(error) {
  if (error instanceof GitError) {
    const snippet = getStderrSnippet(error);
    if (snippet.length > 0) {
      return snippet.split(/\r?\n/, 1)[0];
    }
    return `Command failed: ${formatCommand("git", error.args)}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function hasSshAuthFailure(stderr) {
  if (stderr.length === 0) {
    return false;
  }
  const lowered = stderr.toLowerCase();
  return lowered.includes("sign_and_send_pubkey")
    || lowered.includes("permission denied (publickey)")
    || lowered.includes("communication with agent failed");
}

function getFailureContext(error) {
  if (!isObject(error)) {
    return null;
  }
  return isObject(error["cloneFromLocalCacheContext"])
    ? error["cloneFromLocalCacheContext"]
    : null;
}

function setFailureContext(error, context) {
  if (isObject(error)) {
    error["cloneFromLocalCacheContext"] = context;
  }
}

function ensureStepCompleted(state, stepId, result) {
  if (!state.completedSteps.includes(stepId)) {
    state.completedSteps.push(stepId);
  }
  if (result !== undefined) {
    state.stepResults[stepId] = result;
  }
  state.currentStep = null;
  state.lastError = null;
}

function getNextPendingStep(state) {
  for (const stepId of STEP_SEQUENCE) {
    if (!state.completedSteps.includes(stepId)) {
      return stepId;
    }
  }
  return null;
}

function invalidateStepsFrom(state, stepId) {
  const startIndex = STEP_SEQUENCE.indexOf(stepId);
  if (startIndex < 0) {
    return;
  }

  const invalidated = new Set(STEP_SEQUENCE.slice(startIndex));
  state.completedSteps = state.completedSteps.filter((value) => !invalidated.has(value));
  for (const invalidatedStepId of invalidated) {
    delete state.stepResults[invalidatedStepId];
  }
  if (state.currentStep && invalidated.has(state.currentStep)) {
    state.currentStep = null;
  }
}

function runCheckpointedStep(stepId, description, context, action) {
  const {state, stateFilePath, useColor, resumeCommand} = context;
  if (state.completedSteps.includes(stepId)) {
    printStepUpdate(stepId, "SKIP", "already completed", useColor);
    return state.stepResults[stepId];
  }

  printStepUpdate(stepId, "START", description, useColor);
  state.currentStep = stepId;
  state.lastError = null;
  writeCheckpointState(stateFilePath, state);

  try {
    const result = action();
    ensureStepCompleted(state, stepId, result);
    writeCheckpointState(stateFilePath, state);
    printStepUpdate(stepId, "OK", "", useColor);
    return result;
  }
  catch (error) {
    const stderrSnippet = getStderrSnippet(error);
    const message = summarizeError(error);

    state.currentStep = stepId;
    state.lastError = {
      step: stepId,
      message,
      stderrSnippet,
      timestamp: new Date().toISOString(),
      resumeHint: resumeCommand,
    };
    writeCheckpointState(stateFilePath, state);

    setFailureContext(error, {
      resumeCommand,
      sshAuthFailure: hasSshAuthFailure(stderrSnippet),
      stateFilePath,
      stepId,
    });

    printStepUpdate(stepId, "FAIL", message, useColor);
    throw error;
  }
}

function printRecoveryHints(context, useColor) {
  if (!isObject(context)) {
    return;
  }

  const stepId = typeof context["stepId"] === "string" ? context["stepId"] : "";
  const stateFilePath = typeof context["stateFilePath"] === "string" ? context["stateFilePath"] : "";
  const resumeCommand = typeof context["resumeCommand"] === "string" ? context["resumeCommand"] : "";
  const sshAuthFailure = context["sshAuthFailure"] === true;

  if (stepId.length > 0) {
    printWarning(`Failed at step: ${stepId}`, useColor);
  }
  if (stateFilePath.length > 0) {
    printWarning(`Progress checkpoint saved: ${stateFilePath}`, useColor);
  }
  if (resumeCommand.length > 0) {
    printInfo(`Resume command: ${resumeCommand}`, useColor);
  }
  if (sshAuthFailure) {
    printWarning("Detected SSH publickey authentication failure. Fix SSH agent/key setup, then rerun the resume command.", useColor);
    console.log([
      "Suggested checks:",
      "  ssh-add -l",
      "  ssh-add <path-to-your-private-key>",
      "  ssh -T git@git.jetbrains.team",
    ].join("\n"));
  }
}

function printCheckpointStatus(cliSourceRepoPath, targetRepoPath, useColor) {
  const stateFilePath = getCheckpointStatePath(targetRepoPath);
  const bootstrapStateFilePath = getBootstrapCheckpointPath(targetRepoPath);
  printKeyValue("Target", targetRepoPath, useColor);
  printKeyValue("Checkpoint", stateFilePath, useColor);
  printKeyValue("Bootstrap checkpoint", bootstrapStateFilePath, useColor);

  const state = readCheckpointStateIfExists(stateFilePath);
  const bootstrapState = readBootstrapCheckpointStateIfExists(bootstrapStateFilePath);

  if (!state && !bootstrapState) {
    if (!existsSync(targetRepoPath)) {
      printWarning("Target path does not exist.", useColor);
    }
    else {
      ensureDirectoryExists(targetRepoPath, "Target path");
      printWarning("No checkpoint state files found.", useColor);
    }
    return;
  }

  if (state) {
    printInfo("Primary checkpoint state:", useColor);
    printKeyValue("Source", state.sourceRepoPath || "(unknown)", useColor);
    printKeyValue("Origin URL", state.originUrl || "(unknown)", useColor);
    printKeyValue("Default branch", state.defaultBranch || "(unknown)", useColor);
    printKeyValue("Current step", state.currentStep ?? "(none)", useColor);
    printKeyValue("Completed steps", String(state.completedSteps.length), useColor);
    if (state.completedSteps.length > 0) {
      console.log(`  ${state.completedSteps.join(", ")}`);
    }
    const nextStep = getNextPendingStep(state);
    printKeyValue("Next step", nextStep ?? "(none)", useColor);
    if (state.lastError && state.lastError.message.length > 0) {
      printWarning(`Last error (${state.lastError.step || "unknown"}): ${state.lastError.message}`, useColor);
      if (state.lastError.stderrSnippet.length > 0) {
        console.log(state.lastError.stderrSnippet);
      }
    }
    const resumeSource = state.sourceRepoPath || cliSourceRepoPath;
    const resumeTarget = state.targetRepoPath || targetRepoPath;
    printInfo(`Resume command: ${buildResumeCommand(resumeSource, resumeTarget)}`, useColor);
  }

  if (bootstrapState) {
    if (state) {
      printWarning("Bootstrap checkpoint also exists and will be ignored because a primary checkpoint is present.", useColor);
      return;
    }

    printInfo("Bootstrap checkpoint state:", useColor);
    printKeyValue("Source", bootstrapState.sourceRepoPath || "(unknown)", useColor);
    printKeyValue("Current step", bootstrapState.currentStep || STEPS.cloneLocal, useColor);
    if (bootstrapState.lastError && bootstrapState.lastError.message.length > 0) {
      printWarning(`Last error (${bootstrapState.lastError.step || "unknown"}): ${bootstrapState.lastError.message}`, useColor);
      if (bootstrapState.lastError.stderrSnippet.length > 0) {
        console.log(bootstrapState.lastError.stderrSnippet);
      }
    }
    const resumeSource = bootstrapState.sourceRepoPath || cliSourceRepoPath;
    const resumeTarget = bootstrapState.targetRepoPath || targetRepoPath;
    printInfo(`Resume command: ${buildResumeCommand(resumeSource, resumeTarget)}`, useColor);
  }
}

function resetCheckpointState(targetRepoPath, useColor) {
  const stateFilePath = getCheckpointStatePath(targetRepoPath);
  const bootstrapStateFilePath = getBootstrapCheckpointPath(targetRepoPath);
  const deletedPrimary = deleteCheckpointStateIfExists(stateFilePath);
  const deletedBootstrap = deleteBootstrapCheckpointStateIfExists(bootstrapStateFilePath);

  if (!deletedPrimary && !deletedBootstrap) {
    printWarning(`No checkpoint state files found at ${stateFilePath} or ${bootstrapStateFilePath}`, useColor);
    return;
  }

  if (deletedPrimary) {
    printSuccess(`Removed checkpoint state file: ${stateFilePath}`, useColor);
  }
  if (deletedBootstrap) {
    printSuccess(`Removed bootstrap checkpoint state file: ${bootstrapStateFilePath}`, useColor);
  }
}

function resolveRunMode(targetRepoPath, stateFilePath, bootstrapStateFilePath) {
  const state = readCheckpointStateIfExists(stateFilePath);
  const bootstrapState = readBootstrapCheckpointStateIfExists(bootstrapStateFilePath);

  if (state) {
    return {
      mode: "resume",
      bootstrapState,
      state,
    };
  }
  if (bootstrapState) {
    return {
      mode: "resume-bootstrap",
      bootstrapState,
      state: null,
    };
  }
  if (!existsSync(targetRepoPath)) {
    return {
      mode: "fresh",
      bootstrapState: null,
      state: null,
    };
  }

  ensureDirectoryExists(targetRepoPath, "Target path");
  throw new UsageError(
    [
      `Target path already exists without checkpoint state: ${targetRepoPath}`,
      `Expected checkpoint file: ${stateFilePath}`,
      `Expected bootstrap checkpoint file: ${bootstrapStateFilePath}`,
      "If you want a clean restart, remove target directory and rerun.",
    ].join(" "),
  );
}

function validateCheckpointForResume(state, sourceRepoPath, targetRepoPath, stateFilePath) {
  if (state.sourceRepoPath !== sourceRepoPath) {
    throw new UsageError(
      [
        `Checkpoint source mismatch in ${stateFilePath}.`,
        `Expected: ${sourceRepoPath}`,
        `Recorded: ${state.sourceRepoPath || "(empty)"}`,
        "Use --reset-state to discard stale checkpoint.",
      ].join(" "),
    );
  }
  if (state.targetRepoPath !== targetRepoPath) {
    throw new UsageError(
      [
        `Checkpoint target mismatch in ${stateFilePath}.`,
        `Expected: ${targetRepoPath}`,
        `Recorded: ${state.targetRepoPath || "(empty)"}`,
        "Use --reset-state to discard stale checkpoint.",
      ].join(" "),
    );
  }
}

function validateBootstrapCheckpointForResume(state, sourceRepoPath, targetRepoPath, stateFilePath) {
  if (state.sourceRepoPath !== sourceRepoPath) {
    throw new UsageError(
      [
        `Bootstrap checkpoint source mismatch in ${stateFilePath}.`,
        `Expected: ${sourceRepoPath}`,
        `Recorded: ${state.sourceRepoPath || "(empty)"}`,
        "Use --reset-state to discard stale checkpoint.",
      ].join(" "),
    );
  }
  if (state.targetRepoPath !== targetRepoPath) {
    throw new UsageError(
      [
        `Bootstrap checkpoint target mismatch in ${stateFilePath}.`,
        `Expected: ${targetRepoPath}`,
        `Recorded: ${state.targetRepoPath || "(empty)"}`,
        "Use --reset-state to discard stale checkpoint.",
      ].join(" "),
    );
  }
}

function isGitWorkTree(repoPath, options) {
  if (!existsSync(repoPath)) {
    return false;
  }

  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "rev-parse", "--is-inside-work-tree"], {
    allowFailure: true,
    verbose,
    useColor,
  });
  return result.status === 0 && result.stdout.trim() === "true";
}

function markBootstrapFailure(state, stateFilePath, stepId, error, resumeCommand) {
  const stderrSnippet = getStderrSnippet(error);
  const message = summarizeError(error);
  state.currentStep = stepId;
  state.lastError = {
    step: stepId,
    message,
    stderrSnippet,
    timestamp: new Date().toISOString(),
    resumeHint: resumeCommand,
  };
  writeBootstrapCheckpointState(stateFilePath, state);

  setFailureContext(error, {
    resumeCommand,
    sshAuthFailure: hasSshAuthFailure(stderrSnippet),
    stateFilePath,
    stepId,
  });
  return message;
}

function runCloneWithBootstrapCheckpoint(sourceRepoPath, targetRepoPath, originUrl, defaultBranch, options) {
  const {
    bootstrapStateFilePath,
    gitOptions,
    resumeCommand,
    stateFilePath,
    useColor,
    existingBootstrapState,
  } = options;

  let bootstrapState = existingBootstrapState;
  if (!bootstrapState) {
    bootstrapState = createBootstrapCheckpointState(sourceRepoPath, targetRepoPath);
    writeBootstrapCheckpointState(bootstrapStateFilePath, bootstrapState);
  }
  else {
    validateBootstrapCheckpointForResume(bootstrapState, sourceRepoPath, targetRepoPath, bootstrapStateFilePath);
  }

  const cloneArgs = buildCloneArgs(sourceRepoPath, targetRepoPath);

  if (isGitWorkTree(targetRepoPath, gitOptions)) {
    printStepUpdate(STEPS.cloneLocal, "SKIP", "target already contains a git work tree", useColor);
  }
  else {
    if (existsSync(targetRepoPath)) {
      printWarning(`Removing incomplete clone target before retry: ${targetRepoPath}`, useColor);
      rmSync(targetRepoPath, {recursive: true, force: true});
    }

    printStepUpdate(STEPS.cloneLocal, "START", "clone target repository from local source", useColor);
    bootstrapState.currentStep = STEPS.cloneLocal;
    bootstrapState.lastError = null;
    writeBootstrapCheckpointState(bootstrapStateFilePath, bootstrapState);

    try {
      runGit(cloneArgs, {
        ...gitOptions,
        captureStreamOutput: true,
        streamOutput: true,
      });
      printStepUpdate(STEPS.cloneLocal, "OK", "", useColor);
    }
    catch (error) {
      const message = markBootstrapFailure(bootstrapState, bootstrapStateFilePath, STEPS.cloneLocal, error, resumeCommand);
      printStepUpdate(STEPS.cloneLocal, "FAIL", message, useColor);
      throw error;
    }
  }

  const state = createCheckpointState(sourceRepoPath, targetRepoPath, originUrl, defaultBranch);
  ensureStepCompleted(state, STEPS.cloneLocal, {createdTarget: true});
  writeCheckpointState(stateFilePath, state);
  deleteBootstrapCheckpointStateIfExists(bootstrapStateFilePath);
  printInfo(`Checkpoint created: ${stateFilePath}`, useColor);
  return state;
}

function run() {
  const rawArgv = process["argv"];
  const argv = Array.isArray(rawArgv) ? rawArgv.slice(2) : [];
  const args = parseArguments(argv);
  const {
    dryRun,
    fetchDiagnostics,
    resetState,
    sourceRepoPath,
    statusOnly,
    targetRepoPath,
    useColor,
    verbose,
  } = args;

  const stateFilePath = getCheckpointStatePath(targetRepoPath);
  const bootstrapStateFilePath = getBootstrapCheckpointPath(targetRepoPath);

  if (statusOnly) {
    printCheckpointStatus(sourceRepoPath, targetRepoPath, useColor);
    return;
  }
  if (resetState) {
    resetCheckpointState(targetRepoPath, useColor);
    return;
  }

  const gitOptions = {verbose, useColor};

  ensureDirectoryExists(sourceRepoPath, "Source path");
  ensureSourceIsGitRepository(sourceRepoPath, gitOptions);

  const originUrl = detectOriginUrl(sourceRepoPath, gitOptions);
  const defaultBranch = detectOriginDefaultBranch(sourceRepoPath, gitOptions);
  const resumeCommand = buildResumeCommand(sourceRepoPath, targetRepoPath);
  const cloneArgs = buildCloneArgs(sourceRepoPath, targetRepoPath);
  const runMode = resolveRunMode(targetRepoPath, stateFilePath, bootstrapStateFilePath);

  if (verbose || dryRun) {
    printKeyValue("Source", sourceRepoPath, useColor);
    printKeyValue("Target", targetRepoPath, useColor);
    printKeyValue("Origin URL", originUrl, useColor);
    printKeyValue("Default branch", defaultBranch, useColor);
    printKeyValue("Fetch strategy", FETCH_STRATEGY_NEGOTIATION_FIRST, useColor);
    printKeyValue("Fetch diagnostics", fetchDiagnostics ? "enabled" : "disabled", useColor);
    printKeyValue("Checkpoint", stateFilePath, useColor);
    printKeyValue("Bootstrap checkpoint", bootstrapStateFilePath, useColor);
    printKeyValue("Resume command", colorize(resumeCommand, ANSI.cyan, useColor), useColor);
    if (runMode.mode === "fresh") {
      printKeyValue("Clone command", colorize(formatCommand("git", cloneArgs), ANSI.cyan, useColor), useColor);
    }
    else {
      printKeyValue("Run mode", runMode.mode, useColor);
    }
  }

  if (dryRun) {
    if (runMode.mode === "fresh") {
      const sourceConfig = groupEntriesByKey(listLocalConfigEntries(sourceRepoPath, gitOptions));
      printWarning(`Dry run: clone skipped. ${sourceConfig.size} local config keys would be mirrored after clone.`, useColor);
      printInfo("Dry run plan: clone with --local, write checkpoint, set origin URL, prepare fetch config, cleanup refs/tags (non-destructive), fetch --prune origin, checkout/reset/clean, prune branches, sync config.", useColor);
    }
    else if (runMode.mode === "resume") {
      const nextStep = getNextPendingStep(runMode.state);
      printWarning(`Dry run: target already exists. Resume would continue from ${nextStep ?? "(completed)"}.`, useColor);
    }
    else {
      printWarning("Dry run: target already exists. Resume would continue from clone_local bootstrap step.", useColor);
    }
    printInfo(`Dry run fetch strategy: ${FETCH_STRATEGY_NEGOTIATION_FIRST}`, useColor);
    return;
  }

  let state;
  if (runMode.mode === "fresh" || runMode.mode === "resume-bootstrap") {
    state = runCloneWithBootstrapCheckpoint(sourceRepoPath, targetRepoPath, originUrl, defaultBranch, {
      bootstrapStateFilePath,
      existingBootstrapState: runMode.bootstrapState,
      gitOptions,
      resumeCommand,
      stateFilePath,
      useColor,
    });
  }
  else {
    state = runMode.state;

    if (runMode.bootstrapState) {
      printWarning("Ignoring stale bootstrap checkpoint because primary checkpoint exists.", useColor);
      deleteBootstrapCheckpointStateIfExists(bootstrapStateFilePath);
    }

    validateCheckpointForResume(state, sourceRepoPath, targetRepoPath, stateFilePath);
    ensureDirectoryExists(resolve(targetRepoPath, ".git"), "Target .git directory");

    if (!state.completedSteps.includes(STEPS.cloneLocal)) {
      ensureStepCompleted(state, STEPS.cloneLocal, {createdTarget: true});
      writeCheckpointState(stateFilePath, state);
    }

    if (state.originUrl !== originUrl || state.defaultBranch !== defaultBranch) {
      printWarning("Source origin/default branch changed since last checkpoint; rerunning normalization from set_origin_url.", useColor);
      state.originUrl = originUrl;
      state.defaultBranch = defaultBranch;
      invalidateStepsFrom(state, STEPS.setOriginUrl);
      writeCheckpointState(stateFilePath, state);
    }

    const nextStep = getNextPendingStep(state);
    printInfo(`Resuming from checkpoint. Next step: ${nextStep ?? "(completed)"}.`, useColor);
  }

  const checkpointContext = {resumeCommand, state, stateFilePath, useColor};

  runCheckpointedStep(STEPS.setOriginUrl, "set target origin URL", checkpointContext, () => {
    runGit(["-C", targetRepoPath, "remote", "set-url", "origin", originUrl], gitOptions);
    return {originUrl};
  });

  runCheckpointedStep(STEPS.prepareNormalizationConfig, "prepare normalization fetch config", checkpointContext, () => {
    prepareTargetConfigForNormalizationFetch(targetRepoPath, gitOptions);
    return {prepared: true};
  });

  runCheckpointedStep(STEPS.removeInheritedRefsAndTags, "cleanup inherited refs/tags", checkpointContext, () => {
    return {
      removedRemoteRefs: 0,
      removedTags: 0,
      skipped: true,
    };
  });

  runCheckpointedStep(STEPS.fetchPruneOrigin, "fetch and prune origin", checkpointContext, () => {
    const originRefsBeforeFetch = fetchDiagnostics ? listRefNames(targetRepoPath, "refs/remotes/origin", gitOptions).length : 0;
    const fetchStartTime = Date.now();
    const fetchCommand = buildFetchArgs(targetRepoPath, defaultBranch, gitOptions);

    runGit(fetchCommand.args, {
      ...gitOptions,
      captureStreamOutput: true,
      streamOutput: true,
    });

    const fetchElapsedMs = Date.now() - fetchStartTime;
    const originRefsAfterFetch = fetchDiagnostics ? listRefNames(targetRepoPath, "refs/remotes/origin", gitOptions).length : 0;
    ensureRemoteBranchFetched(targetRepoPath, defaultBranch, gitOptions);

    if (fetchDiagnostics) {
      printInfo(
        [
          "Fetch diagnostics:",
          `strategy=${FETCH_STRATEGY_NEGOTIATION_FIRST}`,
          `negotiationTip=${fetchCommand.negotiationTip.length > 0 ? fetchCommand.negotiationTip : "none"}`,
          `originRefsBefore=${originRefsBeforeFetch}`,
          `originRefsAfter=${originRefsAfterFetch}`,
          `elapsedMs=${fetchElapsedMs}`,
        ].join(" "),
        useColor,
      );
    }

    return {
      defaultBranch,
      elapsedMs: fetchElapsedMs,
      negotiationTip: fetchCommand.negotiationTip,
      originRefsAfterFetch,
      originRefsBeforeFetch,
      strategy: FETCH_STRATEGY_NEGOTIATION_FIRST,
    };
  });

  runCheckpointedStep(STEPS.checkoutResetClean, `checkout/reset/clean ${defaultBranch}`, checkpointContext, () => {
    runGit(["-C", targetRepoPath, "checkout", "-B", defaultBranch, `origin/${defaultBranch}`], gitOptions);
    runGit(["-C", targetRepoPath, "reset", "--hard", `origin/${defaultBranch}`], gitOptions);
    runGit(["-C", targetRepoPath, "clean", "-fd"], gitOptions);
    return {defaultBranch};
  });

  runCheckpointedStep(STEPS.pruneLocalBranches, "prune non-default local branches", checkpointContext, () => {
    return pruneLocalBranchesExceptDefault(targetRepoPath, defaultBranch, gitOptions);
  });

  runCheckpointedStep(STEPS.syncLocalConfig, "sync final local git config from source", checkpointContext, () => {
    return syncLocalConfig(sourceRepoPath, targetRepoPath, gitOptions);
  });

  const removedRefsAndTags = isObject(state.stepResults[STEPS.removeInheritedRefsAndTags])
    ? state.stepResults[STEPS.removeInheritedRefsAndTags]
    : {};
  const removedRemoteRefs = Number(removedRefsAndTags["removedRemoteRefs"] ?? 0);
  const removedTags = Number(removedRefsAndTags["removedTags"] ?? 0);
  const prunedLocalBranches = Number(state.stepResults[STEPS.pruneLocalBranches] ?? 0);
  const changedKeys = Number(state.stepResults[STEPS.syncLocalConfig] ?? 0);

  deleteCheckpointStateIfExists(stateFilePath);
  deleteBootstrapCheckpointStateIfExists(bootstrapStateFilePath);

  printSuccess(
    `Done. Created ${targetRepoPath}. Mirrored ${changedKeys} config key(s), removed ${removedRemoteRefs} remote-tracking ref(s), removed ${removedTags} tag(s), pruned ${prunedLocalBranches} local branch(es).`,
    useColor,
  );
}

try {
  run();
}
catch (error) {
  const useColor = detectColorSupport();
  const failureContext = getFailureContext(error);

  if (error instanceof UsageError) {
    printError(error.message, useColor);
    printRecoveryHints(failureContext, useColor);
    printUsage();
    process.exit(USAGE_EXIT_CODE);
  }
  if (error instanceof GitError) {
    printError(error.message, useColor);
    printRecoveryHints(failureContext, useColor);
    process.exit(COMMAND_EXIT_CODE);
  }

  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  printError(message, useColor);
  printRecoveryHints(failureContext, useColor);
  process.exit(COMMAND_EXIT_CODE);
}
