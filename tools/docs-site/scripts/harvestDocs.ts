// The doc part of a site harvest: the notes, the guides, the explainers, the glossary, the decision records, and the
// extra readmes, then the hand-laid diagrams with their references resolved. The folders come from the site config
// (`docs` of siteConfig.ts). The site model resolves each reference (modelProvider.ts), so this file knows no
// language and no build system.
//
// The harvest also runs the decision check (adrFiles.ts), the diagram check (diagramFiles.ts), the prose check
// (proseLint.ts), and the vocabulary check (vocabulary.ts), in this order, so a prose finding hides no reference error.
// It takes them from a check run (checkRun.ts), so a check that the caller ran already does not run again.

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { assertRunOf, type CheckRun, createCheckRun } from './checkRun.ts'
import { addCodeSpanPaths } from './codeSpans.ts'
import { type AirDiagram, DIAGRAM_FILE_SUFFIX, type HarvestedDiagram, type HarvestedDiagramNode, type HarvestedDiagramStep } from './diagram.ts'
import type { DiagramFiles } from './diagramFiles.ts'
import { collectPictureReferences } from './docAssets.ts'
import { adrDisplayTitle, type AdrMeta, type GuideGroup, isLabeledId, isNoteState, NOTE_STATES, type NoteState, parseAdrMeta } from './docMeta.ts'
import { type ExplainerChapter, splitExplainerChapters } from './explainerDoc.ts'
import type { ModelProvider, SourceLocation } from './modelProvider.ts'
import { contentDir, repoRoot } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'

export { slugify } from './explainerDoc.ts'

/** `readme` is a bundled doc that no index lists, so a link to it still resolves. */
export type DocKind = 'guide' | 'adr' | 'note' | 'home' | 'glossary' | 'readme' | 'explainer'

export const DOC_KINDS: readonly DocKind[] = ['guide', 'adr', 'note', 'home', 'glossary', 'readme', 'explainer']

export interface HarvestedDoc {
  /** Path relative to the content root, e.g. `docs/awb.md`. */
  path: string
  title: string
  description: string
  kind: DocKind
  /** Sort order within its kind (frontmatter `order:`, default 999). */
  order: number
  /**
   * Terms from the mention universe that the body contains. The site harvest fills it for guide, note, and explainer
   * docs, and leaves it empty for the rest.
   */
  mentions: string[]
  /** The decision record metadata (docMeta.ts). Set on an `adr` doc only. */
  adr?: AdrMeta
  /** The Guides page group (frontmatter `group:`). Set on a `guide` doc only. */
  group?: GuideGroup
  /** Whether the work of a note goes on (frontmatter `state:`). Set on a `note` doc only. */
  state?: NoteState
  /** The text before the first chapter. Set on an `explainer` doc only. */
  intro?: string
  /** The chapters, in document order (explainerDoc.ts). Set on an `explainer` doc only. */
  chapters?: ExplainerChapter[]
}

export interface DocHarvest {
  /** The docs, in harvest order. */
  docs: HarvestedDoc[]
  /** The doc bodies without the frontmatter, by content-relative path, in harvest order. */
  bodies: Map<string, string>
  /** The content-relative paths of the pictures that the doc bodies reference. */
  pictures: Set<string>
  /** The hand-laid diagrams, with their references resolved. */
  diagrams: HarvestedDiagram[]
  /** The code spans of the doc bodies that their folder does not resolve, by doc folder (codeSpans.ts). */
  codeSpanPaths: Map<string, Map<string, string | null>>
}

export interface DocHarvestOptions {
  /** The repository root. A test gives a temporary tree. */
  root?: string
  /** Called with each harvested doc and its source. The Air harvest reads the air-view fences there. */
  onDoc?: (path: string, source: string) => void
  /** The run of the kernel checks of the same config and root. Without it, the harvest makes its own. */
  checks?: CheckRun
}

/** The 1-based line of `offset` in `source`. */
function lineAt(source: string, offset: number): number {
  return source.slice(0, offset).split('\n').length
}

