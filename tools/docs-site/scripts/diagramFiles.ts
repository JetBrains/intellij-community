// Reads the hand-laid diagrams of a site (`docs.diagrams` of the site config) and cross-checks the diagram fences
// of its markdown against them (ADR 0203). Grammar and graph errors come from the shared validator (diagram.ts);
// this module adds the file system: every fence names a known diagram, and every diagram has a fence, because an
// orphan diagram is a diagram that drifts unseen.
//
// Run by the `check` script of a site, by the gate of a site (the Air JUnit gate runs on bun), and by the harvest.

import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { type AirDiagram, DIAGRAM_FILE_SUFFIX, findDiagramFences, parseDiagramFence, validateDiagram } from './diagram.ts'
import { contentDir, isMainModule, posixRelative, repoRoot, runCheck, walkFiles } from './paths.ts'
import { diagramGrammar, type SiteConfig } from './siteConfig.ts'
import { parseYaml } from './yamlCompat.ts'

/** The part of the site config that the check reads. */
export type DiagramFilesConfig = Pick<SiteConfig, 'contentRoot' | 'docs' | 'specs' | 'diagram'>

/** The directories under the doc and spec roots that hold no authored markdown, in addition to `docs.skipDirs`. */
const SKIP_DIRS: readonly string[] = ['node_modules', 'generated', 'test-results']

export interface DiagramFence {
  /** The markdown file, relative to the content root. */
  doc: string
  /** The 1-based line of the opening fence. */
  line: number
  /** The diagram id, or null when the fence does not parse. */
  id: string | null
  errors: string[]
}

export interface DiagramFiles {
  diagrams: Map<string, AirDiagram>
  fences: DiagramFence[]
}

/** Every diagram file, validated. Throws with every finding of every file. */
export function readDiagramFiles(config: DiagramFilesConfig, root: string = repoRoot): Map<string, AirDiagram> {
  const diagramsDir = config.docs.diagrams
  const dir = join(contentDir(config, root), diagramsDir)
  const diagrams = new Map<string, AirDiagram>()
  if (!existsSync(dir)) {
    return diagrams
  }
  const grammar = diagramGrammar(config)
  const errors: string[] = []
  for (const entry of readdirSync(dir).sort()) {
    if (!entry.endsWith(DIAGRAM_FILE_SUFFIX)) {
      if (entry.endsWith('.yaml') || entry.endsWith('.yml')) {
        errors.push(`${diagramsDir}/${entry}: a diagram file ends with ${DIAGRAM_FILE_SUFFIX}`)
      }
      continue
    }
    const id = entry.slice(0, -DIAGRAM_FILE_SUFFIX.length)
    try {
      diagrams.set(id, validateDiagram(parseYaml(readFileSync(join(dir, entry), 'utf8')), id, grammar))
    }
    catch (error) {
      errors.push(`${diagramsDir}/${entry}:\n  ${(error instanceof Error ? error.message : String(error)).split('\n').join('\n  ')}`)
    }
  }
  if (errors.length > 0) {
    throw new Error(`diagrams: ${errors.length} invalid file(s)\n${errors.join('\n')}`)
  }
  return diagrams
}

/** Every diagram fence under `docs.root` and under each of `specs.roots`. */
export function readDiagramFences(config: DiagramFilesConfig, root: string = repoRoot): DiagramFence[] {
  const content = contentDir(config, root)
  const skipDirs = new Set([...SKIP_DIRS, ...config.docs.skipDirs])
  const fences: DiagramFence[] = []
  for (const top of [join(content, config.docs.root), ...config.specs.roots.map(specRoot => join(root, specRoot))]) {
    walkFiles(top, entry => skipDirs.has(entry) || entry.startsWith('.'), (path) => {
      if (!path.endsWith('.md')) {
        return
      }
      const source = readFileSync(path, 'utf8')
      for (const fence of findDiagramFences(source)) {
        const { spec, errors } = parseDiagramFence(fence.body)
        fences.push({ doc: posixRelative(content, path), line: fence.line, id: spec?.id ?? null, errors })
      }
    })
  }
  return fences
}

/**
 * Reads the diagrams and the fences, and checks that they agree. Throws with every finding: a
 * malformed fence, a fence that names no diagram, and a diagram that no fence shows.
 */
export function checkDiagramFiles(config: DiagramFilesConfig, root: string = repoRoot): DiagramFiles {
  const diagrams = readDiagramFiles(config, root)
  const fences = readDiagramFences(config, root)
  const errors: string[] = []
  const used = new Set<string>()
  for (const fence of fences) {
    for (const error of fence.errors) {
      errors.push(`${fence.doc}:${fence.line}: ${error}`)
    }
    if (fence.id !== null) {
      if (diagrams.has(fence.id)) {
        used.add(fence.id)
      }
      else {
        errors.push(`${fence.doc}:${fence.line}: no diagram '${fence.id}' under ${config.docs.diagrams}/`)
      }
    }
  }
  for (const id of diagrams.keys()) {
    if (!used.has(id)) {
      errors.push(`${config.docs.diagrams}/${id}${DIAGRAM_FILE_SUFFIX}: no diagram fence shows it; embed it or delete it`)
    }
  }
  if (errors.length > 0) {
    throw new Error(`diagrams: the fences and the files disagree\n${errors.join('\n')}`)
  }
  return { diagrams, fences }
}

// --- CLI: `node diagramFiles.ts <site.config.ts>` --------------------------------------------------

if (isMainModule(import.meta.url)) {
  await runCheck('diagrams', (config) => {
    const { diagrams, fences } = checkDiagramFiles(config)
    return `${diagrams.size} diagrams, ${fences.length} fences`
  })
}
