// The one ordered table of app URLs of a docs site (ADR 0162 of Air). Parse, build, the app route heads, the nav
// section, and the crumb head all come from it. The first entry that matches wins, in both directions. Legacy entries
// only parse, so old links and bookmarks keep working.
//
// The kernel owns the routes of the generic pages: home, guides, explainers, decisions, docs, sources, and the launch
// picker when the site config has one. A site adds
// its own routes as route extensions, which come before the kernel routes in the table.
//
// The module is pure: it reads no bundler state, so a node unit test loads it.

import type { SiteConfig } from '../scripts/siteConfig.ts'

/** The common shape of every route: a site route has more fields. */
export interface AnyRoute {
  page: string
}

/** Inclusive, one-based source line selection used by source-viewer permalinks. */
export interface SourceLineSelection {
  start: number
  end: number
}

/** The Decisions index filter on the record status (ADR_STATUSES of scripts/docMeta.ts). */
export type DecisionStatusFilter = 'accepted' | 'proposed' | 'partly-superseded' | 'superseded'

/** The routes of the kernel pages. */
export type KernelRoute =
  | { page: 'home' }
  | { page: 'guides', anchor: string | null }
  | { page: 'guide', slug: string, anchor: string | null }
  /** The explainers index (ADR 0203 of Air). */
  | { page: 'explainers', anchor: string | null }
  /** One explainer at one chapter, with the step and the selected node of its diagram. */
  | { page: 'explainer', slug: string, chapter: string | null, step: number | null, node: string | null }
  | {
      page: 'decisions'
      anchor: string | null
      status: DecisionStatusFilter | null
      /** A topic id, `all` for every topic, or null for every topic except the hidden one. */
      topic: string | null
    }
  /** One decision record by the base name of its file, for example `0197-a-session-is-state-an-agent-is-a-writer`. */
  | { page: 'decision', slug: string, anchor: string | null }
  | { page: 'source', path: string, selection: SourceLineSelection | null }
  | { page: 'doc', path: string, anchor: string | null }

/**
 * The route of the launch picker, with its search text. It is not a {@link KernelRoute}: the route exists only when the
 * site has a launch picker, so a site without one keeps a route union without it.
 */
export interface LaunchRoute {
  page: 'launch'
  q: string | null
}

/** The nav sections that the kernel routes mark as active. */
export type KernelNavSection = 'guides' | 'explainers' | 'decisions'

/** The nav section of the launch picker. */
export type LaunchNavSection = 'launch'

/** The head crumb of a route: the section label, and the index page that it links to. */
export interface RouteParent {
  label: string
  /** Absent when the section has no index page. */
  route?: AnyRoute
}

/**
 * How the shell lays out a page on the window (Air ADR "The docs site lays out a page as islands on the window").
 * - `reading`: the section tree, the document, and the table of contents, each an island. The document has the measure
 *   of prose, 70ch. The outlet scrolls.
 * - `catalog`: the reading layout with a wide document, about 960px, for a page of cards, tiles and chip rows.
 * - `tool`: one island across the window, at the density of the IDE. The outlet scrolls.
 * - `canvas`: the page fills the outlet with its own islands, and the outlet does not scroll. The page owns its trail.
 */
export type PageLayout = 'reading' | 'catalog' | 'tool' | 'canvas'

/** The canonical entry that builds a route. */
export interface RouteEntryInfo {
  /** The URL pattern, for example `/concept/:id/:anchor?`. */
  pattern: string
  section: string | null
  parent: RouteParent | null
  layout: PageLayout
}

/**
 * Catalog section anchors share the entity-slug segment (`/guides/<x>`, `/decisions/<x>`); the `group-` prefix
 * reserves them for "scroll the catalog" routes. Entity slugs never start with it.
 */
export function catalogAnchor(id: string): string {
  return `group-${id}`
}

export function isCatalogAnchor(slug: string | null | undefined): slug is string {
  return slug != null && slug.startsWith('group-')
}

/** The decision records of a site: the paths are relative to `contentRoot`. */
export interface DecisionPaths {
  /** True for the path of a decision record. */
  isDecisionPath(path: string): boolean
  /** The `decision` route slug of a decision record path: the file name without `.md`. */
  decisionSlug(path: string): string
}

