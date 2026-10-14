import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { codeSpanTarget } from './codeSpanLink.ts'

describe('codeSpanTarget', () => {
  it('keeps the span text as the label when the content root is the repository root', () => {
    const unresolved = { 'build/dev-build.json': 'build/dev-build.json', 'community/build/dev-build.json': 'community/build/dev-build.json' }
    assert.deepEqual(codeSpanTarget('build/dev-build.json', 'build/docs/guides', '', unresolved), {
      repoPath: 'build/dev-build.json',
      contentPath: 'build/dev-build.json',
      fragment: undefined,
      label: 'build/dev-build.json',
    })
    assert.equal(codeSpanTarget('community/build/dev-build.json#L3', 'build/docs/guides', '', unresolved)?.label, 'community/build/dev-build.json#L3')
  })

  it('keeps the span text as the label under a plugin content root, also for a repository path', () => {
    const unresolved = { 'backend/acp/TODO.md': 'plugins/air/backend/acp/TODO.md', 'community/build/X.kt': 'community/build/X.kt' }
    assert.deepEqual(codeSpanTarget('backend/acp/TODO.md', 'docs', 'plugins/air', unresolved), {
      repoPath: 'plugins/air/backend/acp/TODO.md',
      contentPath: 'backend/acp/TODO.md',
      fragment: undefined,
      label: 'backend/acp/TODO.md',
    })
    assert.deepEqual(codeSpanTarget('community/build/X.kt', 'docs', 'plugins/air', unresolved), {
      repoPath: 'community/build/X.kt',
      contentPath: null,
      fragment: undefined,
      label: 'community/build/X.kt',
    })
  })

  it('gives no target for a span that names no file', () => {
    assert.equal(codeSpanTarget('META-INF/plugin.xml', 'build/docs/guides', '', { 'META-INF/plugin.xml': null }), null)
  })

  it('resolves a span with no entry against the folder of the doc and labels it with the resolved path', () => {
    assert.deepEqual(codeSpanTarget('src/pages/HomePage.tsx', 'docs', 'plugins/air', {}), {
      repoPath: 'plugins/air/docs/src/pages/HomePage.tsx',
      contentPath: 'docs/src/pages/HomePage.tsx',
      fragment: undefined,
      label: 'plugins/air/docs/src/pages/HomePage.tsx',
    })
    assert.equal(codeSpanTarget('../spec/x.spec.md#goals', 'build/docs', '', undefined)?.label, 'build/spec/x.spec.md#goals')
  })
})
