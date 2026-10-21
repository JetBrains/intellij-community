// Renders every hand-laid diagram (ADR 0204 of Air) to PNG: the explainer canvas of each chapter at the
// overview and at each step, in the light and the dark theme, plus the compact figures of each guide
// and spec that embeds one. A review tool for the author and the critic, not a test: a site runs it after
// `pnpm run build` through its own `e2e/diagramShots.ts`, which passes its Playwright `chromium` and its spec
// route. The pictures land under out/<site id>-site-shots/.

import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { BrowserType } from '@playwright/test'
import type { HarvestedDiagram } from '../scripts/diagram.ts'
import type { HarvestedDoc } from '../scripts/harvestDocs.ts'
import { loadSiteConfig, repoRoot, siteDir } from '../scripts/paths.ts'
import { findChromium } from './browser.ts'

export interface DiagramShotsOptions {
  /** The `chromium` of the Playwright package of the site. */
  chromium: BrowserType
  /** The path of the `site.config.ts` of the site. */
  configPath: string
  /** The app path of a spec page, after the base, or null when the site has no spec pages. */
  specRoute: ((specId: string) => string) | null
  /** The diagram ids to render; empty renders every diagram. */
  only: readonly string[]
}

/** Renders the pictures and returns their count. */
export async function renderDiagramShots({ chromium, configPath, specRoute, only: onlyIds }: DiagramShotsOptions): Promise<number> {
  const config = await loadSiteConfig(configPath)
  const docsRoot = siteDir(config)
  const PORT = 4319
  const BASE = `http://127.0.0.1:${PORT}${config.base.replace(/\/$/, '')}`
  const OUT = join(repoRoot, `out/${config.id}-site-shots`)
  const only = new Set(onlyIds)

  const archData = JSON.parse(readFileSync(join(docsRoot, 'generated/arch-data.json'), 'utf8')) as {
    docs: HarvestedDoc[]
    diagrams: HarvestedDiagram[]
    specs: { id: string, path: string }[]
  }

  async function waitForServer(): Promise<void> {
    for (let attempt = 0; attempt < 100; attempt++) {
      try {
        const response = await fetch(`${BASE}/`)
        if (response.ok) {
          return
        }
      }
      catch {
        // not up yet
      }
      await new Promise(resolve => setTimeout(resolve, 100))
    }
    throw new Error('the static server did not start')
  }

  const server = spawn(process.execPath, [join(import.meta.dirname, 'serve-site.ts'), resolve(configPath)], { env: { ...process.env, SITE_PORT: String(PORT) }, stdio: 'ignore' })
  let count = 0
  try {
    await waitForServer()
    rmSync(OUT, { recursive: true, force: true })
    mkdirSync(OUT, { recursive: true })
    const browser = await chromium.launch({ executablePath: findChromium() })
    for (const theme of ['light', 'dark'] as const) {
      const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 1 })
      await context.addInitScript(([key, value]: [string, string]) => localStorage.setItem(key, value), [config.themeStorageKey, theme] as [string, string])
      const page = await context.newPage()
      const errors: string[] = []
      page.on('pageerror', error => errors.push(error.message))
      page.on('console', message => {
        if (message.type() === 'error') {
          errors.push(message.text())
        }
      })
      // the explainer chapters: the canvas pane at the overview and at each step
      for (const doc of archData.docs) {
        if (doc.kind !== 'explainer' || doc.chapters === undefined) {
          continue
        }
        const slug = doc.path.slice(doc.path.lastIndexOf('/') + 1, -'.md'.length)
        for (const chapter of doc.chapters) {
          if (chapter.diagram === null || (only.size > 0 && !only.has(chapter.diagram))) {
            continue
          }
          const diagram = archData.diagrams.find(candidate => candidate.id === chapter.diagram)
          const steps = diagram?.steps.length ?? 0
          for (let step = 0; step <= steps; step++) {
            const url = `${BASE}/explain/${slug}/${chapter.id}${step === 0 ? '' : `?step=${step}`}`
            await page.goto(url)
            await page.locator('.explainer-canvas-pane .react-flow__node.react-flow__node-diagramNode').first().waitFor()
            await page.waitForTimeout(400)
            const file = join(OUT, `${chapter.diagram}-${theme}-${step === 0 ? 'overview' : `step${step}`}.png`)
            await page.locator('.explainer-canvas-pane').screenshot({ path: file })
            count++
          }
          // the whole page once, so the prose pane and the card are reviewed too
          await page.goto(`${BASE}/explain/${slug}/${chapter.id}?step=1&node=${diagram?.steps[0]?.nodes[0] ?? ''}`)
          await page.locator('.explainer-canvas-pane .react-flow__node.react-flow__node-diagramNode').first().waitFor()
          await page.waitForTimeout(400)
          await page.screenshot({ path: join(OUT, `${chapter.diagram}-${theme}-page.png`) })
          count++
        }
      }
      // the compact figures of the guides and specs
      const embedded = new Set(archData.diagrams.map(diagram => diagram.id))
      const fenceIds = (path: string): string[] => {
        const bodyPath = join(docsRoot, 'generated/markdown', path)
        if (!existsSync(bodyPath)) {
          return []
        }
        return [...readFileSync(bodyPath, 'utf8').matchAll(/^```(?:air-diagram|diagram)\n(?:.*\n)*?id: ([a-z0-9-]+)/gm)]
          .map(match => match[1])
          .filter(id => embedded.has(id) && (only.size === 0 || only.has(id)))
      }
      const shootFigures = async (route: string, ids: string[]): Promise<void> => {
        await page.goto(`${BASE}${route}`)
        const figures = page.locator('.hand-laid-diagram')
        await figures.first().waitFor()
        await page.waitForTimeout(600)
        for (const [index, id] of ids.entries()) {
          await figures.nth(index).scrollIntoViewIfNeeded()
          await figures.nth(index).screenshot({ path: join(OUT, `${id}-${theme}-figure.png`) })
          count++
        }
      }
      for (const doc of archData.docs) {
        const ids = doc.kind === 'guide' || doc.kind === 'note' ? fenceIds(doc.path) : []
        if (ids.length > 0) {
          await shootFigures(`/guides/${doc.path.slice(doc.path.lastIndexOf('/') + 1, -'.md'.length)}`, ids)
        }
      }
      for (const spec of specRoute === null ? [] : archData.specs) {
        const ids = fenceIds(spec.path)
        if (ids.length > 0) {
          await shootFigures(specRoute!(spec.id), ids)
        }
      }
      if (errors.length > 0) {
        console.error(`${theme}: ${errors.length} console or page error(s)\n${[...new Set(errors)].join('\n')}`)
      }
      await context.close()
    }
    await browser.close()
    console.log(`diagramShots: ${count} pictures under ${OUT}`)
  }
  finally {
    server.kill()
  }
  return count
}
