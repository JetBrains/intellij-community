// The protocol of the docs gate of a site. The Rust crate `docs-gate` (community/build/dist-tools/crates) runs the
// gate script of a site on the bun that Bazel declares, and reads its output:
//   - stdout starts with the `docs-gate 1` sentinel line, printed before any check module loads, so wrapper or
//     bootstrap noise cannot be mistaken for it.
//   - exit 0: each check passed. The records of the site follow the sentinel, one per line.
//   - exit 3: a check failed. The report follows the sentinel.
//   - exit 4: infrastructure past the sentinel, such as an OpenSpec CLI that gives no report. The report follows.
//   - anything else, or no sentinel: the gate cannot run.
//
// This module has no static import, so the gate script of a site imports it first and loads its checks in the body.

/** The first line of the output of a gate. */
export const GATE_SENTINEL = 'docs-gate 1'

/** The exit code of a gate whose check failed. */
export const CHECK_FAILED_EXIT_CODE = 3

/** The exit code of a gate that started and could not finish. */
export const INFRASTRUCTURE_EXIT_CODE = 4

/**
 * Writes the sentinel, then the record lines that `body` returns, and answers the exit code. A throw of `body` writes
 * its report instead of the records. An `OpenSpecInfrastructureError` gives the infrastructure exit, and each other
 * throw gives the exit of a failed check, because a failure past the sentinel is repo state or a bug in the checks.
 */
export async function gateExitCode(body: () => Promise<readonly string[]>, write: (line: string) => void): Promise<number> {
  write(GATE_SENTINEL)
  try {
    write((await body()).join('\n'))
    return 0
  }
  catch (error) {
    write(error instanceof Error ? error.stack ?? error.message : String(error))
    const { OpenSpecInfrastructureError } = await import('./openspec.ts')
    return error instanceof OpenSpecInfrastructureError ? INFRASTRUCTURE_EXIT_CODE : CHECK_FAILED_EXIT_CODE
  }
}

/** Runs the gate of a site on stdout, and sets the exit code of the process. `body` loads the checks dynamically. */
export async function runGate(body: () => Promise<readonly string[]>): Promise<void> {
  process.exitCode = await gateExitCode(body, line => console.log(line))
}
