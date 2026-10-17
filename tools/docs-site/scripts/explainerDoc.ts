// The shape of an explainer (ADR 0203): a markdown doc under docs/explainers/ whose `##` headings are
// the chapters, each with at most one diagram fence. Shared by scripts/harvestDocs.ts (node) and
// the site (browser), so this file imports nothing from node.

import { findDiagramFences, parseDiagramFence } from './diagram.ts'

export interface ExplainerChapter {
  /** The route segment: the slug of the heading. */
  id: string
  title: string
  /** The chapter body without its heading and without the diagram fence. */
  markdown: string
  /** The diagram id of the chapter's fence, or null for a chapter with no diagram. */
  diagram: string | null
  /** The `step:` of the fence, or null. */
  step: number | null
  /** The opening question of the chapter as plain text, or null when the first paragraph does not open with one. */
  question: string | null
}

export interface ExplainerParts {
  /** The text before the first `##` heading. */
  intro: string
  chapters: ExplainerChapter[]
}

/** The heading id of the site renderer (src/markdown.tsx): one source for the chapter slugs. */
export function slugify(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
}

/** The plain text of a heading: inline code, emphasis, and link brackets removed. */
export function headingText(markdown: string): string {
  return markdown
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
    .replace(/[*_]+/g, '')
    .trim()
}

/**
 * The opening question of a chapter body: the first sentence of the first paragraph, when it ends with `?`. Null when a
 * full stop, an exclamation mark, or "e.g." comes first. The result keeps the code text and drops the link targets
 * and the emphasis marks.
 */
export function openingQuestion(markdown: string): string | null {
  const paragraph = markdown.trim().split(/\n\s*\n/)[0]?.replace(/\s*\n\s*/g, ' ') ?? ''
  if (paragraph === '' || /^(?:[#>|-]|```|\d+\.\s)/.test(paragraph)) {
    return null
  }
  // hide the code spans first: a dot, an underscore, or a question mark inside code is not prose
  const codes: string[] = []
  const masked = paragraph
    .replace(/`([^`]*)`/g, (_, code: string) => `\u0000${codes.push(code) - 1}\u0000`)
    .replace(/\[([^\]]*)]\([^)]*\)/g, '$1')
  const end = /\?(?=\s|$)/.exec(masked)
  if (end === null) {
    return null
  }
  const sentence = masked.slice(0, end.index + 1)
  if (/[.!](?=\s)|\be\.g\./i.test(sentence)) {
    return null
  }
  return sentence
    .replace(/(\*{1,2}|_{1,2})(\S(?:.*?\S)?)\1/g, '$2')
    .replace(/\u0000(\d+)\u0000/g, (_, index: string) => codes[Number(index)])
}

/**
 * Splits an explainer body into its intro and chapters. Throws when a chapter has two fences, two
 * chapters share a slug, or the body has no chapter.
 */
export function splitExplainerChapters(body: string, path: string): ExplainerParts {
  const lines = body.split('\n')
  const sections: { title: string, lines: string[] }[] = []
  const intro: string[] = []
  let inFence = false
  for (const line of lines) {
    if (/^```/.test(line)) {
      inFence = !inFence
    }
    const heading = inFence ? null : /^##\s+(.+?)\s*$/.exec(line)
    if (heading) {
      sections.push({ title: headingText(heading[1]), lines: [] })
    }
    else if (sections.length === 0) {
      intro.push(line)
    }
    else {
      sections[sections.length - 1].lines.push(line)
    }
  }
  if (sections.length === 0) {
    throw new Error(`${path}: an explainer needs at least one '## ' chapter`)
  }
  const seen = new Set<string>()
  const chapters = sections.map((section) => {
    const id = slugify(section.title)
    if (id === '' || seen.has(id)) {
      throw new Error(`${path}: chapter '${section.title}' has an empty or repeated slug '${id}'`)
    }
    seen.add(id)
    const source = section.lines.join('\n')
    const fences = findDiagramFences(source)
    if (fences.length > 1) {
      throw new Error(`${path}: chapter '${section.title}' has ${fences.length} diagram fences; one at most`)
    }
    let diagram: string | null = null
    let step: number | null = null
    if (fences.length === 1) {
      const { spec, errors } = parseDiagramFence(fences[0].body)
      if (spec === null) {
        throw new Error(`${path}: chapter '${section.title}': ${errors.join('; ')}`)
      }
      diagram = spec.id
      step = spec.step
    }
    const markdown = source.replace(/^```(?:air-)?diagram[ \t]*\n[\s\S]*?^```[ \t]*$\n?/gm, '').trim()
    return { id, title: section.title, markdown, diagram, step, question: openingQuestion(markdown) }
  })
  // the frontmatter title is the page title, so the H1 of the body stays out of the intro
  return { intro: intro.join('\n').replace(/^#\s[^\n]*\n?/m, '').trim(), chapters }
}