/** The flat frontmatter of a markdown source: `key: value` and `key:` with a list of `- item` lines. */
export function parseFrontmatter(source: string): { meta: Record<string, string | string[]>, body: string } {
  if (!source.startsWith('---')) {
    return { meta: {}, body: source }
  }
  const end = source.indexOf('\n---', 3)
  if (end < 0) {
    return { meta: {}, body: source }
  }
  const meta: Record<string, string | string[]> = {}
  let currentList: string[] | null = null
  for (const line of source.slice(3, end).split('\n')) {
    const listItem = /^\s+-\s+(.+)$/.exec(line)
    if (listItem && currentList) {
      currentList.push(listItem[1].trim())
      continue
    }
    const keyValue = /^(\w[\w-]*):\s*(.*)$/.exec(line)
    if (keyValue) {
      if (keyValue[2] === '') {
        currentList = []
        meta[keyValue[1]] = currentList
      }
      else {
        currentList = null
        meta[keyValue[1]] = keyValue[2].trim()
      }
    }
  }
  return { meta, body: source.slice(end + 4).replace(/^\n/, '') }
}

/** The text of the `## <heading>` section of a body, up to the next `##` heading. */
export function sectionOf(body: string, heading: string): string {
  const pattern = new RegExp(`^##\\s+${heading}[^\\n]*\\n([\\s\\S]*?)(?=^##\\s|$(?![\\s\\S]))`, 'm')
  return pattern.exec(body)?.[1] ?? ''
}

/**
 * Adds the pictures of a spec or doc body to `pictures`, and fails on a picture that is not a file under the content
 * root. `path` is the content-relative path of the source, and `body` is the source without its frontmatter.
 */
export function collectDocPictures(
  config: Pick<SiteConfig, 'contentRoot'>,
  path: string,
  source: string,
  body: string,
  pictures: Set<string>,
  root: string = repoRoot,
): void {
  const content = contentDir(config, root)
  const firstLine = lineAt(source, source.length - body.length)
  const baseDir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
  for (const reference of collectPictureReferences(config.contentRoot, body, baseDir)) {
    if (reference.path === null || !existsSync(join(content, reference.path)) || !statSync(join(content, reference.path)).isFile()) {
      throw new Error(`harvest: ${path}:${firstLine + reference.line - 1}: picture ${reference.source} not found`)
    }
    pictures.add(reference.path)
  }
}

/** The markdown files of a content-relative folder, sorted, or none when the folder does not exist. */
function markdownEntries(content: string, folder: string): string[] {
  const dir = join(content, folder)
  return existsSync(dir) ? readdirSync(dir).sort().filter(entry => entry.endsWith('.md')) : []
}

/**
 * Harvests the docs and the diagrams of a site. Throws on the first malformed doc, on every finding of the decision,
 * diagram, prose, and vocabulary checks, and on the first diagram reference that the provider does not resolve.
 */
