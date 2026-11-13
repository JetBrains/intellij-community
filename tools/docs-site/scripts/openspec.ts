// The OpenSpec check of a site: the structure rules of OpenSpec, such as a Purpose, a SHALL or MUST and a scenario,
// over each OpenSpec root of `specs.openspec`. The CLI owns these rules, and this module runs it. The references, the
// anchors and the sentence budget stay with specRefs.ts.
//
// The kernel names no path of a site. The site gives the command that starts the CLI, and `openspecCommand` builds it
// from the wrapper of the repository that the site names. The gate of a site runs on bun without node_modules, so this
// module imports nothing from npm.

import { spawnSync } from 'node:child_process'
import { basename, dirname, join } from 'node:path'
import { posixRelative, repoRoot } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'

/** The part of the site config that the check reads. */
export type OpenSpecConfig = Pick<SiteConfig, 'specs'>

/**
 * The CLI could not run or gave no report that this module can read. It is a defect of the environment or of the test
 * target, not a finding of the specs, so a gate reports it apart from a failed check.
 */
export class OpenSpecInfrastructureError extends Error {
}

/** The environment of each run: no usage data, no update check, no prompt. */
const QUIET_ENVIRONMENT: Readonly<Record<string, string>> = {
  OPENSPEC_TELEMETRY: '0',
  DO_NOT_TRACK: '1',
  OPENSPEC_NO_UPDATE_CHECK: '1',
}

/**
 * The command line that starts the OpenSpec CLI: the runtime of this process with the entry in `OPENSPEC_ENTRY`, or
 * `wrapper`, the wrapper script of the repository.
 *
 * Under `bazel test`, the test target passes the declared entry of the CLI in `OPENSPEC_ENTRY`. The wrapper is refused
 * there, because its build would wait for the output base lock of the outer test.
 */
export function openspecCommand(wrapper: string): string[] {
  const entry = process.env.OPENSPEC_ENTRY
  if (entry !== undefined && entry.length > 0) {
    return [process.execPath, entry]
  }
  if (process.env.TEST_SRCDIR !== undefined) {
    throw new OpenSpecInfrastructureError('openspec: OPENSPEC_ENTRY is missing under bazel test; the test target must pass the declared entry of the OpenSpec CLI')
  }
  // a polyglot `.cmd` has no shebang: a shell runs its bash half, and cmd.exe its CMD half
  return process.platform === 'win32' ? ['cmd.exe', '/c', wrapper] : ['/bin/sh', wrapper]
}

/** The validate runs over one root: the main specs and the active changes, then the archived changes. */
const VALIDATE_RUNS: readonly (readonly string[])[] = [
  ['validate', '--all', '--strict', '--json', '--no-interactive'],
  ['validate', '--archived', '--strict', '--json', '--no-interactive'],
]

interface ValidateIssue {
  level?: string
  path?: string
  message?: string
}

interface ValidateReport {
  items: { id: string, type: string, valid: boolean, issues?: ValidateIssue[] }[]
}

function parseReport(stdout: string): ValidateReport | null {
  const start = stdout.indexOf('{')
  if (start < 0) {
    return null
  }
  try {
    const report = JSON.parse(stdout.slice(start)) as ValidateReport
    return Array.isArray(report.items) ? report : null
  }
  catch {
    return null
  }
}

/**
 * The findings of the CLI over each OpenSpec root, as `<root>: <type> <id>: <level>: <message>`, and the count of the
 * items it checked. `command` starts the CLI, for example `[bun, '<package>/bin/openspec.js']`. The CLI runs in the
 * parent folder of each root, because it finds the nearest `openspec/` folder. Throws `OpenSpecInfrastructureError`
 * when a run does not end with a report.
 */
export function openspecFindings(config: OpenSpecConfig, command: readonly string[], root: string = repoRoot): { findings: string[], items: number } {
  const findings: string[] = []
  let items = 0
  for (const openspecRoot of config.specs.openspec?.roots ?? []) {
    const rootDir = join(root, openspecRoot)
    if (basename(rootDir) !== 'openspec') {
      throw new Error(`specs.openspec.roots: '${openspecRoot}' is not a folder named openspec; the CLI finds only that name`)
    }
    for (const args of VALIDATE_RUNS) {
      const [program, ...prefix] = command
      const run = spawnSync(program, [...prefix, ...args], {
        cwd: dirname(rootDir),
        encoding: 'utf8',
        env: { ...process.env, ...QUIET_ENVIRONMENT },
        stdio: ['ignore', 'pipe', 'pipe'],
      })
      const report = run.error === undefined ? parseReport(run.stdout ?? '') : null
      if (report === null) {
        const reason = run.error?.message ?? `exit ${run.status}, no JSON report`
        throw new OpenSpecInfrastructureError(`openspec: \`${[...command, ...args].join(' ')}\` in ${posixRelative(root, dirname(rootDir)) || '.'} did not report (${reason})\n${(run.stderr ?? '').trim()}`)
      }
      for (const item of report.items) {
        items++
        // with --strict, a warning makes an item invalid too
        if (item.valid) {
          continue
        }
        const issues = item.issues ?? []
        for (const issue of issues) {
          const where = issue.path === undefined || issue.path.length === 0 ? '' : ` (${issue.path})`
          findings.push(`${openspecRoot}: ${item.type} ${item.id}: ${issue.level ?? 'ERROR'}: ${issue.message ?? 'invalid'}${where}`)
        }
        if (issues.length === 0) {
          findings.push(`${openspecRoot}: ${item.type} ${item.id}: ERROR: invalid`)
        }
      }
    }
  }
  return { findings, items }
}

/** Runs the OpenSpec check of the site. Throws with every finding. Returns the count of the items that the CLI checked. */
export function checkOpenSpec(config: OpenSpecConfig, command: readonly string[], root: string = repoRoot): number {
  const { findings, items } = openspecFindings(config, command, root)
  if (findings.length > 0) {
    throw new Error(`openspec: ${findings.length} finding(s); see community/.ai/spec/SPEC_GUIDE.md, section "OpenSpec roots"\n${findings.join('\n')}`)
  }
  return items
}
