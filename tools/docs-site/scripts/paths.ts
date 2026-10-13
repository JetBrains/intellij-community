// The repository paths of a site, from its config, and the file walk that the checks share. Node side only.

import { existsSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import type { SiteConfig } from './siteConfig.ts'

/** The repository root: the kernel sits at `community/tools/docs-site`. */
export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..')

/** The package directory of the site. */
export function siteDir(config: Pick<SiteConfig, 'siteRoot'>, root: string = repoRoot): string {
  return join(root, config.siteRoot)
}

/** The content root of the site: every doc path of the site is relative to it. */
export function contentDir(config: Pick<SiteConfig, 'contentRoot'>, root: string = repoRoot): string {
  return join(root, config.contentRoot)
}

/** The output directory of the site build. */
export function outDir(config: Pick<SiteConfig, 'outDir'>, root: string = repoRoot): string {
  return join(root, config.outDir)
}

/** The POSIX path of `path` relative to `from`. */
export function posixRelative(from: string, path: string): string {
  return relative(from, path).split(sep).join('/')
}

/**
 * Calls `visit` with every file under `dir`, depth first, with the entries of a directory in sorted order. An entry
 * that `skips` accepts is not entered and not visited. A `dir` that does not exist has no files.
 */
export function walkFiles(dir: string, skips: (entry: string) => boolean, visit: (path: string) => void): void {
  if (!existsSync(dir)) {
    return
  }
  for (const entry of readdirSync(dir).sort()) {
    if (skips(entry)) {
      continue
    }
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      walkFiles(path, skips, visit)
    }
    else {
      visit(path)
    }
  }
}

/** Loads the default export of a `site.config.ts` module. A relative path resolves against the working directory. */
export async function loadSiteConfig(path: string | undefined): Promise<SiteConfig> {
  if (path === undefined) {
    throw new Error('usage: node <check>.ts SITE_CONFIG, where SITE_CONFIG is the site.config.ts of the site')
  }
  const module = await import(pathToFileURL(resolve(path)).href) as { default?: SiteConfig }
  if (module.default === undefined) {
    throw new Error(`${path}: the module has no default export`)
  }
  return module.default
}

/** True when the module at `moduleUrl` is the script that node or bun runs. */
export function isMainModule(moduleUrl: string): boolean {
  return process.argv[1] === fileURLToPath(moduleUrl)
}

/** Runs the CLI of one check: loads the site config of the first argument, and prints the result or the error. */
export async function runCheck(name: string, check: (config: SiteConfig) => string): Promise<void> {
  try {
    console.log(`${name}: ok (${check(await loadSiteConfig(process.argv[2]))})`)
  }
  catch (error) {
    console.error(error instanceof Error ? error.message : String(error))
    process.exitCode = 1
  }
}