/** The decision path rules of the decision folders `dirs` (`docs.decisions` of the site config). */
export function decisionPaths(dirs: readonly string[]): DecisionPaths {
  const prefixes = dirs.map(dir => `${dir}/`)
  const dirOf = (path: string): string | undefined => prefixes.find(dir => path.startsWith(dir) && !path.slice(dir.length).includes('/'))
  return {
    isDecisionPath: path => path.endsWith('.md') && dirOf(path) !== undefined,
    decisionSlug: path => path.slice((dirOf(path) ?? '').length, -'.md'.length),
  }
}

// --- query codecs --------------------------------------------------------------------------------

/** One query field of an entry: its URL key, and how its value parses and formats. */
export interface QueryCodec<T> {
  /** The URL key, when it is not the route field name. */
  readonly key?: string
  /** True for a value that is a path: the build keeps each `/` of it, so the link reads as the path it names. */
  readonly path?: boolean
  parse(raw: string | null): T | null
  /** Null drops the key from the URL. */
  format(value: T | null): string | null
}

export const text: QueryCodec<string> = {
  parse: raw => raw,
  format: value => value,
}

/** A text value that is a path, such as `a/b/c.zip`: its `/` stays a `/` in the URL. */
export const pathText: QueryCodec<string> = {
  path: true,
  parse: raw => raw,
  format: value => value,
}

export const int: QueryCodec<number> = {
  parse: raw => raw !== null && /^\d+$/.test(raw) ? Number(raw) : null,
  format: value => value === null ? null : String(value),
}

export function oneOf<T extends string>(...values: readonly T[]): QueryCodec<T> {
  return {
    parse: raw => values.find(value => value === raw) ?? null,
    format: value => value,
  }
}

function normalizedSourceSelection(start: number, end: number): SourceLineSelection | null {
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 1 || end < 1) {
    return null
  }
  return { start: Math.min(start, end), end: Math.max(start, end) }
}

/** An inclusive line range as `<n>` or `<start>-<end>`. Both directions put the lower line first. */
function lineRange(key: string): QueryCodec<SourceLineSelection> {
  return {
    key,
    parse: raw => {
      const match = raw?.match(/^(\d+)(?:-(\d+))?$/)
      return match ? normalizedSourceSelection(Number(match[1]), Number(match[2] ?? match[1])) : null
    },
    format: selection => {
      const normalized = selection === null ? null : normalizedSourceSelection(selection.start, selection.end)
      if (normalized === null) {
        return null
      }
      return normalized.start === normalized.end ? String(normalized.start) : `${normalized.start}-${normalized.end}`
    },
  }
}

// --- patterns ------------------------------------------------------------------------------------

// A pattern matches decoded segments. `:name` is one segment, `:name?` is an optional last
// segment, and `*name` is one or more segments that a single `:name` can follow.

type PatternPart =
  | { kind: 'literal', text: string }
  | { kind: 'param', name: string, optional: boolean }
  | { kind: 'splat', name: string }

type Split<S extends string> = S extends `${infer Head}/${infer Rest}` ? Head | Split<Rest> : S
type PatternSegments<Path extends string> = Path extends `/${infer Rest}` ? Split<Rest> : never
type RequiredName<S extends string> = S extends `:${string}?` ? never : S extends `:${infer N}` ? N : S extends `*${infer N}` ? N : never
type OptionalName<S extends string> = S extends `:${infer N}?` ? N : never

type PathValues<Path extends string> =
  & { [N in RequiredName<PatternSegments<Path>>]: string }
  & { [N in OptionalName<PatternSegments<Path>>]: string | null }

type QuerySpec = Record<string, QueryCodec<unknown>>
type QueryValues<Q extends QuerySpec> = { [K in keyof Q]: Q[K] extends QueryCodec<infer T> ? T | null : never }
type EntryValues<Path extends string, Q extends QuerySpec> = PathValues<Path> & QueryValues<Q>

type ValueNames<Path extends string, Q extends QuerySpec> = keyof PathValues<Path> | keyof Q
type Of<R extends AnyRoute, P extends R['page']> = Extract<R, { page: P }>
type RouteFields<R extends AnyRoute, P extends R['page']> = Exclude<keyof Of<R, P>, 'page'>

