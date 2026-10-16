import { readFileSync } from 'node:fs'

const cache = new Map<string, string>()

/**
 * Memoized file read for the repo-scan passes: harvest.ts and generateFlows.ts each revisit the
 * same Kotlin sources several times (declaration index, read-site scans, `@State` / KDoc passes),
 * so the tree is read from disk once per process instead of once per pass.
 */
export function readSourceFile(path: string): string {
  let text = cache.get(path)
  if (text === undefined) {
    text = readFileSync(path, 'utf8')
    cache.set(path, text)
  }
  return text
}
