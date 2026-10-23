// The kernel route table: every canonical kernel entry round-trips, every legacy URL parses to its canonical route,
// and a site route extension comes before the kernel routes. The test needs no harvest and no site.

import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createRouteTable, kernelRouteEntries, type LaunchRoute, pathText, routeBuilder, siteRouteTable, text, type KernelRoute } from './routeTable.ts'

const CONFIG = { docs: { decisions: ['docs/decisions'] }, launchPicker: { defaultName: 'IDEA', explainChapter: null } } as Parameters<typeof kernelRouteEntries>[0]
const table = createRouteTable(kernelRouteEntries(CONFIG))
const parse = (input: string, basePrefix = '') => table.parse(input, basePrefix)
const build = (route: KernelRoute | LaunchRoute, basePrefix = '') => table.build(route, basePrefix)

/** One sample per canonical kernel entry, with its canonical URL. */
const SAMPLES: readonly (readonly [KernelRoute | LaunchRoute, string])[] = [
  [{ page: 'home' }, '/'],
  [{ page: 'guides', anchor: null }, '/guides'],
  [{ page: 'guide', slug: 'flow-authoring-guide', anchor: 'steps' }, '/guides/flow-authoring-guide/steps'],
  [{ page: 'explainers', anchor: null }, '/explain'],
  [{ page: 'explainer', slug: 'the-session-host-model', chapter: null, step: null, node: null }, '/explain/the-session-host-model'],
  [{ page: 'explainer', slug: 'the-session-host-model', chapter: 'a-channel', step: 2, node: 'store' }, '/explain/the-session-host-model/a-channel?step=2&node=store'],
  [{ page: 'decisions', anchor: null, status: 'proposed', topic: 'all' }, '/decisions?status=proposed&topic=all'],
  [{ page: 'decision', slug: '0197-a-session-is-state', anchor: 'context' }, '/decisions/0197-a-session-is-state/context'],
  [{ page: 'source', path: 'docs/src/router.ts', selection: { start: 3, end: 9 } }, '/source/docs/src/router.ts?line=3-9'],
  [{ page: 'doc', path: 'spec/README.md', anchor: 'layout' }, '/doc/spec%2FREADME.md/layout'],
  [{ page: 'launch', q: 'IDEA Community' }, '/launch?q=IDEA+Community'],
]

test('every canonical sample round-trips in both directions', () => {
  for (const [route, url] of SAMPLES) {
    assert.equal(build(route), url, JSON.stringify(route))
    assert.deepEqual(parse(url), route, url)
    assert.equal(build(parse(url) as KernelRoute), url, url)
  }
})

test('the samples cover every canonical entry', () => {
  const built = new Set(SAMPLES.map(([route]) => table.entryFor(route).pattern))
  const canonical = table.rows().filter(row => !row.legacy).map(row => row.pattern)
  assert.deepEqual([...new Set(canonical)].filter(pattern => !built.has(pattern)), [])
})

/** Legacy and degraded inputs, with the canonical URL each one parses to. */
const ALIASES: readonly (readonly [string, string])[] = [
  ['#/guides', '/guides'],
  ['/doc/spec/README.md', '/doc/spec%2FREADME.md'],
  ['/doc/docs%2Fdecisions%2F0197-a.md', '/decisions/0197-a'],
  ['/doc/docs%2Fdecisions%2F0197-a.md/context', '/decisions/0197-a/context'],
  ['/doc/docs/decisions/0197-a.md', '/decisions/0197-a'],
  ['/decisions?status=bogus', '/decisions'],
  ['/decisions/group-x/y', '/decisions/group-x'],
  ['/doc/README.md/x/y', '/doc/README.md/x'],
  ['/source', '/'],
  ['/doc', '/'],
  ['/zzz', '/'],
  ['/guides/group-x/y', '/guides/group-x'],
  ['/guides/a/b/c', '/guides/a/b'],
  ['/explain/group-x/y', '/explain/group-x'],
  ['http://docs-site.local/guides/a?x=1', '/guides/a'],
]

test('every legacy and degraded input parses to its canonical route', () => {
  for (const [input, url] of ALIASES) {
    assert.equal(build(parse(input) as KernelRoute), url, input)
  }
})

test('the app route heads are the first literal of every entry', () => {
  assert.deepEqual(table.heads().toSorted(), ['decisions', 'doc', 'explain', 'guides', 'launch', 'source'])
})

test('a doc route to a decision record builds the decision page', () => {
  assert.equal(build({ page: 'doc', path: 'docs/decisions/0197-a.md', anchor: 'context' }), '/decisions/0197-a/context')
  assert.equal(build({ page: 'doc', path: 'docs/decisions/sub/x.md', anchor: null }), '/doc/docs%2Fdecisions%2Fsub%2Fx.md')
})