/** True when the path params and the query fields are exactly the route fields, with their types. */
type DefaultMapping<R extends AnyRoute, P extends R['page'], Path extends string, Q extends QuerySpec> =
  [Exclude<ValueNames<Path, Q>, RouteFields<R, P>>] extends [never]
    ? [Exclude<RouteFields<R, P>, ValueNames<Path, Q>>] extends [never]
        ? [EntryValues<Path, Q>] extends [Omit<Of<R, P>, 'page'>] ? true : false
        : false
    : false

type ToRoute<R extends AnyRoute, Path extends string, Q extends QuerySpec> = (values: EntryValues<Path, Q>) => R
type FromRoute<R extends AnyRoute, P extends R['page'], Path extends string, Q extends QuerySpec> =
  (route: Of<R, P>) => EntryValues<Path, Q> | null

interface EntryOptions<R extends AnyRoute, P extends R['page'], Path extends string, Q extends QuerySpec, S extends string> {
  /** The query fields, in URL order. */
  query?: Q
  /** A parse guard. The parse tries the next entry when it is false. */
  when?: (values: EntryValues<Path, Q>) => boolean
  /** Renamed param values: an old value parses as the new one. */
  aliases?: { readonly [N in keyof PathValues<Path>]?: Readonly<Record<string, string>> }
  /** Parse-only. The build never picks this entry. */
  legacy?: true
  /** The parse result. The default is the page with the values as its fields. */
  to?: ToRoute<R, Path, Q>
  /** The build values, or null to skip this entry. The default reads the route fields. */
  from?: FromRoute<R, P, Path, Q>
  /** A build pre-step that can move the route to another page. */
  normalize?: (route: Of<R, P>) => R
  /** The top-nav item. Null on a page that no nav item owns. */
  section?: S | null
  /** The head crumb. */
  parent?: RouteParent
  /** The layout of the page. A canonical entry must give it, a legacy entry has none. */
  layout?: PageLayout
}

/** A mapping that is not the default one needs a `to`, and a `from` unless the entry is legacy. */
type Mapping<R extends AnyRoute, P extends R['page'], Path extends string, Q extends QuerySpec> = DefaultMapping<R, P, Path, Q> extends true
  ? unknown
  : { to: ToRoute<R, Path, Q> } & ({ legacy: true } | { from: FromRoute<R, P, Path, Q> })

/** A canonical entry builds a page, so it must name the layout of the page. A legacy entry only parses. */
type LayoutRequirement = { legacy: true, layout?: never } | { legacy?: false, layout: PageLayout }

type Values = Record<string, unknown>

/** One entry of the route table. A site builds its route extensions with {@link routeBuilder}. */
export interface RouteEntry {
  readonly page: string
  readonly path: string
  readonly pattern: readonly PatternPart[]
  readonly query: readonly (readonly [field: string, codec: QueryCodec<unknown>])[]
  readonly legacy: boolean
  readonly section: string | null
  readonly parent: RouteParent | null
  /** Null on a legacy entry. */
  readonly layout: PageLayout | null
  readonly aliases: Readonly<Record<string, Readonly<Record<string, string>>>>
  readonly when: ((values: Values) => boolean) | null
  readonly to: (values: Values) => AnyRoute
  readonly from: (route: AnyRoute) => Values | null
  readonly normalize: ((route: AnyRoute) => AnyRoute) | null
}

/** A route of a site page, added to the kernel routes. */
export type RouteExtension = RouteEntry

function parsePattern(path: string): PatternPart[] {
  const parts = path.split('/').filter(Boolean).map((segment): PatternPart => {
    if (segment.startsWith('*')) {
      return { kind: 'splat', name: segment.slice(1) }
    }
    if (segment.startsWith(':')) {
      const optional = segment.endsWith('?')
      return { kind: 'param', name: segment.slice(1, optional ? -1 : undefined), optional }
    }
    return { kind: 'literal', text: segment }
  })
  parts.forEach((part, index) => {
    const last = index === parts.length - 1
    if (part.kind === 'param' && part.optional && !last) {
      throw new Error(`Route pattern ${path}: only the last segment can be optional`)
    }
    if (part.kind === 'splat' && !(last || (index === parts.length - 2 && parts[index + 1].kind === 'param'))) {
      throw new Error(`Route pattern ${path}: a splat can have only one param after it`)
    }
  })
  return parts
}

