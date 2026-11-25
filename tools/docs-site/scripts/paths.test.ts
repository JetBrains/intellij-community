import assert from 'node:assert/strict'
import { chmodSync, symlinkSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { openspecArchiveDesigns, posixRelative, repoRoot, walkFiles } from './paths.ts'
import { temporaryTree } from './tempTree.ts'
import { ARCHIVE_FILES, testSiteConfig, withOpenSpecRoot } from './testSiteConfig.ts'

test('a walk gives the files depth first in sorted order, skips an entry, and enters a link to a folder', () => {
  const elsewhere = temporaryTree('walk-files-out-', { 'z.txt': '' })
  const root = temporaryTree('walk-files-', { 'b/2.txt': '', 'b/1.txt': '', 'a.txt': '', 'skip/x.txt': '', 'B.txt': '' })
  symlinkSync(elsewhere, join(root, 'c'))
  symlinkSync(join(root, 'a.txt'), join(root, 'd.txt'))
  const files: string[] = []
  walkFiles(root, entry => entry === 'skip', file => files.push(posixRelative(root, file)))
  assert.deepEqual(files, ['B.txt', 'a.txt', 'b/1.txt', 'b/2.txt', 'c/z.txt', 'd.txt'])
})

test('a walk of a folder that does not exist gives no file', () => {
  const root = temporaryTree('walk-files-', {})
  walkFiles(join(root, 'missing'), () => false, () => assert.fail('the walk gave a file'))
})

test('the designs of the archive are the design files of its folders, with the fields of .openspec.yaml', () => {
  const root = temporaryTree('archive-designs-', ARCHIVE_FILES)
  const designs = openspecArchiveDesigns(withOpenSpecRoot(testSiteConfig()), root)
  assert.deepEqual(designs.map(({ file, id, date, slug, topic, adr }) => ({ path: posixRelative(root, file), id, date, slug, topic, adr })), [
    { path: 'openspec/changes/archive/2026-11-20-one-channel/design.md', id: '2026-11-20-one-channel', date: '2026-11-20', slug: 'one-channel', topic: 'docs-site', adr: ['0001'] },
    { path: 'openspec/changes/archive/2026-11-21-a-view-frame/design.md', id: '2026-11-21-a-view-frame', date: '2026-11-21', slug: 'a-view-frame', topic: null, adr: [] },
    { path: 'openspec/changes/archive/2026-11-22-no-heading/design.md', id: '2026-11-22-no-heading', date: '2026-11-22', slug: 'no-heading', topic: null, adr: [] },
  ])
  assert.deepEqual(openspecArchiveDesigns(testSiteConfig(), root), [])
})

test('a walk with checkout-files gives the listed files in the order of a walk, and a folder outside the repository is walked', { skip: process.platform === 'win32' }, () => {
  const root = 'community/tools/docs-site/scripts/walk-listing-fixture'
  const listed = ['b.txt', 'a-b/x.txt', 'a/y.txt', 'a/skip/z.txt'].map(path => `'${root}/${path}'`).join(' ')
  const tool = join(temporaryTree('checkout-files-', {}), 'checkout-files')
  writeFileSync(tool, `#!/bin/sh\nprintf '%s\\0' ${listed}\n`)
  chmodSync(tool, 0o755)
  const outside = temporaryTree('walk-outside-', { 'c.txt': '' })
  const previous = process.env.DOCS_CHECKOUT_FILES
  process.env.DOCS_CHECKOUT_FILES = tool
  try {
    const dir = join(repoRoot, root)
    const walk = (from: string, skips: (entry: string) => boolean, base: string): string[] => {
      const files: string[] = []
      walkFiles(from, skips, file => files.push(posixRelative(base, file)))
      return files
    }
    assert.deepEqual(walk(dir, entry => entry === 'skip', dir), ['a/y.txt', 'a-b/x.txt', 'b.txt'])
    assert.deepEqual(walk(join(dir, 'a'), () => false, dir), ['a/skip/z.txt', 'a/y.txt'])
    assert.deepEqual(walk(outside, () => false, outside), ['c.txt'])
  }
  finally {
    if (previous === undefined) {
      delete process.env.DOCS_CHECKOUT_FILES
    }
    else {
      process.env.DOCS_CHECKOUT_FILES = previous
    }
  }
})