export function harvestDocs(config: SiteConfig, provider: ModelProvider, options: DocHarvestOptions = {}): DocHarvest {
  const root = options.root ?? repoRoot
  const checks = options.checks ?? createCheckRun(config, root)
  assertRunOf(checks, config, root)
  const content = contentDir(config, root)
  const docs: HarvestedDoc[] = []
  const bodies = new Map<string, string>()
  const pictures = new Set<string>()
  const codeSpanPaths = new Map<string, Map<string, string | null>>()

  const harvestDoc = (docPath: string, defaultKind: DocKind): void => {
    const absolute = join(content, docPath)
    if (!existsSync(absolute)) {
      return
    }
    const source = readFileSync(absolute, 'utf8')
    const { meta, body } = parseFrontmatter(source)
    const metaKind = typeof meta.kind === 'string' ? meta.kind : ''
    const fileName = docPath.slice(docPath.lastIndexOf('/') + 1, -'.md'.length)
    if (body.trim() === '') {
      throw new Error(`harvest: ${docPath}: the doc is empty`)
    }
    const kind = (DOC_KINDS as readonly string[]).includes(metaKind) ? metaKind as DocKind : defaultKind
    const heading = /^#\s+(.+)$/m.exec(body)?.[1]?.trim() ?? fileName
    const doc: HarvestedDoc = {
      path: docPath,
      title: typeof meta.title === 'string' ? meta.title : kind === 'adr' ? adrDisplayTitle(heading) : heading,
      description: typeof meta.description === 'string' ? meta.description : '',
      kind,
      order: typeof meta.order === 'string' && /^\d+$/.test(meta.order) ? Number(meta.order) : 999,
      mentions: [],
    }
    if (kind === 'adr') {
      doc.adr = parseAdrMeta(docPath, body, typeof meta.topic === 'string' ? meta.topic : undefined, config.decisions.topics)
    }
    else if (kind === 'guide') {
      if (typeof meta.group !== 'string' || !isLabeledId(config.guideGroups, meta.group)) {
        throw new Error(`harvest: ${docPath}: frontmatter "group:" is missing or unknown; use one of ${config.guideGroups.map(group => group.id).join(', ')}`)
      }
      doc.group = meta.group
    }
    else if (kind === 'note') {
      if (typeof meta.state !== 'string' || !isNoteState(meta.state)) {
        throw new Error(`harvest: ${docPath}: frontmatter "state:" is missing or unknown; use one of ${NOTE_STATES.join(', ')}`)
      }
      doc.state = meta.state
    }
    else if (kind === 'explainer') {
      const parts = splitExplainerChapters(body, docPath)
      doc.intro = parts.intro
      doc.chapters = parts.chapters
    }
    docs.push(doc)
    bodies.set(docPath, body)
    collectDocPictures(config, docPath, source, body, pictures, root)
    addCodeSpanPaths(codeSpanPaths, config.contentRoot, docPath, body, root)
    options.onDoc?.(docPath, source)
  }

  const { home, notes } = config.docs
  const homeInNotes = home !== null && notes !== null && home === `${notes}/${home.slice(home.lastIndexOf('/') + 1)}`
  if (home !== null && !homeInNotes) {
    harvestDoc(home, 'home')
  }
  if (notes !== null) {
    for (const entry of markdownEntries(content, notes)) {
      // AGENTS.md instructs an agent, not a reader
      if (entry !== 'AGENTS.md') {
        const path = `${notes}/${entry}`
        harvestDoc(path, path === home ? 'home' : 'note')
      }
    }
  }
  for (const entry of markdownEntries(content, config.docs.guides)) {
    harvestDoc(`${config.docs.guides}/${entry}`, 'guide')
  }
  for (const entry of markdownEntries(content, config.docs.explainers)) {
    harvestDoc(`${config.docs.explainers}/${entry}`, 'explainer')
  }
  if (config.docs.glossary !== null) {
    harvestDoc(config.docs.glossary, 'glossary')
  }
  for (const folder of config.docs.decisions) {
    for (const entry of markdownEntries(content, folder)) {
      harvestDoc(`${folder}/${entry}`, 'adr')
    }
  }
  for (const extra of config.docs.extraReadmes) {
    harvestDoc(extra, 'readme')
  }
  // the record files and the references to them, shared with the gate and the check script (adrFiles.ts)
  checks.decisions()

  const diagrams = harvestDiagrams(config, provider, docs, checks.diagramFiles())
  // the prose of the explainers and the diagram cards, shared with the gate and the check script (proseLint.ts).
  // It runs after the diagram references resolved, so a prose finding hides no reference error.
  checks.prose()
  // the terms of the site vocabulary, shared with the gate and the check script (vocabulary.ts)
  checks.vocabulary()
  return { docs, bodies, pictures, diagrams, codeSpanPaths }
}

/**
 * The diagrams of the site with each reference resolved against the provider and the harvested decision records.
 * The grammar, the grid, and the fence cross-check are the shared check (diagramFiles.ts).
 */
