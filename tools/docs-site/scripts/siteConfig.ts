// The configuration of one docs site. Each site gives one `site.config.ts` with a default export of this shape.
// The kernel checks, the doc harvest, and the browser pages read the paths and the vocabulary of the site from it.
//
// The browser reads this file too, so it imports nothing from node.

/** One entry of an ordered list that a page shows as a filter or a group. */
export interface LabeledId {
  id: string
  label: string
}

/** The skin of one node kind of a hand-laid diagram. */
export interface DiagramKindStyle {
  /** The `kind:` value of a node. */
  id: string
  /** The name of the kind in the legend and on the node card. */
  label: string
  /** The glyph before the node title, or null for no glyph. */
  glyph: string | null
  /** The Tailwind classes of the node box. */
  className: string
  /** True when the node title is code, for example a module name. */
  mono?: boolean
}

/** One value of the `planned:` field of a node. */
export interface DiagramPlannedPhase {
  /** The `planned:` value of a node. */
  id: string
  /** The badge text on the diagram node. */
  badge: string
  /** The sentence on the node card. */
  cardText: string
}

/**
 * One term rule of the vocabulary check (vocabulary.ts). The check finds the term, or the term with an `s`, as a whole
 * word, case-insensitive, outside code spans and fenced blocks. A space of the term matches any whitespace.
 */
export interface VocabularyRule {
  /** The term that the prose must not use. */
  term: string
  /** The term to use instead, or null when the text must say it in other words. */
  replacement: string | null
  /** Why the term is out. */
  reason: string
  /** The places where the term can stay. */
  allowed?: {
    /** Content-relative doc paths. A `*` matches any characters inside one path segment. */
    docs?: string[]
    /** Sections as `<doc path>#<heading slug>`. A section includes its subsections. */
    sections?: string[]
    /** Content-relative paths. The label of a link to one of them can use the term. */
    linkPaths?: string[]
  }
}

/** The launch picker of a site: the page at /launch that lists the launch targets of the site data. */
export interface LaunchPickerConfig {
  /** The name of the entry that the page selects when the query names none, for example `IDEA`. */
  defaultName: string
  /** The route path of the explainer chapter that explains a launch, or null. The page links it only when it exists. */
  explainChapter: string | null
}

export interface SiteConfig {
  /** The site id. */
  id: string
  /** The page title. */
  title: string
  /** The short text of the brand mark. */
  brandMark: string
  /** The URL base of the built site, with the slashes on both sides. */
  base: string
  /** The package directory of the site, relative to the repository root. */
  siteRoot: string
  /** The directory that every doc path is relative to, relative to the repository root. */
  contentRoot: string
  /** The output directory of the build, relative to the repository root. */
  outDir: string
  /** The doc folders and files. Each path is relative to `contentRoot`. */
  docs: {
    /** The folder of the docs. The diagram fence check reads every markdown file under it. */
    root: string
    /** The home doc, or null when the site has none. */
    home: string | null
    /** The folder whose top-level markdown files are notes, or null when the site has no notes. */
    notes: string | null
    /** The folder of the guides. */
    guides: string
    /** The folder of the explainers. */
    explainers: string
    /** The folder of the hand-laid diagrams. */
    diagrams: string
    /** The glossary doc, or null when the site has none. */
    glossary: string | null
    /** The folders whose markdown files the harvest reads as decision records. */
    decisions: string[]
    /** The extra docs that the site bundles but no index lists. */
    extraReadmes: string[]
    /** The directory names under the doc and spec roots that hold no authored markdown. */
    skipDirs: string[]
  }
  /** The specs. */
  specs: {
    /** The spec folders, relative to the repository root. */
    roots: string[]
    /** The file name suffix of a spec. */
    suffix: string
    /** The spec format, or null for the free format. With `plain-1`, the spec check applies the style rules (specRefs.ts). */
    style: 'plain-1' | null
    /** The `##` headings that a `plain-1` spec can use, or null when the site checks no sections. */
    templateSections: string[] | null
  }
  /** The folders that a `source:` reference and a spec target can name, relative to the repository root. */
  sourceRoots: string[]
  /** The GitHub link of a source file. */
  github: {
    /** The URL of a file at the repository root, for example `https://github.com/<org>/<repo>/blob/master/`. */
    repoBase: string
    /** The path of `contentRoot` in the repository, with a trailing slash. */
    pathPrefix: string
  }
  /** The localStorage key of the theme mode. */
  themeStorageKey: string
  /** The decision records. */
  decisions: {
    /** The folders that hold the records, relative to the repository root. */
    folders: string[]
    /** The highest frozen record number. A numbered file name above it must take a date. */
    lastFrozenNumber: number
    /** The folders whose markdown and sources cite the records, relative to the repository root. */
    referenceRoots: string[]
    /** The directory names that the reference walk does not enter, in addition to the tool outputs. */
    skipDirs: string[]
    /** The topics of a record, in the order of the Decisions page filter. */
    topics: LabeledId[]
    /** The topic that the Decisions page hides until the reader asks for it, or null. */
    hiddenTopic: string | null
    /** The frozen numbers that two records can share. A bare `ADR NNNN` mention of a shared number is ambiguous. */
    allowDuplicateNumbers: string[]
  }
  /** The groups of the Guides page, in page order. */
  guideGroups: LabeledId[]
  /** The vocabulary of the hand-laid diagrams. */
  diagram: {
    /** The node kinds, in legend order. */
    kinds: DiagramKindStyle[]
    /** The values of the `planned:` field. */
    planned: DiagramPlannedPhase[]
  }
  /** The limits of the prose check. */
  prose: {
    /** The most words in one sentence. */
    maxSentenceWords: number
    /** The most words in one diagram card. */
    maxCardWords: number
    /** The marks of history in prose, such as an issue id. The default finds a round and a commit hash. */
    historyPattern?: RegExp
    /** The guide that a prose finding names, relative to the repository root, or null. */
    guide: string | null
  }
  /** The vocabulary check, or null when the site has none. */
  vocabulary: VocabularyRule[] | null
  /** The launch picker, or null when the site has none. */
  launchPicker: LaunchPickerConfig | null
}

/** The node kinds and the planned values that the diagram grammar accepts. */
export function diagramGrammar(config: Pick<SiteConfig, 'diagram'>): { kinds: string[], planned: string[] } {
  return {
    kinds: config.diagram.kinds.map(kind => kind.id),
    planned: config.diagram.planned.map(phase => phase.id),
  }
}