/**
 * The typed entry builder for the route union `R` and the nav sections `S`. The mapped types make a path param or a
 * query field that is not a route field a compile error, unless the entry maps the values with `to` and `from`.
 */
export function routeBuilder<R extends AnyRoute, S extends string = string>() {
  return function route<P extends R['page'], Path extends string, Q extends QuerySpec = Record<never, never>>(
    page: P,
    path: Path,
    options: EntryOptions<R, P, Path, Q, S> & Mapping<R, P, Path, Q> & LayoutRequirement,
  ): RouteEntry {
    if (options.legacy !== true && options.layout === undefined) {
      throw new Error(`Route ${page} ${path}: a canonical entry needs a layout`)
    }
    const pattern = parsePattern(path)
    const query = Object.entries(options.query ?? {})
    const names = [...pattern.flatMap(part => part.kind === 'literal' ? [] : [part.name]), ...query.map(([field]) => field)]
    const defaultFrom = (target: AnyRoute): Values => Object.fromEntries(names.map(name => [name, (target as unknown as Values)[name]]))
    return {
      page,
      path,
      pattern,
      query,
      legacy: options.legacy === true,
      section: options.section ?? null,
      parent: options.parent ?? null,
      layout: options.layout ?? null,
      aliases: (options.aliases ?? {}) as RouteEntry['aliases'],
      when: (options.when ?? null) as RouteEntry['when'],
      to: (options.to ?? (values => ({ page, ...values }))) as RouteEntry['to'],
      from: (options.from ?? defaultFrom) as RouteEntry['from'],
      normalize: (options.normalize ?? null) as RouteEntry['normalize'],
    }
  }
}

function matchPattern(pattern: readonly PatternPart[], segments: readonly string[]): Record<string, string | null> | null {
  const values: Record<string, string | null> = {}
  let index = 0
  for (const [position, part] of pattern.entries()) {
    if (part.kind === 'splat') {
      const end = segments.length - (pattern.length - position - 1)
      if (end <= index) {
        return null
      }
      values[part.name] = segments.slice(index, end).join('/')
      index = end
    }
    else if (index === segments.length) {
      if (part.kind !== 'param' || !part.optional) {
        return null
      }
      values[part.name] = null
    }
    else if (part.kind === 'literal') {
      if (segments[index] !== part.text) {
        return null
      }
      index++
    }
    else {
      values[part.name] = segments[index++]
    }
  }
  return index === segments.length ? values : null
}

/** The encoded path, or null when a required param has no value. An empty value counts as none. */
function fillPattern(pattern: readonly PatternPart[], values: Values): string | null {
  let path = ''
  for (const part of pattern) {
    if (part.kind === 'literal') {
      path += `/${part.text}`
      continue
    }
    const value = values[part.name]
    if (typeof value !== 'string' || value === '') {
      if (part.kind === 'param' && part.optional) {
        continue
      }
      return null
    }
    path += part.kind === 'splat'
      ? `/${value.split('/').map(encodeURIComponent).join('/')}`
      : `/${encodeURIComponent(value)}`
  }
  return path === '' ? '/' : path
}

/** The path with its query. A key in `paths` keeps each `/` of its value; a `/` needs no escape in a query. */
function withQuery(path: string, params: Record<string, string | null>, paths: ReadonlySet<string>): string {
  const pairs: string[] = []
  for (const [key, value] of Object.entries(params)) {
    if (value !== null) {
      const pair = new URLSearchParams({ [key]: value }).toString()
      pairs.push(paths.has(key) ? pair.replaceAll('%2F', '/') : pair)
    }
  }
  return `${path}${pairs.length > 0 ? `?${pairs.join('&')}` : ''}`
}

// --- the kernel routes ---------------------------------------------------------------------------

const HOME: KernelRoute = { page: 'home' }
const GUIDES: RouteParent = { label: 'Guides', route: { page: 'guides', anchor: null } as KernelRoute }
const EXPLAINERS: RouteParent = { label: 'Explainers', route: { page: 'explainers', anchor: null } as KernelRoute }
const DECISIONS: RouteParent = { label: 'Decisions', route: { page: 'decisions', anchor: null, status: null, topic: null } as KernelRoute }

