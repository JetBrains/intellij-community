// The read model that the kernel pages need: the harvested docs, diagrams, specs and symbols of a site, and the
// loaders of the harvested files. A site builds it from its harvest output and gives it to `createSite`.
// `siteIndex` joins it into the lookups of the kernel pages, once per data object.

import type { HarvestedDiagram } from '../scripts/diagram.ts'
import { type AdrMeta, adrOrderKey } from '../scripts/docMeta.ts'
import type { ExplainerChapter } from '../scripts/explainerDoc.ts'
import type { HarvestedDoc } from '../scripts/harvestDocs.ts'
import type { SiteConfig } from '../scripts/siteConfig.ts'
import { decisionPaths } from './routeTable.ts'

export type { HarvestedDoc } from '../scripts/harvestDocs.ts'

/** A harvested spec, as the kernel pages read it. A site spec has more fields. */
export interface SiteSpec {
  /** The path relative to its spec root, without the spec suffix, for example `actions/new-thread`. */
  id: string
  /** The path relative to `contentRoot`. */
  path: string
  name: string
  description: string
}

/** A harvested code symbol, for the palette search. */
export interface SiteSymbol {
  name: string
  fqn: string
  /** The path relative to `contentRoot`. */
  file: string
  /** The 1-based line of the declaration. */
  line: number
}

/** One launch target of the launch picker: a Bazel target that builds a dev distribution and starts the IDE. */
export interface LaunchEntry {
  /** The name of the IDE run configuration, for example `IDEA`. */
  name: string
  /** The Bazel label, for example `//build:idea`. */
  target: string
  /** The command that starts the target from the repository root, for example `./bazel.cmd run //build:idea`. */
  command: string
  /** The repository half that declares the target. */
  half: 'ultimate' | 'community'
  /** The run configuration file, relative to the repository root. */
  configurationFile: string
  /** The product of the distribution, for example `Idea`. */
  product?: string
}

/** The read model of the kernel pages. */
export interface SiteData {
  docs: readonly HarvestedDoc[]
  diagrams: readonly HarvestedDiagram[]
  specs: readonly SiteSpec[]
  symbols: readonly SiteSymbol[]
  /** The loaders of the harvested doc and spec bodies, by content-relative path (see {@link harvestedFiles}). */
  markdown: Readonly<Record<string, () => Promise<string>>>
  /** The loaders of the bundled source files, by content-relative path. */
  sources: Readonly<Record<string, () => Promise<unknown>>>
  /** The hashed URLs of the harvested pictures, by content-relative path. */
  assets: Readonly<Record<string, string>>
  /** The code spans that the doc folder does not resolve, by doc folder (`CodeSpanPaths` of scripts/codeSpans.ts). */
  codeSpanPaths?: Readonly<Record<string, Readonly<Record<string, string | null>>>>
  /** The entries of the launch picker, sorted by name (`launchPicker` of the site config). */
  launch?: readonly LaunchEntry[]
}

/**
 * The entries of an `import.meta.glob` result under `prefix`, keyed by the path after it. A site calls the glob
 * itself, because Vite resolves a glob pattern relative to the file that holds it.
 */
export function harvestedFiles<T>(glob: Record<string, T>, prefix: string): Record<string, T> {
  return Object.fromEntries(Object.entries(glob).filter(([key]) => key.startsWith(prefix)).map(([key, value]) => [key.slice(prefix.length), value]))
}

/** Slug of a bundled doc, used in /guides/<slug> and /explain/<slug> routes: the file basename, so a move between folders keeps its URL. */
export function guideSlug(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1, -'.md'.length)
}

export type ExplainerDoc = HarvestedDoc & { chapters: ExplainerChapter[] }
export type DecisionDoc = HarvestedDoc & { adr: AdrMeta }

/** The lookups of the kernel pages over one {@link SiteData}. */
export interface SiteIndex {
  data: SiteData
  /** Bundled prose docs by content-relative path. */
  docsByPath: ReadonlyMap<string, HarvestedDoc>
  /** The explainers (ADR 0203 of Air), in frontmatter order. Each one has `chapters`. */
  explainerDocs: readonly ExplainerDoc[]
  /** The decision records, newest first: a dated record before a numbered one. */
  decisionDocs: readonly DecisionDoc[]
  /** The hand-laid diagrams by id. */
  diagramsById: ReadonlyMap<string, HarvestedDiagram>
  specsById: ReadonlyMap<string, SiteSpec>
  /** The doc behind a /guides/<slug> route, or undefined. */
  docForGuideSlug(slug: string): HarvestedDoc | undefined
  /** The explainer behind an /explain/<slug> route, or undefined. */
  docForExplainerSlug(slug: string): ExplainerDoc | undefined
  /** The decision record behind a /decisions/<slug> route, or undefined. */
  docForDecisionSlug(slug: string): DecisionDoc | undefined
}

const indexes = new WeakMap<SiteData, SiteIndex>()

/** The index of `data`. The site data module and `createSite` share one index per data object. */
export function siteIndex(data: SiteData, config: Pick<SiteConfig, 'docs'>): SiteIndex {
  const cached = indexes.get(data)
  if (cached !== undefined) {
    return cached
  }
  const docsRoot = `${config.docs.root}/`
  const { decisionSlug } = decisionPaths(config.docs.decisions)
  const docByGuideSlug = new Map(
    data.docs
      .filter(doc => doc.path.startsWith(docsRoot) && !doc.path.includes('/decisions/') && doc.kind !== 'explainer')
      .map(doc => [guideSlug(doc.path), doc]),
  )
  const explainerDocs = data.docs
    .filter((doc): doc is ExplainerDoc => doc.kind === 'explainer' && doc.chapters !== undefined)
    .sort((a, b) => a.order - b.order || a.title.localeCompare(b.title))
  const explainerBySlug = new Map(explainerDocs.map(doc => [guideSlug(doc.path), doc]))
  const decisionDocs = data.docs
    .filter((doc): doc is DecisionDoc => doc.adr !== undefined)
    .sort((a, b) => adrOrderKey(b.adr).localeCompare(adrOrderKey(a.adr)) || b.path.localeCompare(a.path))
  const decisionBySlug = new Map(decisionDocs.map(doc => [decisionSlug(doc.path), doc]))
  const index: SiteIndex = {
    data,
    docsByPath: new Map(data.docs.map(doc => [doc.path, doc])),
    explainerDocs,
    decisionDocs,
    diagramsById: new Map(data.diagrams.map(diagram => [diagram.id, diagram])),
    specsById: new Map(data.specs.map(spec => [spec.id, spec])),
    docForGuideSlug: slug => docByGuideSlug.get(slug),
    docForExplainerSlug: slug => explainerBySlug.get(slug),
    docForDecisionSlug: slug => decisionBySlug.get(slug),
  }
  indexes.set(data, index)
  return index
}
