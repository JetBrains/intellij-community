// The e2e suite of the kernel pages, for any site: the source viewer, the guides, the explainers (ADR 0204 of Air),
// the command palette, the decisions, and the theme. The fixtures come from the site config and from the harvest of
// the site under test (`generated/`), so a content edit does not break the suite.
//
// A site calls `defineKernelSuite` from one of its test files, after it registers `guardPageErrors`. The kernel
// imports nothing from `@playwright/test` at run time (sitePage.ts says why), so the site passes its `test` and
// `expect`.

import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import type { Page } from '@playwright/test'
import type { HarvestedDiagram } from '../scripts/diagram.ts'
import type { HarvestedDoc } from '../scripts/harvestDocs.ts'
import { siteDir } from '../scripts/paths.ts'
import type { SiteConfig } from '../scripts/siteConfig.ts'
import { languageForPath } from '../scripts/sourcePaths.ts'
import type { PlaywrightExpect, PlaywrightTest, SitePage } from './sitePage.ts'

export interface KernelSuiteOptions {
  test: PlaywrightTest
  expect: PlaywrightExpect
  /** The config of the site under test. */
  config: SiteConfig
  /** The page helpers of the site (createSitePage). */
  sitePage: SitePage
}

interface Harvest {
  docs: HarvestedDoc[]
  diagrams: HarvestedDiagram[]
}

function filesUnder(dir: string): string[] {
  return readdirSync(dir).sort().flatMap(entry => {
    const path = join(dir, entry)
    return statSync(path).isDirectory() ? filesUnder(path) : [path]
  })
}

