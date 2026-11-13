// The repository paths of a site, from its config, and the file walk that the checks share. Node side only.

import { existsSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import type { SiteConfig } from './siteConfig.ts'
import { OPENSPEC_SPEC_FILE, specIdOfRepoPath } from './sourcePaths.ts'

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

/** The directories under a spec root that hold no authored spec, in addition to `docs.skipDirs`. */
const SPEC_SKIP_DIRS: readonly string[] = ['node_modules', 'generated', 'test-results']

/** One spec file of a site. */
export interface SpecFile {
  /** The absolute path. */
  file: string
  /** The absolute path of the folder that the id is relative to: a suffix root, or `<root>/specs` of an OpenSpec root. */
  specRoot: string
  /** The spec id (`specIdOfRepoPath`). */
  id: string
  /** True for a spec of an OpenSpec root, whose `targets` and `[@test]` paths are from the repository root. */
  rootPaths: boolean
}

/**
 * The spec files of the site, sorted by path: the specs of each OpenSpec root, then the files of the suffix roots. A
 * suffix spec file belongs to the first root of `specs.roots` that holds it. Throws when two files have one id, so a
 * spec that moves to an OpenSpec root cannot keep its old file.
 */
export function specFiles(config: Pick<SiteConfig, 'docs' | 'specs'>, root: string = repoRoot): SpecFile[] {
  const skipDirs = new Set([...SPEC_SKIP_DIRS, ...config.docs.skipDirs])
  const skips = (entry: string): boolean => skipDirs.has(entry) || entry.startsWith('.')
  const owner = new Map<string, SpecFile>()
  for (const openspecRoot of config.specs.openspec?.roots ?? []) {
    const dir = join(root, openspecRoot, 'specs')
    walkFiles(dir, skips, (file) => {
      const id = specIdOfRepoPath(config.specs, posixRelative(root, file))
      if (id !== null && !owner.has(file)) {
        owner.set(file, { file, specRoot: dir, id, rootPaths: true })
      }
    })
  }
  for (const specRoot of config.specs.roots) {
    const dir = join(root, specRoot)
    walkFiles(dir, skips, (file) => {
      if (file.endsWith(config.specs.suffix) && !owner.has(file)) {
        owner.set(file, { file, specRoot: dir, id: posixRelative(dir, file).slice(0, -config.specs.suffix.length), rootPaths: false })
      }
    })
  }
  const files = [...owner.values()].sort((a, b) => a.file < b.file ? -1 : a.file > b.file ? 1 : 0)
  const byId = new Map<string, string>()
  const duplicates: string[] = []
  for (const { file, id } of files) {
    const first = byId.get(id)
    if (first === undefined) {
      byId.set(id, file)
    }
    else {
      duplicates.push(`'${id}': ${posixRelative(root, first)} and ${posixRelative(root, file)}`)
    }
  }
  if (duplicates.length > 0) {
    throw new Error(`specs: ${duplicates.length} spec id(s) name two files; delete the old file of a moved spec\n${duplicates.join('\n')}`)
  }
  return files
}

/**
 * The delta spec files of the active changes of the OpenSpec roots: `<root>/changes/<change>/specs/**\/spec.md`, sorted.
 * `capabilityDir` is the folder of the main spec that the archive writes the delta into, so a relative link of a delta
 * resolves from there. The archive `changes/archive/` is history, and no check reads it.
 */
export function openspecDeltaFiles(config: Pick<SiteConfig, 'specs'>, root: string = repoRoot): { file: string, capabilityDir: string }[] {
  const deltas: { file: string, capabilityDir: string }[] = []
  for (const openspecRoot of config.specs.openspec?.roots ?? []) {
    const changes = join(root, openspecRoot, 'changes')
    if (!existsSync(changes)) {
      continue
    }
    for (const change of readdirSync(changes).sort()) {
      const specsDir = join(changes, change, 'specs')
      if (change === 'archive' || change.startsWith('.') || !existsSync(specsDir) || !statSync(specsDir).isDirectory()) {
        continue
      }
      walkFiles(specsDir, entry => entry.startsWith('.'), (file) => {
        if (file.endsWith(`${sep}${OPENSPEC_SPEC_FILE}`)) {
          deltas.push({ file, capabilityDir: join(root, openspecRoot, 'specs', relative(specsDir, dirname(file))) })
        }
      })
    }
  }
  return deltas.sort((a, b) => a.file < b.file ? -1 : a.file > b.file ? 1 : 0)
}

/** The archive folder of each OpenSpec root, as an absolute path. */
export function openspecArchiveDirs(config: Pick<SiteConfig, 'specs'>, root: string = repoRoot): string[] {
  return (config.specs.openspec?.roots ?? []).map(openspecRoot => join(root, openspecRoot, 'changes', 'archive'))
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
