// The repository paths of a site, from its config, and the file walk that the checks share. Node side only.

import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import type { SiteConfig } from './siteConfig.ts'
import { OPENSPEC_ARCHIVE_DIR, OPENSPEC_DESIGN_FILE, OPENSPEC_SPEC_FILE, specIdOfRepoPath } from './sourcePaths.ts'
import { parseYaml } from './yamlCompat.ts'

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
 * The variable that names the `checkout-files` binary of `community/build/dist-tools/bins`. The crate `docs-gate` sets
 * it for the gate under `bazel test`.
 */
const CHECKOUT_FILES_ENV_VAR = 'DOCS_CHECKOUT_FILES'

/** The files of each root that `checkout-files` listed, by the repository-relative root, in the order of a walk. */
const checkoutListings = new Map<string, readonly string[]>()

/** The order of a walk: the segments of two paths compare one by one, so the files of a directory stay together. */
function walkOrder(a: readonly string[], b: readonly string[]): number {
  for (let index = 0; index < Math.min(a.length, b.length); index++) {
    if (a[index] !== b[index]) {
      return a[index] < b[index] ? -1 : 1
    }
  }
  return a.length - b.length
}

/**
 * The files that `checkout-files` lists under the root that holds `dir`, as absolute paths in the order of a walk.
 * Null when no binary is set, and for `dir` outside the repository or at its root. A root is listed once in a process.
 */
function checkoutFiles(dir: string): readonly string[] | null {
  const tool = process.env[CHECKOUT_FILES_ENV_VAR]
  const root = posixRelative(repoRoot, dir)
  if (tool === undefined || tool === '' || root === '' || root.startsWith('../') || root === '..' || isAbsolute(root)) {
    return null
  }
  for (const [listed, files] of checkoutListings) {
    if (root === listed || root.startsWith(`${listed}/`)) {
      return files
    }
  }
  const run = spawnSync(tool, [repoRoot, root], { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 })
  if (run.error !== undefined || run.status !== 0) {
    throw new Error(`${tool} ${repoRoot} ${root} failed (${run.error?.message ?? `exit ${run.status}`}): ${(run.stderr ?? '').trim()}`)
  }
  const files = run.stdout.split('\0').filter(path => path !== '').map(path => path.split('/'))
  files.sort(walkOrder)
  const paths = files.map(segments => join(repoRoot, ...segments))
  checkoutListings.set(root, paths)
  return paths
}

/**
 * Calls `visit` with every file under `dir`, depth first, with the entries of a directory in sorted order. An entry
 * that `skips` accepts is not entered and not visited. A `dir` that does not exist has no files.
 *
 * With the `checkout-files` binary of [CHECKOUT_FILES_ENV_VAR], the walk reads the files that git lists: an ignored
 * file is not visited, and a symbolic link is a file of its own. The binary lists the root of `dir` once, so a process
 * reads no directory and asks for no kind of an entry.
 *
 * Without the binary, the kind of an entry comes with the directory listing, so the walk asks the file system once for
 * a directory, not once for each entry. A symbolic link takes the kind of its target.
 */
export function walkFiles(dir: string, skips: (entry: string) => boolean, visit: (path: string) => void): void {
  const listed = checkoutFiles(dir)
  if (listed !== null) {
    const prefix = resolve(dir) + sep
    for (const file of listed) {
      if (file.startsWith(prefix) && !file.slice(prefix.length).split(sep).some(skips)) {
        visit(file)
      }
    }
    return
  }
  if (!existsSync(dir)) {
    return
  }
  const entries = readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0)
  for (const entry of entries) {
    if (skips(entry.name)) {
      continue
    }
    const path = join(dir, entry.name)
    if (entry.isSymbolicLink() ? statSync(path).isDirectory() : entry.isDirectory()) {
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
 * resolves from there. The archive `changes/archive/` is history, so no spec check reads its deltas. The checks read
 * the design of each archived change as a decision document (`openspecArchiveDesigns`).
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
  return (config.specs.openspec?.roots ?? []).map(openspecRoot => join(root, openspecRoot, OPENSPEC_ARCHIVE_DIR))
}

/** The design of one archived change of an OpenSpec root: a decision document of the site. */
export interface OpenSpecArchiveDesign {
  /** The name of the archive folder, `YYYY-MM-DD-<slug>`: the change id that a `decision <change-id>` mention names. */
  id: string
  /** The absolute path of the `design.md` file. */
  file: string
  /** The `YYYY-MM-DD` prefix of the folder name, or null when the name has none. */
  date: string | null
  /** The folder name without its date prefix. */
  slug: string
  /** The `x-topic` field of `.openspec.yaml`, or null when the change names no topic. */
  topic: string | null
  /** The `x-adr` field of `.openspec.yaml`: the ids of the old decision records of the change. */
  adr: string[]
}

/** The `x-topic` and `x-adr` fields of the `.openspec.yaml` file of a change folder. A folder with no file has none. */
function changeFields(dir: string): { topic: string | null, adr: string[] } {
  const file = join(dir, '.openspec.yaml')
  if (!existsSync(file)) {
    return { topic: null, adr: [] }
  }
  const data = parseYaml(readFileSync(file, 'utf8'))
  const fields = data !== null && typeof data === 'object' && !Array.isArray(data) ? data as Record<string, unknown> : {}
  const topic = fields['x-topic']
  const adr = fields['x-adr']
  return {
    topic: typeof topic === 'string' && topic.trim() !== '' ? topic.trim() : null,
    adr: Array.isArray(adr) ? adr.map(String) : typeof adr === 'string' || typeof adr === 'number' ? [String(adr)] : [],
  }
}

/**
 * The design of each archived change of the OpenSpec roots, sorted by path: `<root>/changes/archive/<id>/design.md`.
 * A folder with no `design.md` gives no design. Each design is a decision document of the site.
 */
export function openspecArchiveDesigns(config: Pick<SiteConfig, 'specs'>, root: string = repoRoot): OpenSpecArchiveDesign[] {
  const designs: OpenSpecArchiveDesign[] = []
  for (const archive of openspecArchiveDirs(config, root)) {
    if (!existsSync(archive)) {
      continue
    }
    for (const id of readdirSync(archive).sort()) {
      const dir = join(archive, id)
      const file = join(dir, OPENSPEC_DESIGN_FILE)
      if (id.startsWith('.') || !statSync(dir).isDirectory() || !existsSync(file)) {
        continue
      }
      const date = /^(\d{4}-\d{2}-\d{2})-/.exec(id)?.[1] ?? null
      designs.push({ id, file, date, slug: date === null ? id : id.slice(date.length + 1), ...changeFields(dir) })
    }
  }
  return designs.sort((a, b) => a.file < b.file ? -1 : a.file > b.file ? 1 : 0)
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
