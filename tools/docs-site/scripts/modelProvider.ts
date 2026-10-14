// The model of a site, as the doc harvest (harvestDocs.ts) sees it. A diagram node names a symbol, a source file,
// a Bazel target, a module, a spec, or a concept. The site resolves each name against its own model, so the kernel
// knows no language and no build system.

import type { AirDiagram, HarvestedDiagramNode, HarvestedDiagramStep } from './diagram.ts'

/** A file and a 1-based line. The file is a path that the source viewer and the GitHub link of the site take. */
export interface SourceLocation {
  file: string
  line: number
}

export interface ModelProvider {
  /** The declaration of a type name or an FQN, null when no declaration matches, or `ambiguous` when two match. */
  resolveSymbol(name: string): SourceLocation | null | 'ambiguous'
  /** The location of a `source:` path, relative to the content root, or null when the file does not exist. */
  resolveSource(path: string): SourceLocation | null
  /**
   * The `BUILD.bazel` line of a Bazel label, or null when no target matches. A site that does not define it rejects
   * every `target:` reference.
   */
  resolveTarget?(label: string): SourceLocation | null
  /** True when the site has a module of this name. */
  hasModule(name: string): boolean
  /** True when the site has a spec of this id. */
  hasSpec(id: string): boolean
  /** True when the site has a concept of this id. */
  hasConcept(id: string): boolean
  /** Adds the files that the harvested docs link to the source bundle of the site viewer. */
  bundleSources(files: readonly string[]): void
  /**
   * The steps of a diagram with each `flowStep:` resolved. The nodes carry their resolved references. A site that
   * does not define it rejects every `flowStep:` reference.
   */
  resolveSteps?(diagram: AirDiagram, nodes: readonly HarvestedDiagramNode[]): HarvestedDiagramStep[]
}
