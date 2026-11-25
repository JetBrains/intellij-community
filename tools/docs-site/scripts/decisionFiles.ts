// The decisions of a site, and the references to them. A decision is the design of an archived change of an OpenSpec
// root (`specs.openspec` of the site config): `<root>/changes/archive/<change-id>/design.md`. The change id is the name
// of the archive folder, `YYYY-MM-DD-<slug>`: the date orders the decision, and the slug identifies it with no
// coordination. A markdown file links the design, and a source file names it as `decision <change-id>`.
//
// The check is one owner for three callers: the `check` script of a site, the gate of a site (the Air JUnit gate
// runs on bun), and the harvest. It fails on an archive folder name without a calendar date and a lower-case slug, on
// an `x-topic` of `.openspec.yaml` that is not a topic of the site, on a `decision <change-id>` mention that names no
// design, on a link to a `design.md` of an archive folder that does not exist, on an `ADR NNNN` mention, and on a link
// into a `docs/decisions/` folder that does not exist. A decision has no number, so a number names nothing.
//
// A file of the archive wrote its relative links for the folder of the active change, and the archive moved it one
// folder down. So in the archive, only the design of a change with an `x-topic` takes the link and the mention rules.

import { existsSync } from 'node:fs'
import { basename, dirname, join, relative, resolve, sep } from 'node:path'
import { withoutFencedBlocks } from './fence.ts'
import { isMainModule, openspecArchiveDesigns, openspecArchiveDirs, type OpenSpecArchiveDesign, posixRelative, repoRoot, runCheck, walkFiles } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'
import { readSourceFile } from './sourceCache.ts'
import { OPENSPEC_DESIGN_FILE } from './sourcePaths.ts'

/** The part of the site config that the check reads. */
export type DecisionConfig = Pick<SiteConfig, 'decisions' | 'specs'>

/** The directories that a walk of a reference root does not enter: tool outputs, dependencies, test data, and images. */
const SKIP_DIRS: ReadonlySet<string> = new Set(['node_modules', '.git', 'generated', 'target', 'testData', 'images', 'test-results', 'out', 'dist'])

/** A change id: a calendar date and a lower-case slug. */
const CHANGE_ID = /^(\d{4}-\d{2}-\d{2})-[a-z0-9]+(?:-[a-z0-9]+)*$/