test('a source line range puts the lower line first and drops an invalid line', () => {
  assert.deepEqual(parse('/source/a/b.kt?line=9-3'), { page: 'source', path: 'a/b.kt', selection: { start: 3, end: 9 } })
  assert.deepEqual(parse('/source/a/b.kt?line=0'), { page: 'source', path: 'a/b.kt', selection: null })
  assert.equal(build({ page: 'source', path: 'a/b.kt', selection: { start: 4, end: 4 } }), '/source/a/b.kt?line=4')
  assert.equal(build({ page: 'source', path: 'a/b.kt', selection: { start: 0, end: 4 } }), '/source/a/b.kt')
})

test('the base prefix round-trips', () => {
  assert.equal(build({ page: 'home' }, '/build'), '/build/')
  assert.deepEqual(parse('/build', '/build'), { page: 'home' })
  assert.deepEqual(parse('/build/', '/build'), { page: 'home' })
  assert.equal(build(parse('#/guides', '/build') as KernelRoute, '/build'), '/build/guides')
  assert.equal(build(parse('https://example.org/build/decisions/0001-x', '/build') as KernelRoute, '/build'), '/build/decisions/0001-x')
})

test('only home and source have no nav section, and the crumb head comes from the building entry', () => {
  assert.deepEqual(table.rows().filter(row => !row.legacy && row.section === null).map(row => row.page), ['home', 'source'])
  const doc: KernelRoute = { page: 'doc', path: 'a.md', anchor: null }
  const source: KernelRoute = { page: 'source', path: 'a', selection: null }
  const guides: KernelRoute = { page: 'guides', anchor: null }
  assert.equal(table.entryFor(doc).section, 'guides')
  assert.deepEqual(table.entryFor(source).parent, { label: 'Source' })
  assert.equal(table.entryFor(guides).parent, null)
})

test('a canonical entry names the layout of its page, and a legacy entry names none', () => {
  assert.deepEqual(table.rows().filter(row => row.legacy !== (row.layout === null)), [])
  const layout = (route: KernelRoute) => table.entryFor(route).layout
  assert.equal(layout({ page: 'guide', slug: 'a', anchor: null }), 'reading')
  assert.equal(layout({ page: 'explainer', slug: 'a', chapter: null, step: null, node: null }), 'canvas')
  assert.equal(layout({ page: 'home' }), 'tool')
  const route = routeBuilder<{ page: 'x' }>()
  // @ts-expect-error: a canonical entry without a layout does not compile
  assert.throws(() => route('x', '/x', {}), /needs a layout/)
})

type SiteRoute = KernelRoute | { page: 'launch', q: string | null } | { page: 'legacy-home', sel: string | null }

test('a route extension comes before the kernel routes, so it can claim a kernel URL', () => {
  const route = routeBuilder<SiteRoute, 'launch'>()
  const site = siteRouteTable({ ...CONFIG, launchPicker: null }, [
    route('legacy-home', '/', { legacy: true, query: { sel: text }, when: ({ sel }) => sel !== null }),
    route('launch', '/launch', { query: { q: text }, section: 'launch', layout: 'tool' }),
  ])
  const launch: SiteRoute = { page: 'launch', q: 'idea' }
  assert.deepEqual(site.parse('/?sel=x'), { page: 'legacy-home', sel: 'x' })
  assert.deepEqual(site.parse('/'), { page: 'home' })
  assert.equal(site.build(launch), '/launch?q=idea')
  assert.equal(site.entryFor(launch).section, 'launch')
  assert.deepEqual(site.heads().toSorted(), ['decisions', 'doc', 'explain', 'guides', 'launch', 'source'])
})

test('the launch route exists only when the site has a launch picker', () => {
  const without = createRouteTable(kernelRouteEntries({ ...CONFIG, launchPicker: null }))
  assert.deepEqual(without.parse('/launch'), { page: 'home' })
  assert.ok(!without.rows().some(row => row.page === 'launch'))
  assert.deepEqual(parse('/launch'), { page: 'launch', q: null })
  const launch: LaunchRoute = { page: 'launch', q: null }
  assert.equal(table.entryFor(launch).section, 'launch')
})

test('a path value keeps its slashes in the build, and any value parses back', () => {
  type ZipRoute = { page: 'zip', zip: string | null, note: string | null }
  const route = routeBuilder<ZipRoute>()
  const zips = createRouteTable([route('zip', '/zip', { query: { zip: pathText, note: text }, layout: 'tool' })])
  const zip: ZipRoute = { page: 'zip', zip: 'ui_lane_test/iter-1/A Test/b c.zip', note: 'x/y' }
  assert.equal(zips.build(zip), '/zip?zip=ui_lane_test/iter-1/A+Test/b+c.zip&note=x%2Fy')
  assert.deepEqual(zips.parse('/zip?zip=ui_lane_test/iter-1/A+Test/b+c.zip&note=x%2Fy'), zip)
  assert.deepEqual(zips.parse('/zip?zip=ui_lane_test%2Fiter-1%2FA%20Test%2Fb%20c.zip&note=x/y'), zip)
})
