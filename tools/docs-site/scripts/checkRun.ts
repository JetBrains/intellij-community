// One run of the kernel checks over one tree. Each check runs at most once in a run, and a later call returns the
// result of the first call. The check of a site runs each check for its count line, and its harvest then takes the
// results from the same run, so no check reads the tree twice. A harvest without a run makes its own.
//
// A run keeps what it read, so make a new run after a change of the tree, for example in the dev server. A check
// that fails keeps no result, so a second call runs it again and throws again.
//
// The gate on bun loads this file, so it imports nothing from npm.

import { checkDecisionRecords, type DecisionRecord } from './adrFiles.ts'
import { checkDiagramFiles, type DiagramFiles } from './diagramFiles.ts'
import { repoRoot } from './paths.ts'
import { checkProse } from './proseLint.ts'
import type { SiteConfig } from './siteConfig.ts'
import { checkSpecRefs, type SpecRefs } from './specRefs.ts'
import { checkVocabulary } from './vocabulary.ts'

/** The kernel checks of one site over one tree. Each method throws with every finding of its check. */
export interface CheckRun {
  readonly config: SiteConfig
  readonly root: string
  /** The decision check (adrFiles.ts): the records. */
  decisions: () => DecisionRecord[]
  /** The diagram check (diagramFiles.ts): the diagrams and the fences. */
  diagramFiles: () => DiagramFiles
  /** The prose check (proseLint.ts): the number of files read. */
  prose: () => number
  /** The vocabulary check (vocabulary.ts): the number of files read. */
  vocabulary: () => number
  /** The spec check (specRefs.ts): what the check read. */
  specRefs: () => SpecRefs
}

/** The value of `compute`, computed on the first call only. A throw keeps no value. */
export function once<T>(compute: () => T): () => T {
  let result: { value: T } | undefined
  return () => (result ??= { value: compute() }).value
}

/** A new run of the kernel checks of `config` over the tree at `root`. */
export function createCheckRun(config: SiteConfig, root: string = repoRoot): CheckRun {
  return {
    config,
    root,
    decisions: once(() => checkDecisionRecords(config, root)),
    diagramFiles: once(() => checkDiagramFiles(config, root)),
    prose: once(() => checkProse(config, root)),
    vocabulary: once(() => checkVocabulary(config, undefined, root)),
    specRefs: once(() => checkSpecRefs(config, root)),
  }
}

/** Fails loudly when `run` checks another site or another tree than the caller reads. */
export function assertRunOf(run: CheckRun, config: SiteConfig, root: string): void {
  if (run.config !== config || run.root !== root) {
    throw new Error(`the check run of ${run.config.id} at ${run.root} does not belong to ${config.id} at ${root}`)
  }
}
