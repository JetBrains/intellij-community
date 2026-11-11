---
name: pycharm-code-analysis
description: Validate PyCharm source-code edits and Git commits with IDE inspections. Use after editing inspectable code; before preparing, creating, or amending a commit; and when verifying an existing commit, even if the user does not mention linting.
allowed-tools: execute_tool
---

# PyCharm Code Analysis

Use PyCharm's configured inspection profile to validate source files changed by the current task. Keep the check focused; never expand it into unrelated repository cleanup.

Call `execute_tool` from the `jetbrains_air_ide` MCP server when it is available. That server is bound to the current AIR session; a
same-named tool from another MCP server may target a different IDE instance.

## Required Invariants

- After editing source code, run `lint_files` before reporting the task complete.
- Before every requested commit or amend, run the Final Post-Staging Commit Gate below. This gate is mandatory even when the same files and contents passed `lint_files` earlier.
- A clean Final Post-Staging Commit Gate also satisfies the post-edit requirement; do not run an identical extra working analysis first.
- Treat any later staging operation or edit to a staged source file as invalidating the final gate. Restart it from staged-file discovery.
- Tests, builds, `git diff --cached --check`, pre-commit hooks, and earlier `lint_files` calls do not replace the final post-staging analysis.
- Never claim IDE analysis is clean when the required call was skipped, incomplete, timed out, or performed before the last staging operation.

## Working Analysis

Build the working file list from source files edited or created in the current task. For a rename, use the destination path. Exclude deleted files, binaries, generated files, unsupported files, files outside the open project, and unrelated dirty files.

Read [reference/tools/lint_files.md](reference/tools/lint_files.md), then call `lint_files` through `execute_tool` with all selected files in one batch where practical, `min_severity=strong_warning`, and `projectPath` when known:

```text
execute_tool(command="lint_files --files '[\"src/app.py\",\"src/service.py\"]' --min_severity strong_warning --projectPath /path/to/project")
```

This threshold includes IDE `WARNING` and `ERROR` highlights and excludes `WEAK_WARNING` highlights.

Interpret results conservatively:

- `more: true` means incomplete, not clean.
- `timedOut: true` means that file is incomplete. Retry it once in a smaller batch.
- `notAnalyzedReason` is acceptable only for a genuinely unsupported or excluded file. Do not claim verification for a source file that should be inspectable.
- An empty `items` or `problems` array is clean only when the response is complete and accounts for every requested source file.

## Repair Loop

For every error or strong warning:

1. Use the location, description, surrounding code, and `inspectionId` when present to understand the problem.
2. Fix diagnostics introduced by or clearly relevant to the current change.
3. Do not modify unrelated dirty files or broaden the task into pre-existing cleanup.
4. Do not use suppressions, `noqa`, inspection-profile changes, or disabled inspections unless the user explicitly requests that solution.
5. Rerun `lint_files` for repaired files and files that still had relevant diagnostics.

Perform at most three repair cycles. Stop when clean or when another fix would be ambiguous, risky, or unrelated. Report remaining diagnostics and do not create a requested commit without an explicit informed override from the user.

## Final Post-Staging Commit Gate

Before every requested commit or amend, execute this checklist in order:

- [ ] Stage exactly the changes intended for the commit.
- [ ] After the last staging operation, use the shell from each repository root to obtain the complete NUL-delimited staged path list: `git diff --cached --name-only --diff-filter=ACMRT -z`.
- [ ] Include staged added, copied, modified, renamed, and type-changed destination paths. Exclude generated files, binaries, and unsupported files; the diff filter already excludes deletions and untracked-only paths.
- [ ] Stop on conflicts. Check with `git diff --name-only --diff-filter=U -z`; any output means the gate cannot pass.
- [ ] Run `lint_files(min_severity=strong_warning)` for the entire staged source-file list, even if the same files were analyzed earlier and their contents have not changed.
- [ ] Fix relevant diagnostics. Stage only the intended repairs, then rediscover the staged files and lint the entire resulting staged source-file list again.
- [ ] Create or amend the commit only when the final response is clean and neither staged membership nor staged source-file content has changed since that response.

Run the Git commands through the agent's shell, not through an MCP tool:

```shell
git diff --name-only --diff-filter=U -z
git diff --cached --name-only --diff-filter=ACMRT -z
```

If there are no staged source files, record that fact and skip `lint_files`; do not call it with an empty list. A documentation-only or deletion-only commit can therefore pass without source analysis.

`lint_files` analyzes current IDE/VFS content for each staged path, not the Git index blob. When a staged source file also has unstaged edits, analyze it but report that the result covers the working-tree version. Never stage those extra edits merely to simplify verification.

If a formatter, hook, test, or any other process changes a staged source file after the final analysis, stage the intended result and restart the gate. Do not rely on memory or infer that a previous clean result is still valid.

## Retrospective Verification

When asked to verify an already-created commit, derive its changed source-file list from that commit and run `lint_files` on those paths. Report this as retrospective verification, not as a post-staging check. If repairs are necessary and the commit must be amended, stage them and run the full Final Post-Staging Commit Gate first.

## Completion Evidence

Before reporting success, committing, or amending, state:

```text
IDE analysis: <working | final post-staging | retrospective>
Source files: <count and paths>
Diagnostics fixed: <inspection ids/descriptions or none>
Final result: <clean | incomplete | remaining diagnostics>
```

For a commit, `IDE analysis` must say `final post-staging`. Mention skipped, partially staged, timed-out, or not-analyzed files explicitly.

This skill covers IDE daemon/local inspections only. It does not replace tests, builds, global inspections, duplicate-code analysis, or project-wide unused-code analysis.