function harvestDiagrams(config: SiteConfig, provider: ModelProvider, docs: readonly HarvestedDoc[], diagramFiles: DiagramFiles): HarvestedDiagram[] {
  // a diagram names a record by its number, or by its file name without `.md` (a dated record has no number)
  const adrTitles = new Map(docs.flatMap(doc => doc.adr
    ? [[doc.path.slice(doc.path.lastIndexOf('/') + 1, -'.md'.length), doc] as const, ...(doc.adr.number === null ? [] : [[doc.adr.number, doc] as const])]
    : []))
  const diagrams: HarvestedDiagram[] = []
  const linkedFiles: string[] = []
  for (const diagram of diagramFiles.diagrams.values()) {
    const file = `harvest: ${config.docs.diagrams}/${diagram.id}${DIAGRAM_FILE_SUFFIX}`
    const where = (node: string): string => `${file}: node '${node}'`
    for (const column of diagram.columns) {
      if (column.module !== null && !provider.hasModule(column.module)) {
        throw new Error(`${file}: column '${column.id}' names unknown module \`${column.module}\``)
      }
    }
    const nodes: HarvestedDiagramNode[] = diagram.nodes.map((node) => {
      let symbolRef: HarvestedDiagramNode['symbolRef'] = null
      if (node.symbol !== null) {
        const resolved = provider.resolveSymbol(node.symbol)
        if (resolved === null || resolved === 'ambiguous') {
          throw new Error(`${where(node.id)}: symbol \`${node.symbol}\` ${resolved === 'ambiguous' ? 'is ambiguous; use the FQN' : 'resolves to no class, interface, or object; use `source:` for a function'}`)
        }
        symbolRef = resolved
      }
      let sourceRef: HarvestedDiagramNode['sourceRef'] = null
      if (node.source !== null) {
        const resolved = provider.resolveSource(node.source)
        if (resolved === null) {
          throw new Error(`${where(node.id)}: source \`${node.source}\` does not exist under ${config.contentRoot}`)
        }
        sourceRef = { file: resolved.file, line: node.line ?? resolved.line }
      }
      let targetRef: SourceLocation | undefined
      if (node.target !== undefined) {
        if (provider.resolveTarget === undefined) {
          throw new Error(`${where(node.id)}: target \`${node.target}\` needs a site that resolves Bazel targets`)
        }
        targetRef = provider.resolveTarget(node.target) ?? undefined
        if (targetRef === undefined) {
          throw new Error(`${where(node.id)}: target \`${node.target}\` names no Bazel target`)
        }
      }
      if (node.module !== null && !provider.hasModule(node.module)) {
        throw new Error(`${where(node.id)}: unknown module \`${node.module}\``)
      }
      if (node.spec !== null && !provider.hasSpec(node.spec)) {
        throw new Error(`${where(node.id)}: unknown spec \`${node.spec}\``)
      }
      if (node.concept !== null && !provider.hasConcept(node.concept)) {
        throw new Error(`${where(node.id)}: unknown concept \`${node.concept}\``)
      }
      const adr = node.adr === null ? null : adrTitles.get(node.adr)
      if (node.adr !== null && adr === undefined) {
        throw new Error(`${where(node.id)}: no decision record ${node.adr}`)
      }
      for (const ref of [symbolRef, sourceRef, targetRef]) {
        if (ref) {
          linkedFiles.push(ref.file)
        }
      }
      return { ...node, symbolRef, sourceRef, ...(targetRef === undefined ? {} : { targetRef }), adrTitle: adr?.title ?? null, adrPath: adr?.path ?? null }
    })
    diagrams.push({ ...diagram, nodes, steps: resolveSteps(provider, diagram, nodes, file) })
  }
  provider.bundleSources(linkedFiles)
  return diagrams
}

/** The steps with their `flowStep:` resolved by the provider. A site without flows rejects a `flowStep:`. */
function resolveSteps(provider: ModelProvider, diagram: AirDiagram, nodes: HarvestedDiagramNode[], file: string): HarvestedDiagramStep[] {
  if (provider.resolveSteps !== undefined) {
    return provider.resolveSteps(diagram, nodes)
  }
  return diagram.steps.map((step, index) => {
    if (step.flowStep !== null) {
      throw new Error(`${file}: steps[${index + 1}]: flowStep \`${step.flowStep}\` needs a site with flows`)
    }
    return { ...step, flowRef: null }
  })
}
