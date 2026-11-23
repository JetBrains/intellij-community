import assert from 'node:assert/strict'
import { symlinkSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { posixRelative, walkFiles } from './paths.ts'
import { temporaryTree } from './tempTree.ts'

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
