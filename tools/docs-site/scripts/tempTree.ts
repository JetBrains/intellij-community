// A temporary file tree for a script test, removed after each test.
//
// A check over the repository (scripts/adrFiles.ts, scripts/proseLint.ts) takes a root. A test builds a small tree
// under the temporary directory of the OS, runs the check against it, and the `afterEach` hook removes the tree.

import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach } from 'node:test'

const roots: string[] = []

/**
 * Creates a temporary root with the given files, relative to the root, and the given empty folders. The root is
 * removed after the current test.
 */
export function temporaryTree(prefix: string, files: Record<string, string>, folders: readonly string[] = []): string {
  const root = mkdtempSync(join(tmpdir(), prefix))
  roots.push(root)
  for (const folder of folders) {
    mkdirSync(join(root, folder), { recursive: true })
  }
  for (const [path, text] of Object.entries(files)) {
    mkdirSync(join(root, path, '..'), { recursive: true })
    writeFileSync(join(root, path), text)
  }
  return root
}

afterEach(() => {
  for (const root of roots.splice(0)) {
    rmSync(root, { recursive: true, force: true })
  }
})
