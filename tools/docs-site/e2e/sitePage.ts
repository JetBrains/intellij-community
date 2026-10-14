// The shared set-up of the e2e test files of a site: a guard that hands each test its fresh page and fails the test
// on a console error or an uncaught page error, and the route helpers that more than one test file uses.
//
// The kernel imports nothing from `@playwright/test` at run time. Playwright refuses a second copy of its test
// module, and the kernel resolves its own copy. So a site passes its `test` and `expect` to `createSitePage`.

import assert from 'node:assert/strict'
import type { Page } from '@playwright/test'

/** The `test` of `@playwright/test`. */
export type PlaywrightTest = typeof import('@playwright/test')['test']
/** The `expect` of `@playwright/test`. */
export type PlaywrightExpect = typeof import('@playwright/test')['expect']

export interface SitePage {
  /**
   * Registers the hooks that hand each test its fresh page through `onPage`, and fail the test when
   * the page logs a console error or throws an uncaught error. Call it once at the top level of a
   * test file.
   */
  guardPageErrors(onPage: (page: Page) => void): void
  /** Waits until the app shell and the page content are attached, with no fixed settle. */
  contentReady(page: Page): Promise<void>
  /**
   * Navigates to an app route and reloads it, so the static server fallback is exercised. The app
   * boots before the reload: `domcontentloaded` does not wait for top-level await, and a reload
   * during the boot aborts the data fetch, which the console error guard reports as
   * "Failed to fetch".
   */
  openAndReload(page: Page, href: string): Promise<void>
}

/** The page helpers bound to the `test` and the `expect` of the site. */
export function createSitePage(test: PlaywrightTest, expect: PlaywrightExpect): SitePage {
  return {
    guardPageErrors(onPage) {
      let consoleErrors: string[] = []
      test.beforeEach(({ page }) => {
        onPage(page)
        consoleErrors = []
        page.on('console', message => {
          if (message.type() === 'error') {
            consoleErrors.push(message.text())
          }
        })
        page.on('pageerror', error => consoleErrors.push(String(error)))
      })
      test.afterEach(() => {
        assert.deepStrictEqual(consoleErrors, [])
      })
    },
    async contentReady(page) {
      await expect(page.locator('.doc-page, .markdown-body, .react-flow__pane, .source-view, .flow-prose').first()).toBeAttached()
    },
    async openAndReload(page, href) {
      await page.goto(href, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('.app-shell')).toBeAttached()
      await page.reload({ waitUntil: 'domcontentloaded' })
    },
  }
}