function isCalendarDate(date: string): boolean {
  const parsed = new Date(`${date}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date
}

function posix(path: string): string {
  return path.split(sep).join('/')
}

/**
 * The designs of the archived changes of the OpenSpec roots. Throws with every finding: a folder name without a
 * calendar date and a lower-case slug, and an `x-topic` that is not one of `decisions.topics`.
 */
export function readDecisionDesigns(config: DecisionConfig, root: string = repoRoot): OpenSpecArchiveDesign[] {
  const designs = openspecArchiveDesigns(config, root)
  const errors: string[] = []
  for (const design of designs) {
    const path = posixRelative(root, design.file)
    const name = CHANGE_ID.exec(design.id)
    if (name === null || !isCalendarDate(name[1])) {
      errors.push(`${path}: the folder of an archived change is YYYY-MM-DD-slug, with a calendar date and a lower-case slug`)
    }
    if (design.topic !== null && !config.decisions.topics.some(topic => topic.id === design.topic)) {
      errors.push(`${path}: "x-topic: ${design.topic}" of .openspec.yaml is unknown; use one of ${config.decisions.topics.map(topic => topic.id).join(', ')}`)
    }
  }
  if (errors.length > 0) {
    throw new Error(`decisions: ${errors.length} invalid archived change(s)\n${errors.join('\n')}`)
  }
  return designs
}

const LINK = /]\(<?([^)\s>]+?\.md)(?:#[^)\s>]*)?>?\)/g
/** A mention of a decision by a number, the form of a numbered record. A decision has no number. */
const NUMBER_MENTION = /\bADRs?\s+`?(\d{3,4})\b/g
/** A mention of a decision by its change id: `decision <change-id>`. */
const DECISION_MENTION = /\b[Dd]ecision\s+(\d{4}-\d{2}-\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*)\b/g
/** A quick test for a source file that may hold a mention. */
const MENTION_HINT = /ADR|[Dd]ecision\s+\d{4}-/
/** A folder of decision record files: no decision lives there. */
const RECORD_FOLDER = `${sep}docs${sep}decisions${sep}`

/** The source files whose comments cite a decision. */
const SOURCE_SUFFIXES: readonly string[] = ['.kt', '.java', '.ts', '.tsx', '.mjs', '.rs', '.bazel', '.bzl']

/**
 * The files under `decisions.referenceRoots` that the reference check reads, in the order of the walk: each markdown
 * file and each source file of [SOURCE_SUFFIXES]. A gate reads them ahead into the shared read cache.
 */
export function decisionReferenceFiles(config: DecisionConfig, root: string = repoRoot): string[] {
  const skipDirs = new Set([...SKIP_DIRS, ...config.decisions.skipDirs])
  const files: string[] = []
  for (const referenceRoot of config.decisions.referenceRoots) {
    walkFiles(join(root, referenceRoot), entry => skipDirs.has(entry), (file) => {
      if (file.endsWith('.md') || SOURCE_SUFFIXES.some(suffix => file.endsWith(suffix))) {
        files.push(file)
      }
    })
  }
  return files
}

export interface DecisionReferenceFinding {
  /** The repository-relative POSIX path of the citing file. */
  path: string
  line: number
  message: string
}

/**
 * The findings about the references under `decisions.referenceRoots`: a `decision <change-id>` mention that names no
 * design of `designs`, a markdown link to a design of an archive folder that does not exist, an `ADR NNNN` mention in
 * markdown or in a source comment, and a markdown link into a `docs/decisions/` folder whose target does not exist. A
 * fenced block is not read, so a guide can show the shape of a name.
 */
export function checkDecisionReferences(
  config: DecisionConfig,
  root: string = repoRoot,
  designs: readonly OpenSpecArchiveDesign[] = readDecisionDesigns(config, root),
): DecisionReferenceFinding[] {
  const archives = openspecArchiveDirs(config, root)
  const inArchive = (path: string): boolean => archives.some(archive => path.startsWith(archive + sep))
  const topicDesigns = new Set(designs.filter(design => design.topic !== null).map(design => design.file))
  const decisionIds = new Set(designs.map(design => design.id))
  const findings: DecisionReferenceFinding[] = []
  for (const file of decisionReferenceFiles(config, root)) {
    const markdown = file.endsWith('.md')
    const path = posix(relative(root, file))
    // a site scan reads the same files, so the text comes from the shared read cache
    const source = readSourceFile(file)
    // a source file has mentions only, and most source files have none
    if (!markdown && !MENTION_HINT.test(source)) {
      continue
    }
    const lines = (markdown ? withoutFencedBlocks(source) : source).split('\n')
    const checksLinks = !inArchive(file) || topicDesigns.has(file)
    lines.forEach((text, index) => {
      for (const mention of text.matchAll(DECISION_MENTION)) {
        if (!decisionIds.has(mention[1])) {
          findings.push({ path, line: index + 1, message: `"${mention[0]}" names no archived change with a design` })
        }
      }
      if (!checksLinks) {
        return
      }
      for (const mention of text.matchAll(NUMBER_MENTION)) {
        findings.push({ path, line: index + 1, message: `"${mention[0]}" names a decision by a number; name the design as decision <change-id>, or link it` })
      }
      if (!markdown) {
        return
      }
      for (const link of text.matchAll(LINK)) {
        const target = resolve(dirname(file), link[1])
        if (existsSync(target)) {
          continue
        }
        if (target.includes(RECORD_FOLDER)) {
          findings.push({ path, line: index + 1, message: `the link "${link[1]}" names a decision record file; link the design of the archived change` })
        }
        else if (basename(target) === OPENSPEC_DESIGN_FILE && inArchive(target)) {
          findings.push({ path, line: index + 1, message: `the link "${link[1]}" names no design of an archived change` })
        }
      }
    })
  }
  return findings
}

/** Reads the designs of the archived changes, and checks the references. Throws with every finding. Returns the designs. */
export function checkDecisions(config: DecisionConfig, root: string = repoRoot): OpenSpecArchiveDesign[] {
  const designs = readDecisionDesigns(config, root)
  const findings = checkDecisionReferences(config, root, designs)
  if (findings.length > 0) {
    throw new Error(`decisions: ${findings.length} stale reference(s)\n${findings.map(finding => `${finding.path}:${finding.line}: ${finding.message}`).join('\n')}`)
  }
  return designs
}

// --- CLI: `node decisionFiles.ts <site.config.ts>` ------------------------------------------------------

if (isMainModule(import.meta.url)) {
  await runCheck('decisions', config => `${checkDecisions(config).length} decisions`)
}