/**
 * The routes of the kernel pages, in table order. A site puts its route extensions before them. The launch route is
 * in the table only when the site has a launch picker.
 */
export function kernelRouteEntries(config: Pick<SiteConfig, 'docs' | 'launchPicker'>): RouteEntry[] {
  const route = routeBuilder<KernelRoute | LaunchRoute, KernelNavSection | LaunchNavSection>()
  const { isDecisionPath, decisionSlug } = decisionPaths(config.docs.decisions)
  return [
    route('home', '/', { section: null, layout: 'tool' }),
    route('guides', '/guides/:anchor?', { when: ({ anchor }) => anchor === null || isCatalogAnchor(anchor), section: 'guides', layout: 'catalog' }),
    route('guide', '/guides/:slug/:anchor?', { when: ({ slug }) => !isCatalogAnchor(slug), section: 'guides', parent: GUIDES, layout: 'reading' }),
    route('explainers', '/explain/:anchor?', { when: ({ anchor }) => anchor === null || isCatalogAnchor(anchor), section: 'explainers', layout: 'catalog' }),
    route('explainer', '/explain/:slug/:chapter?', {
      query: { step: int, node: text },
      when: ({ slug }) => !isCatalogAnchor(slug),
      section: 'explainers',
      parent: EXPLAINERS,
      layout: 'canvas',
    }),
    route('decisions', '/decisions/:anchor?', {
      query: { status: oneOf<DecisionStatusFilter>('accepted', 'proposed', 'partly-superseded', 'superseded'), topic: text },
      when: ({ anchor }) => anchor === null || isCatalogAnchor(anchor),
      section: 'decisions',
      layout: 'catalog',
    }),
    route('decision', '/decisions/:slug/:anchor?', { when: ({ slug }) => !isCatalogAnchor(slug), section: 'decisions', parent: DECISIONS, layout: 'reading' }),
    route('source', '/source/*path', { query: { selection: lineRange('line') }, section: null, parent: { label: 'Source' }, layout: 'tool' }),
    ...config.launchPicker ? [route('launch', '/launch', { query: { q: text }, section: 'launch', layout: 'tool' })] : [],
    // a decision record had a doc URL before it had its own page
    route('decision', '/doc/:path/:anchor?', {
      legacy: true,
      when: ({ path }) => isDecisionPath(path),
      to: ({ path, anchor }) => ({ page: 'decision', slug: decisionSlug(path), anchor }),
    }),
    // the path is one encoded segment. A decision record builds its own page
    route('doc', '/doc/:path/:anchor?', {
      when: ({ path }) => path.endsWith('.md'),
      normalize: target => isDecisionPath(target.path) ? { page: 'decision', slug: decisionSlug(target.path), anchor: target.anchor } : target,
      section: 'guides',
      parent: GUIDES,
      layout: 'reading',
    }),
    // an older link spelled the doc path as plain segments
    route('doc', '/doc/:path', { legacy: true, to: ({ path }) => ({ page: 'doc', path, anchor: null }) }),
    route('doc', '/doc/:head/*rest', {
      legacy: true,
      when: ({ head }) => !head.endsWith('.md'),
      to: ({ head, rest }) => {
        const path = `${head}/${rest}`
        return isDecisionPath(path) ? { page: 'decision', slug: decisionSlug(path), anchor: null } : { page: 'doc', path, anchor: null }
      },
    }),
  ]
}

// --- parse and build -----------------------------------------------------------------------------

export function stripBase(path: string, basePrefix: string): string {
  if (basePrefix === '') {
    return path
  }
  if (path === basePrefix) {
    return '/'
  }
  return path.startsWith(`${basePrefix}/`) ? path.slice(basePrefix.length) : path
}

function routeInput(input: string, basePrefix: string): string {
  if (input.startsWith('#/')) {
    return input.slice(1)
  }
  const url = input.startsWith('http://') || input.startsWith('https://')
    ? new URL(input)
    : new URL(input || '/', 'http://docs-site.local')
  return `${stripBase(url.pathname, basePrefix)}${url.search}`
}

