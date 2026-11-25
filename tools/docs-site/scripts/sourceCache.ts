import { readFileSync } from 'node:fs'
import { readFile } from 'node:fs/promises'

const cache = new Map<string, string>()

/** How many reads of [prefetchSourceFiles] wait at the same time. */
const PREFETCH_READS = 64

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

/**
 * Reads the files of `paths` into the cache of [readSourceFile], [PREFETCH_READS] at a time. The checks read the
 * sources one by one, so a run that knows its sources reads them first, in parallel. A file that cannot be read stays
 * out of the cache, so its first [readSourceFile] call throws the error of the read.
 */
export async function prefetchSourceFiles(paths: Iterable<string>): Promise<void> {
  const pending = [...new Set(paths)].filter(path => !cache.has(path))
  let next = 0
  const reader = async (): Promise<void> => {
    while (next < pending.length) {
      const path = pending[next++]
      try {
        cache.set(path, await readFile(path, 'utf8'))
      }
      catch {
        // readSourceFile reports it
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(PREFETCH_READS, pending.length) }, reader))
}
