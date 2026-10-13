import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { addCodeSpanPaths, codeSpanPathsJson, unresolvedCodeSpans } from './codeSpans.ts'
import { temporaryTree } from './tempTree.ts'

const FILES: Record<string, string> = {
  'build/dev-build.json': '{}\n',
  'build/docs/guides/near.md': 'A guide.\n',
  'build/docs/guides/build/shadow.json': '{}\n',
  'build/shadow.json': '{}\n',
}

const BODY = [
  'The registry is `build/dev-build.json`, and `near.md` sits next to the guide.',
  'The doc folder wins for `build/shadow.json`, and `../../readme.md` is doc-relative by its form.',
  'A jar holds `META-INF/plugin.xml`.',
  '',
  '```',
  '`build/other.json` in a fence is code.',
  '```',
].join('\n')

describe('unresolvedCodeSpans', () => {
  it('names the repository path of a span that the doc folder does not resolve, or null', () => {
    const root = temporaryTree('code-spans-', FILES)
    assert.deepEqual(unresolvedCodeSpans('', 'build/docs/guides/g.md', BODY, root), new Map([
      ['build/dev-build.json', 'build/dev-build.json'],
      ['META-INF/plugin.xml', null],
    ]))
  })

  it('tries the content root of a plugin, then the repository root', () => {
    const root = temporaryTree('code-spans-', { 'plugins/air/backend/acp/TODO.md': '# Todo\n', 'community/build/X.kt': '' })
    assert.deepEqual(unresolvedCodeSpans('plugins/air', 'docs/note.md', 'See `backend/acp/TODO.md` and `community/build/X.kt`.', root), new Map([
      ['backend/acp/TODO.md', 'plugins/air/backend/acp/TODO.md'],
      ['community/build/X.kt', 'community/build/X.kt'],
    ]))
  })

  it('writes the spans by doc folder in sorted order', () => {
    const root = temporaryTree('code-spans-', FILES)
    const paths = new Map<string, Map<string, string | null>>()
    addCodeSpanPaths(paths, '', 'build/docs/guides/g.md', BODY, root)
    addCodeSpanPaths(paths, '', 'build/readme.md', 'Nothing here.', root)
    assert.equal(
      codeSpanPathsJson(paths, new Map([['a', new Map([['z.md', null]])]])),
      '{"a":{"z.md":null},"build/docs/guides":{"META-INF/plugin.xml":null,"build/dev-build.json":"build/dev-build.json"}}',
    )
  })
})
