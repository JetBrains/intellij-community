// Locates a Chromium executable for the e2e tests without forcing a download.
// Preference order: $PLAYWRIGHT_CHROMIUM_EXECUTABLE, then the newest browser in the local
// Playwright cache. Populate the cache once with: pnpm exec playwright install chromium-headless-shell

import { existsSync, readdirSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CACHE_DIRS = [
  join(homedir(), 'Library/Caches/ms-playwright'), // macOS
  join(homedir(), '.cache/ms-playwright'), // Linux
]

const EXECUTABLE_CANDIDATES = [
  'chrome-headless-shell-mac-arm64/chrome-headless-shell',
  'chrome-headless-shell-mac-x64/chrome-headless-shell',
  'chrome-headless-shell-linux64/chrome-headless-shell',
  'chrome-mac-arm64/Chromium.app/Contents/MacOS/Chromium',
  'chrome-mac-x64/Chromium.app/Contents/MacOS/Chromium',
  'chrome-linux/chrome',
]

export function findChromium(): string {
  const override = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
  if (override) {
    return override
  }
  for (const cacheDir of CACHE_DIRS) {
    if (!existsSync(cacheDir)) {
      continue
    }
    const builds = readdirSync(cacheDir)
      .filter(name => name.startsWith('chromium_headless_shell-') || name.startsWith('chromium-'))
      .sort()
      .reverse()
    for (const build of builds) {
      for (const candidate of EXECUTABLE_CANDIDATES) {
        const executable = join(cacheDir, build, candidate)
        if (existsSync(executable)) {
          return executable
        }
      }
    }
  }
  throw new Error(
    'No Chromium found for e2e tests. Run `pnpm exec playwright install chromium-headless-shell` once, '
    + 'or set PLAYWRIGHT_CHROMIUM_EXECUTABLE.',
  )
}