/** The parse and the build of one ordered table. */
export interface RouteTable {
  /**
   * Parses a path, a full URL, or a legacy `#/` hash. The first matching entry wins. When no entry matches, the parse
   * drops the last segment and tries again, so an unknown tail or head degrades to the nearest page. It never reaches
   * the root entries that way: the result is then home.
   */
  parse(input: string, basePrefix?: string): AnyRoute
  /** The canonical URL of a route: the first canonical entry of its page that can build it. */
  build(route: AnyRoute, basePrefix?: string): string
  /** The canonical entry that builds the route. It gives the nav section and the crumb head. */
  entryFor(route: AnyRoute): RouteEntryInfo
  /** The first literal segment of every entry: the heads that an app path can start with. */
  heads(): string[]
  /** Every entry in table order, for a unit test. */
  rows(): readonly { page: string, pattern: string, legacy: boolean, section: string | null, layout: PageLayout | null }[]
}

/** The table of `entries`, in their order. */
export function createRouteTable(entries: readonly RouteEntry[]): RouteTable {
  const canonicalByPage = new Map<string, RouteEntry[]>()
  for (const entry of entries) {
    if (!entry.legacy) {
      canonicalByPage.set(entry.page, [...canonicalByPage.get(entry.page) ?? [], entry])
    }
  }

  function matchTable(segments: readonly string[], params: URLSearchParams): AnyRoute | null {
    for (const entry of entries) {
      const pathValues = matchPattern(entry.pattern, segments)
      if (pathValues === null) {
        continue
      }
      const values: Values = {}
      for (const [name, value] of Object.entries(pathValues)) {
        const aliases = entry.aliases[name]
        values[name] = value !== null && aliases !== undefined && Object.hasOwn(aliases, value) ? aliases[value] : value
      }
      for (const [field, codec] of entry.query) {
        values[field] = codec.parse(params.get(codec.key ?? field))
      }
      if (entry.when === null || entry.when(values)) {
        return entry.to(values)
      }
    }
    return null
  }

  function buildEntry(route: AnyRoute): { entry: RouteEntry, path: string, values: Values } {
    const candidates = canonicalByPage.get(route.page) ?? []
    for (const entry of candidates) {
      if (entry.normalize !== null) {
        const normalized = entry.normalize(route)
        if (normalized.page !== route.page) {
          return buildEntry(normalized)
        }
      }
    }
    for (const entry of candidates) {
      const values = entry.from(route)
      const path = values === null ? null : fillPattern(entry.pattern, values)
      if (values !== null && path !== null) {
        return { entry, path, values }
      }
    }
    throw new Error(`No route table entry builds ${JSON.stringify(route)}`)
  }

  return {
    parse(input, basePrefix = '') {
      const [path, search] = routeInput(input, basePrefix).split('?')
      const params = new URLSearchParams(search ?? '')
      const segments = path.split('/').filter(Boolean).map(decodeURIComponent)
      if (segments.length === 0) {
        return matchTable(segments, params) ?? HOME
      }
      for (let end = segments.length; end > 0; end--) {
        const route = matchTable(segments.slice(0, end), params)
        if (route !== null) {
          return route
        }
      }
      return HOME
    },
    build(route, basePrefix = '') {
      const { entry, path, values } = buildEntry(route)
      const query = Object.fromEntries(entry.query.map(([field, codec]) => [codec.key ?? field, codec.format(values[field] ?? null)]))
      const paths = new Set(entry.query.flatMap(([field, codec]) => codec.path === true ? [codec.key ?? field] : []))
      return `${basePrefix}${withQuery(path, query, paths)}`
    },
    entryFor(route) {
      const { entry } = buildEntry(route)
      // buildEntry picks only canonical entries, and a canonical entry has a layout
      return { pattern: entry.path, section: entry.section, parent: entry.parent, layout: entry.layout! }
    },
    heads() {
      const heads = entries.flatMap(entry => entry.pattern[0]?.kind === 'literal' ? [entry.pattern[0].text] : [])
      return [...new Set(heads)]
    },
    rows() {
      return entries.map(entry => ({ page: entry.page, pattern: entry.path, legacy: entry.legacy, section: entry.section, layout: entry.layout }))
    },
  }
}

/** The table of a site: its route extensions first, then the kernel routes. */
export function siteRouteTable(config: Pick<SiteConfig, 'docs' | 'launchPicker'>, extensions: readonly RouteExtension[]): RouteTable {
  return createRouteTable([...extensions, ...kernelRouteEntries(config)])
}
