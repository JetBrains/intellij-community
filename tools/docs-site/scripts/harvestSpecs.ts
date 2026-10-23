// The spec part of a site harvest: every spec file under the spec roots of the site config (`specs` of siteConfig.ts),
// with its frontmatter, its status and date lines, its `[@test]` targets, and its headings. A site adds the fields
// that need its own model, for example the modules that the targets name.
//
// A spec file belongs to the first root of `specs.roots` that holds it. So a root can sit inside another root, and the
// outer root harvests only the files that the inner root does not hold.

import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { addCodeSpanPaths } from './codeSpans.ts'
import { collectDocPictures, parseFrontmatter } from './harvestDocs.ts'
import { contentDir, posixRelative, repoRoot, specFiles } from './paths.ts'
import type { SiteConfig } from './siteConfig.ts'
import { specTestFiles } from './sourcePaths.ts'

/** The part of the site config that the spec harvest reads. */
export type SpecHarvestConfig = Pick<SiteConfig, 'contentRoot' | 'docs' | 'specs'>

/** The fields of a spec that every site harvests. */
export interface HarvestedSpecFields {
  /** The path relative to its spec root, without the spec suffix, for example `actions/new-thread`. */
  id: string
  /** The path relative to `contentRoot`. */
  path: string
  /** The first segment of the id, or `spec` for an id with no folder. */
  group: string
  name: string
  description: string
  /** The `Status:` line of the body, or null. */
  status: string | null
  /** The `Date:` line of the body, verbatim, or null. */
  date: string | null
  /** The `style:` frontmatter value, for example `plain-1`, or null. */
  style: string | null
  /** The `targets:` frontmatter list, as written: each entry is relative to the folder of the spec. */
  targets: string[]
  /** The `[@test]` targets of the body, relative to the repository root, unique and sorted. */
  testFiles: string[]
  /** The `##` headings of the body, in document order. */
  headings: string[]
}

export interface SpecHarvest {
  /** The specs, sorted by path. */
  specs: HarvestedSpecFields[]
  /** The spec bodies without the frontmatter, by content-relative path, in spec order. */
  bodies: Map<string, string>
  /** The content-relative paths of the pictures that the spec bodies reference. */
  pictures: Set<string>
  /** The code spans of the spec bodies that their folder does not resolve, by spec folder (codeSpans.ts). */
  codeSpanPaths: Map<string, Map<string, string | null>>
}

/**
 * Harvests the specs of a site. Throws on a picture that is not a file under the content root, and warns on a
 * `[@test]` target that does not exist.
 */
export function harvestSpecs(config: SpecHarvestConfig, root: string = repoRoot): SpecHarvest {
  const content = contentDir(config, root)
  const specs: HarvestedSpecFields[] = []
  const bodies = new Map<string, string>()
  const pictures = new Set<string>()
  const codeSpanPaths = new Map<string, Map<string, string | null>>()
  for (const { file, specRoot } of specFiles(config, root)) {
    const source = readFileSync(file, 'utf8')
    const { meta, body } = parseFrontmatter(source)
    const path = posixRelative(content, file)
    const id = relative(specRoot, file).split(sep).join('/').slice(0, -config.specs.suffix.length)
    const specDir = dirname(path) === '.' ? '' : dirname(path)
    const testFiles = specTestFiles(config.contentRoot, specDir, body, (repoPath, target) => {
      if (existsSync(join(root, repoPath))) {
        return true
      }
      console.warn(`harvest: ${path}: [@test] target not found: ${target}`)
      return false
    })
    specs.push({
      id,
      path,
      group: id.includes('/') ? id.slice(0, id.indexOf('/')) : 'spec',
      name: typeof meta.name === 'string' ? meta.name : id,
      description: typeof meta.description === 'string' ? meta.description : '',
      status: /^Status:\s*(.+)$/m.exec(body)?.[1]?.trim() ?? null,
      date: /^Date:\s*(.+)$/m.exec(body)?.[1]?.trim() ?? null,
      style: typeof meta.style === 'string' ? meta.style : null,
      targets: Array.isArray(meta.targets) ? meta.targets : [],
      testFiles,
      headings: [...body.matchAll(/^##\s+(.+)$/gm)].map(match => match[1].trim()),
    })
    bodies.set(path, body)
    collectDocPictures(config, path, source, body, pictures, root)
    addCodeSpanPaths(codeSpanPaths, config.contentRoot, path, body, root)
  }
  return { specs, bodies, pictures, codeSpanPaths }
}
