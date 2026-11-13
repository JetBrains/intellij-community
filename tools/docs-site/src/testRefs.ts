// The `[@test]` lines of a spec as link chips, for the spec page of every site.

import { resolveSpecPath, TEST_LINK_LINE, testLinkNames } from '../scripts/sourcePaths.ts'
import { repoSourceHref } from './sourceRef'

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/**
 * Turns the `[@test]` lines of a spec into link chips before the markdown pipeline runs. The raw form is not valid
 * markdown and renders as dead text. The chip names the file and opens it in the source viewer when the site bundles
 * it, or on GitHub otherwise. The test names of the line follow the chip as code.
 *
 * `specDir` is the content-relative folder of the spec. With `rootPaths`, as in an OpenSpec root, a path is from the
 * repository root.
 */
export function linkTestRefs(markdown: string, contentRoot: string, specDir: string, rootPaths: boolean): string {
  return markdown.replace(TEST_LINK_LINE, (_match, indent: string, raw: string, names: string | undefined) => {
    const repoPath = resolveSpecPath(contentRoot, specDir, rootPaths, raw)
    const { href, external } = repoSourceHref(repoPath)
    const name = repoPath.slice(repoPath.lastIndexOf('/') + 1)
    const attrs = external ? ' target="_blank" rel="noreferrer"' : ''
    const tests = testLinkNames(names).map(test => `<code>${escapeHtml(test)}</code>`).join(', ')
    return `${indent}<a class="test-chip" href="${href}" title="${repoPath}"${attrs}>${name}</a>${tests.length > 0 ? ` ${tests}` : ''}`
  })
}