/** Registers the describe blocks of the kernel pages. */
export function defineKernelSuite({ test, expect, config, sitePage }: KernelSuiteOptions): void {
  const describe = test.describe
  const generated = join(siteDir(config), 'generated')
  const harvest = JSON.parse(readFileSync(join(generated, 'arch-data.json'), 'utf8')) as Harvest
  /** The app path of a route path: the base of the site and the path without its leading slash. */
  const app = (path: string): string => `${config.base}${path.replace(/^\//, '')}`
  const slugOf = (path: string): string => path.slice(path.lastIndexOf('/') + 1, -'.md'.length)
  const harvestedMarkdown = (path: string): string => readFileSync(join(generated, 'markdown', path), 'utf8')

  async function open(page: Page, href: string, ready: 'content' | 'none' = 'content'): Promise<void> {
    await sitePage.openAndReload(page, href)
    if (ready === 'content') {
      await sitePage.contentReady(page)
    }
  }

  async function expectUrlContains(page: Page, fragment: string): Promise<void> {
    await page.waitForURL(url => url.href.includes(fragment), { timeout: 10_000 })
  }

  // cmdk renders palette results as [data-slot=command-item] divs (no anchors);
  // navigation is asserted by selecting the item and checking the URL.
  const paletteItems = (page: Page) => page.locator('.palette-results [data-slot="command-item"]')

  describe('source viewer', () => {
    // a bundled source file that the viewer highlights, with enough lines for a range
    const sourcesDir = join(generated, 'sources')
    const bundled = filesUnder(sourcesDir)
      .map(file => ({ path: relative(sourcesDir, file).split(sep).join('/'), lines: readFileSync(file, 'utf8').split('\n') }))
      .find(file => languageForPath(file.path) !== null && file.lines.length >= 30)
    assert.ok(bundled, 'the harvest bundles no highlighted source file with 30 lines')
    const fileLines = bundled.lines.length

    test('a /source deep link highlights code, scrolls to ?line=, and offers Open on GitHub', async ({ page }) => {
      const line = Math.min(30, fileLines)
      await open(page, app(`/source/${bundled.path}?line=${line}`))
      // token spans only exist once shiki finished (the plain fallback has no spans inside lines)
      await page.waitForSelector('.source-view .line span', { timeout: 15_000 })
      const lineElement = page.locator(`#L${line}`)
      assert.strictEqual(await lineElement.count(), 1)
      const inView = await lineElement.evaluate(element => {
        const rect = element.getBoundingClientRect()
        return rect.top >= 0 && rect.top <= window.innerHeight
      })
      assert.ok(inView)
      assert.ok((await lineElement.locator('a.line-no').getAttribute('href'))?.includes(`line=${line}`))
      const github = page.locator('.entity-actions a[href^="https://github.com"]')
      assert.ok((await github.getAttribute('href'))?.includes(`${config.github.pathPrefix}${bundled.path}#L${line}`))
    })

    test('selects source ranges, canonicalizes their URL, and copies the link or exact code', async ({ page }) => {
      const start = Math.min(10, Math.max(1, fileLines - 2))
      const end = Math.min(start + 2, fileLines)
      await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
      await open(page, app(`/source/${bundled.path}?line=${end}-${start}`))
      await page.waitForURL(url => url.searchParams.get('line') === `${start}-${end}`)
      await page.waitForSelector('.source-view .line span', { timeout: 15_000 })

      assert.strictEqual(await page.locator('.source-view .line-selected').count(), end - start + 1)
      assert.ok((await page.locator('.entity-actions a[href^="https://github.com"]').getAttribute('href'))?.includes(`#L${start}-L${end}`))

      const single = Math.min(start + 1, end)
      await page.locator(`a.line-no[data-line="${single}"]`).click()
      await page.waitForURL(url => url.searchParams.get('line') === String(single))
      assert.strictEqual(await page.locator('.source-view .line-selected').count(), 1)

      await page.locator(`a.line-no[data-line="${end}"]`).click({ modifiers: ['Shift'] })
      const rangeStart = Math.min(single, end)
      await page.waitForURL(url => url.searchParams.get('line') === `${rangeStart}-${end}`)

      await page.getByRole('button', { name: 'Copy link' }).click()
      await expect(page.getByRole('button', { name: 'Link copied' })).toBeVisible()
      assert.ok((await page.evaluate(() => navigator.clipboard.readText())).includes(`?line=${rangeStart}-${end}`))

      await page.getByRole('button', { name: 'Copy code' }).click()
      await expect(page.getByRole('button', { name: 'Code copied' })).toBeVisible()
      assert.strictEqual(await page.evaluate(() => navigator.clipboard.readText()), bundled.lines.slice(rangeStart - 1, end).join('\n'))
    })

    test('drops invalid source selections and clamps selections beyond the file', async ({ page }) => {
      await open(page, app(`/source/${bundled.path}?line=0`))
      await page.waitForURL(url => !url.searchParams.has('line'))
      await open(page, app(`/source/${bundled.path}?line=${fileLines + 10}`))
      await page.waitForURL(url => url.searchParams.get('line') === String(fileLines))
      await expect(page.locator(`#L${fileLines}`)).toHaveClass(/line-selected/)
    })
  })

  // ```diagram fences (```air-diagram is its first name) and doc pictures (`![alt](source "title")` alone on a line)
  const diagramFenceIds = (markdown: string): string[] =>
    [...markdown.matchAll(/^```(?:air-diagram|diagram)[ \t]*\n(?:.*\n)*?id: ([a-z0-9-]+)/gm)].map(match => match[1])
  const docPictures = (markdown: string): { source: string, title: string }[] =>
    [...markdown.matchAll(/^!\[[^\]]*]\((\S+) "([^"]+)"\)[ \t]*$/gm)].map(match => ({ source: match[1], title: match[2] }))

  describe('guides', () => {
    const guides = harvest.docs.filter(doc => doc.kind === 'guide')
    const pictureGuide = guides.find(doc => docPictures(harvestedMarkdown(doc.path)).length > 0)
    const diagramGuide = guides.find(doc => diagramFenceIds(harvestedMarkdown(doc.path)).length > 0)
    const group = config.guideGroups.find(entry => guides.some(doc => doc.group === entry.id))

    test('the guides index groups each guide under its group', async ({ page }) => {
      assert.ok(group, 'no guide names a guide group of the site config')
      const guide = guides.find(doc => doc.group === group.id)!
      await open(page, app('/guides'))
      const section = page.locator('section', { has: page.locator('h2', { hasText: group.label }) })
      assert.ok(await section.locator(`a[href="${app(`/guides/${slugOf(guide.path)}`)}"]`).count() >= 1)
    })

    test('the guides index lists no decision record and folds the historical notes', async ({ page }) => {
      await open(page, app('/guides'))
      assert.strictEqual(await page.locator(`.doc-list a[href^="${app('/decisions/')}"]`).count(), 0)
      const historical = harvest.docs.filter(doc => doc.kind === 'note' && doc.state === 'historical')
      const fold = page.locator('details.historical-notes')
      if (historical.length > 0) {
        assert.strictEqual(await fold.getAttribute('open'), null)
        assert.strictEqual(await fold.locator('a').count(), historical.length)
      }
    })

    test('a guide page embeds each picture that the harvest copied, with its title as the caption', async ({ page }) => {
      test.skip(pictureGuide === undefined, 'no guide of this site has a doc picture')
      assert.ok(pictureGuide)
      const pictures = docPictures(harvestedMarkdown(pictureGuide.path))
      await open(page, app(`/guides/${slugOf(pictureGuide.path)}`))
      const figures = page.locator('.doc-figure')
      await expect(figures).toHaveCount(pictures.length)
      for (const [index, picture] of pictures.entries()) {
        const image = figures.nth(index).locator('img')
        const src = await image.getAttribute('src')
        assert.ok(src?.includes(app('/assets/')), `${src} is a harvested asset`)
        assert.ok(src?.endsWith(picture.source.slice(picture.source.lastIndexOf('.'))), `${src} keeps the extension of ${picture.source}`)
        await image.scrollIntoViewIfNeeded()
        await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.complete ? element.naturalWidth : 0)).toBeGreaterThan(0)
        const caption = await figures.nth(index).locator('figcaption').textContent()
        assert.ok(caption?.startsWith(picture.title), `${caption} starts with ${picture.title}`)
        assert.strictEqual(await figures.nth(index).locator('figcaption a').getAttribute('href'), src)
      }
      assert.strictEqual(await page.locator('.doc-figure-error').count(), 0)
    })

    test('a guide page renders each diagram fence as a hand-laid figure with its step bar', async ({ page }) => {
      if (diagramGuide === undefined) {
        return // no guide of this site embeds a hand-laid diagram — nothing to check
      }
      await open(page, app(`/guides/${slugOf(diagramGuide.path)}`), 'none')
      const figures = page.locator('.hand-laid-diagram')
      await expect(figures).toHaveCount(diagramFenceIds(harvestedMarkdown(diagramGuide.path)).length)
      await expect(figures.first().locator('.react-flow__node-diagramNode').first()).toBeVisible()
      assert.ok(await figures.first().locator('.diagram-step-bar').count() >= 1)
      assert.ok(await figures.first().locator('.diagram-legend').count() >= 1)
      assert.strictEqual(await page.locator('.air-diagram-error').count(), 0)
      // the figure walks its own steps and links the explainer chapter
      await figures.first().locator('button[aria-label="Next step"]').click()
      await expect(figures.first().locator('.react-flow__node.step-active')).not.toHaveCount(0)
      // the chapter walk belongs to the explainer canvas, not to a figure
      assert.strictEqual(await figures.first().locator('.diagram-step-chapter, .diagram-next-chapter, .hand-off').count(), 0)
      assert.ok((await figures.first().locator('figcaption a').getAttribute('href'))?.includes(app('/explain/')))
    })
  })

  describe('explainers (ADR 0204)', () => {
    const explainer = harvest.docs.find(doc => doc.kind === 'explainer' && (doc.chapters?.length ?? 0) >= 3)
    if (explainer === undefined) {
      test('the site has an explainer with three chapters', () => {
        test.skip(true, 'no explainer with three chapters is harvested')
      })
      return
    }
    const explainerSlug = slugOf(explainer.path)
    const chapters = explainer.chapters!
    const firstDiagramChapter = chapters.find(chapter => chapter.diagram !== null)!
    const firstDiagram = harvest.diagrams.find(diagram => diagram.id === firstDiagramChapter.diagram)!
    const chapterUrl = (chapter: string, query = ''): string => app(`/explain/${explainerSlug}/${chapter}${query}`)
    const diagramNodes = (page: Page) => page.locator('.explainer-canvas-pane .react-flow__node-diagramNode')
    async function openChapter(page: Page, chapter: string, query = ''): Promise<void> {
      await open(page, chapterUrl(chapter, query), 'none')
      await expect(diagramNodes(page).first()).toBeVisible()
    }

    test('the index lists the explainer with its chapters', async ({ page }) => {
      await open(page, app('/explain'))
      const card = page.locator('.explainer-card', { has: page.locator(`a[href="${app(`/explain/${explainerSlug}`)}"]`) })
      assert.strictEqual(await card.count(), 1)
      assert.strictEqual(await card.locator('ol a').count(), chapters.length)
    })

    test('a bare explainer URL opens the first chapter with every card and no dimming', async ({ page }) => {
      await open(page, app(`/explain/${explainerSlug}`), 'none')
      await expect(diagramNodes(page).first()).toBeVisible()
      await expectUrlContains(page, chapterUrl(chapters[0].id))
      const chapterDiagram = harvest.diagrams.find(diagram => diagram.id === chapters[0].diagram)!
      await expect(diagramNodes(page)).toHaveCount(chapterDiagram.nodes.length)
      assert.strictEqual(await page.locator('.explainer-canvas-pane .react-flow__node.sel-dimmed').count(), 0)
      assert.strictEqual(await page.locator('.chapter-rail a[aria-current="page"]').count(), 1)
      assert.ok(await page.locator('.explainer-prose .explainer-steps .step-card').count() >= 1)
    })

    test('a step lights its cards, dims the rest, and the arrow key walks on', async ({ page }) => {
      await openChapter(page, firstDiagramChapter.id, '?step=1')
      const active = page.locator('.explainer-canvas-pane .react-flow__node.step-active')
      await expect(active).toHaveCount(firstDiagram.steps[0].nodes.length)
      const dimmed = page.locator('.explainer-canvas-pane .react-flow__node.sel-dimmed').first()
      await expect.poll(() => dimmed.evaluate(element => getComputedStyle(element).opacity)).toBe('0.3')
      assert.ok((await page.locator('.diagram-step-bar .diagram-steps-counter').textContent())?.includes(`Step 1 of ${firstDiagram.steps.length}`))
      assert.strictEqual(await page.locator('.explainer-prose .step-card.active').count(), 1)
      await page.locator('.explainer-canvas-pane .react-flow').click({ position: { x: 5, y: 5 } })
      await page.keyboard.press('ArrowRight')
      await expectUrlContains(page, 'step=2')
      await expect(page.locator('.explainer-canvas-pane .react-flow__node.step-trail')).not.toHaveCount(0)
      await page.locator('.diagram-step-bar .diagram-overview').click()
      await expect.poll(() => page.url()).not.toContain('step=')
    })

    const stepsOf = (chapter: { diagram: string | null }): number =>
      harvest.diagrams.find(diagram => diagram.id === chapter.diagram)?.steps.length ?? 0
    // two chapters in a row that both have steps: the walk from the last step of one to the next one
    const handOffIndex = chapters.findIndex((chapter, index) => stepsOf(chapter) > 0 && index + 1 < chapters.length && stepsOf(chapters[index + 1]) > 0)
    const proseTop = (page: Page) => page.locator('.explainer-prose').evaluate(element => element.scrollTop)

    test('the last step hands off to the next chapter, and Previous walks back', async ({ page }) => {
      test.skip(handOffIndex < 0, 'no two chapters in a row have steps')
      const from = chapters[handOffIndex]
      const to = chapters[handOffIndex + 1]
      const last = stepsOf(from)
      await openChapter(page, from.id, `?step=${last}`)
      const bar = page.locator('.diagram-step-bar')
      await expect(bar.locator('.diagram-step-chapter')).toContainText(`Chapter ${handOffIndex + 1} of ${chapters.length}`)
      await expect(bar.locator('[data-hand-off="chapter"]')).toContainText(to.title)
      await expect(page.locator('.chapter-rail a', { hasText: to.title }).locator('.chapter-up-next')).toHaveCount(1)
      await expect(bar.locator('.diagram-overview')).toBeEnabled()
      // a new chapter starts at the top of its text
      const scrolled = await page.locator('.explainer-prose').evaluate((element) => {
        element.scrollTop = element.scrollHeight
        return element.scrollTop > 0
      })
      await bar.locator('.diagram-next-chapter').click()
      await expectUrlContains(page, chapterUrl(to.id))
      await expect.poll(() => page.url()).not.toContain('step=')
      await expect(page.locator('.explainer-chapter-title')).toContainText(to.title)
      if (scrolled) {
        await expect.poll(() => proseTop(page)).toBe(0)
      }
      await expect(page.locator('.chapter-rail a', { hasText: from.title })).toHaveAttribute('data-state', 'done')
      await bar.locator('button[aria-label="Previous step"]').click()
      await expectUrlContains(page, `${chapterUrl(from.id)}?step=${last}`)
    })

    test('the last chapter ends the explainer, and its text scrolls', async ({ page }) => {
      const last = chapters[chapters.length - 1]
      await open(page, chapterUrl(last.id), 'none')
      const end = page.locator('.explainer-pager [data-hand-off="end"]')
      await expect(end).toBeVisible()
      assert.strictEqual(await page.locator('.explainer-pager [data-hand-off="chapter"]').count(), 0)
      if (last.diagram === null) {
        const pane = page.locator('.explainer-page-scroll')
        const scrollable = await pane.evaluate(element => element.scrollHeight > element.clientHeight)
        if (scrollable) {
          await pane.evaluate((element) => {
            element.scrollTop = element.scrollHeight
          })
          await expect.poll(() => pane.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
        }
      }
    })

    test('on a phone the step bar stays small and the current chapter chip is in view', async ({ page }) => {
      test.skip(handOffIndex < 0, 'no two chapters in a row have steps')
      await page.setViewportSize({ width: 390, height: 844 })
      const target = chapters.findLast(chapter => stepsOf(chapter) > 0)!
      await openChapter(page, target.id, `?step=${stepsOf(target)}`)
      const pane = await page.locator('.explainer-canvas-pane').boundingBox()
      const bar = await page.locator('.diagram-step-bar').boundingBox()
      assert.ok(pane !== null && bar !== null)
      assert.ok(bar.height < pane.height / 2, `the step bar is ${bar.height}px of a ${pane.height}px canvas`)
      await expect(page.locator('.diagram-step-chapter')).toBeHidden()
      const rail = page.locator('.chapter-rail')
      await expect.poll(async () => {
        const railBox = await rail.boundingBox()
        const chipBox = await rail.locator('a[aria-current="page"]').boundingBox()
        return railBox !== null && chipBox !== null && chipBox.x >= railBox.x - 1 && chipBox.x + chipBox.width <= railBox.x + railBox.width + 1
      }).toBe(true)
    })

    test('a clicked card opens its detail with a source link, and Escape closes it', async ({ page }) => {
      const withSource = firstDiagram.nodes.find(node => node.symbolRef !== null || node.sourceRef !== null)!
      await openChapter(page, firstDiagramChapter.id, `?node=${withSource.id}`)
      const panel = page.locator('.explainer-canvas-pane .detail-panel')
      await expect(panel).toBeVisible()
      assert.ok((await panel.locator('h2').textContent())?.includes(withSource.title))
      const link = panel.locator('a.source-link').first()
      const href = await link.getAttribute('href')
      assert.ok(href?.includes('line=') && (href.includes(app('/source/')) || href.startsWith('https://github.com/')), `${href} links the code`)
      await page.locator('.explainer-canvas-pane .react-flow').click({ position: { x: 5, y: 5 } })
      await page.keyboard.press('Escape')
      await expect.poll(() => page.url()).not.toContain('node=')
    })

    test('the chapter rail and PageDown move between chapters', async ({ page }) => {
      await openChapter(page, chapters[0].id)
      await page.waitForLoadState('load')
      const title = page.locator('.explainer-chapter-title')
      await page.locator('.chapter-rail a', { hasText: chapters[1].title }).click()
      await expect(title).toContainText(chapters[1].title)
      await expectUrlContains(page, chapterUrl(chapters[1].id))
      await title.click()
      await page.keyboard.press('PageDown')
      await expect(title).toContainText(chapters[2].title)
      await expectUrlContains(page, chapterUrl(chapters[2].id))
      await page.keyboard.press('PageUp')
      await expect(title).toContainText(chapters[1].title)
      await expectUrlContains(page, chapterUrl(chapters[1].id))
    })

    test('the dark theme keeps every card and recolors the canvas', async ({ page }) => {
      await openChapter(page, firstDiagramChapter.id)
      const background = () => page.locator('.explainer-canvas-pane .react-flow').first().evaluate(element => getComputedStyle(element).backgroundColor)
      const light = await background()
      await page.evaluate(() => document.documentElement.classList.add('dark'))
      await expect.poll(background).not.toBe(light)
      await expect(diagramNodes(page)).toHaveCount(firstDiagram.nodes.length)
    })
  })

  describe('command palette', () => {
    // a word of a guide title: the docs provider of the kernel finds it on every site
    const guide = harvest.docs.find(doc => doc.kind === 'guide')
    assert.ok(guide, 'no guide is harvested')
    const word = guide.title.toLowerCase().split(/[^a-z]+/).find(candidate => candidate.length >= 5) ?? guide.title.slice(0, 5).toLowerCase()

    test('opens with ⌘K, finds results grouped by kind, and closes on Escape', async ({ page }) => {
      await open(page, app('/guides'))
      await page.keyboard.press('ControlOrMeta+k')
      await expect(page.locator('.palette')).toHaveCount(1)
      assert.strictEqual(await page.evaluate(() => document.activeElement?.tagName), 'INPUT')
      await page.fill('.palette input', word)
      await expect.poll(() => paletteItems(page).count()).toBeGreaterThan(0)
      assert.ok(await page.locator('.palette-results [cmdk-group-heading]').count() > 0)
      await page.press('.palette input', 'Escape')
      await expect(page.locator('.palette')).toHaveCount(0)
    })

    test('supports / open, arrow keys, and Enter navigation', async ({ page }) => {
      await open(page, app('/decisions'))
      await page.keyboard.press('/')
      // the dialog moves the focus after it mounts, which a loaded worker can take a frame or two for
      await expect.poll(() => page.evaluate(() => document.activeElement?.tagName)).toBe('INPUT')
      await page.fill('.palette input', word)
      await page.press('.palette input', 'ArrowDown')
      assert.strictEqual(await page.locator('[data-slot="command-item"][data-selected="true"]').count(), 1)
      await page.press('.palette input', 'Enter')
      await page.waitForURL(url => !url.pathname.endsWith('/decisions'))
      await expect(page.locator('.palette')).toHaveCount(0)
    })
  })

  describe('decisions', () => {
    const decisions = harvest.docs.filter(doc => doc.adr !== undefined)
    const decisionRouteOf = (path: string) => app(`/decisions/${slugOf(path)}`)

    test('the index hides the records of the hidden topic until the reader asks for them', async ({ page }) => {
      await open(page, app('/decisions'))
      const hidden = decisions.filter(doc => doc.adr!.topic === config.decisions.hiddenTopic).length
      await expect(page.locator('.decision-row')).toHaveCount(decisions.length - hidden)
      if (hidden === 0) {
        return
      }
      await page.locator('.decision-hidden-note button').click()
      await expectUrlContains(page, 'topic=all')
      await expect(page.locator('.decision-row')).toHaveCount(decisions.length)
    })

    test('the status filter lives in the query, and a superseded row links its successor', async ({ page }) => {
      await open(page, app('/decisions?status=superseded&topic=all'))
      const superseded = decisions.filter(doc => doc.adr!.status === 'superseded')
      await expect(page.locator('.decision-row')).toHaveCount(superseded.length)
      const withSuccessor = superseded.find(doc => doc.adr!.supersededBy !== null)
      if (withSuccessor === undefined) {
        return // no superseded record of this site links its successor
      }
      const row = page.locator('.decision-row', { has: page.locator(`a.decision-title[href="${decisionRouteOf(withSuccessor.path)}"]`) })
      assert.strictEqual(await row.locator(`a[href="${decisionRouteOf(withSuccessor.adr!.supersededBy!)}"]`).count(), 1)
    })

    test('a superseded record names its successor above the body', async ({ page }) => {
      const record = decisions.find(doc => doc.adr!.status === 'superseded' && doc.adr!.supersededBy !== null)
      if (record === undefined) {
        return // no superseded record of this site links its successor
      }
      await open(page, decisionRouteOf(record.path))
      await expect(page.locator('.decision-superseded a')).toHaveAttribute('href', decisionRouteOf(record.adr!.supersededBy!))
      await expect(page.locator('.decision-facts .decision-status')).toHaveAttribute('data-status', 'superseded')
    })

    // a dated record has no number (adrFiles.ts of the kernel); the oldest numbered one stands for the numbered records
    const numbered = decisions.filter(doc => doc.adr!.number !== null)
    const lastNumbered = numbered[numbered.length - 1]

    test('an old doc link to a decision record opens the decision page', async ({ page }) => {
      assert.ok(lastNumbered, 'no numbered decision record is harvested')
      await open(page, app(`/doc/${encodeURIComponent(lastNumbered.path)}`))
      await expect(page.locator('.decision-facts')).toContainText(`ADR ${lastNumbered.adr!.number}`)
      await expect(page.locator('.route-outlet .page-breadcrumb')).toContainText('Decisions')
    })

    test('the palette finds a decision record by its number', async ({ page }) => {
      assert.ok(lastNumbered, 'no numbered decision record is harvested')
      await open(page, app('/guides'))
      await page.keyboard.press('/')
      await page.fill('.palette input', String(Number(lastNumbered.adr!.number)))
      await expect(paletteItems(page).filter({ hasText: lastNumbered.title }).first()).toBeVisible()
    })
  })

  describe('theme', () => {
    test('the picker sets dark mode and persists across reloads', async ({ page }) => {
      await open(page, app('/guides'))
      const resolved = () => page.evaluate(() => (document.documentElement.classList.contains('dark') ? 'dark' : 'light'))
      await page.click('.theme-toggle')
      await page.getByRole('menuitemradio', { name: 'Dark' }).click()
      assert.strictEqual(await resolved(), 'dark')
      await page.reload()
      await expect.poll(resolved).toBe('dark')
    })
  })
}
