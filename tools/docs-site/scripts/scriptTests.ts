// The runner of the script tests. It loads each test file into the process that calls it (`isolation: 'none'` of
// node:test), so a module that many tests import loads once, and no child process starts. `pnpm run test:scripts`
// runs the kernel tests with it. The check of a site runs its own tests and the kernel tests with it, in the process
// of its checks.
//
// Node runs this file. No gate loads it.

import { globSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { run } from 'node:test'
import { spec } from 'node:test/reporters'
import { fileURLToPath } from 'node:url'
import { isMainModule } from './paths.ts'

/** The package directory of the kernel. */
const kernelDir = join(dirname(fileURLToPath(import.meta.url)), '..')

/** The files under `dir` that the globs match, as absolute paths in sorted order. */
export function testFiles(dir: string, globs: readonly string[]): string[] {
  return globSync([...globs], { cwd: dir }).sort().map(file => join(dir, file))
}

/** The test files of the kernel: the tests of the scripts and of the browser modules. */
export function kernelScriptTests(): string[] {
  return testFiles(kernelDir, ['scripts/*.test.ts', 'src/*.test.ts'])
}

/**
 * Runs the tests of `files` in this process and writes the spec report to stdout. Returns true when the run passes.
 * A failed test, a failed hook, and a file that does not load fail the run.
 */
export async function runScriptTests(files: readonly string[]): Promise<boolean> {
  if (files.length === 0) {
    throw new Error('runScriptTests: no test file')
  }
  let success = false
  const stream = run({ files: [...files], isolation: 'none' })
  stream.on('test:summary', (summary) => {
    // the summary of the whole run has no file
    if (summary.file === undefined) {
      success = summary.success
    }
  })
  for await (const chunk of stream.compose(spec)) {
    process.stdout.write(chunk as string)
  }
  return success
}

// --- CLI: `node scripts/scriptTests.ts` ------------------------------------------------------------

if (isMainModule(import.meta.url)) {
  if (!await runScriptTests(kernelScriptTests())) {
    process.exitCode = 1
  }
}
