// The source files that the harvest bundles (the `sources` of the site data). Each file is its own lazy hashed
// chunk; nothing here lands in the main bundle.

import { site } from './site.ts'

export function isBundledSource(path: string): boolean {
  return Object.hasOwn(site().index.data.sources, path)
}

/** Loads a bundled source file's text; the path must be a bundled source. */
export function loadSource(path: string): Promise<string> {
  return site().index.data.sources[path]() as Promise<string>
}
