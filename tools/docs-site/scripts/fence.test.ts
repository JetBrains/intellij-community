import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { fenceEntries, withoutFencedBlocks } from './fence.ts'

describe('fence', () => {
  it('read the key: value lines of a fence body in order', () => {
    const errors: string[] = []
    const entries = [...fenceEntries('a: 1\n# a comment\n\nb: two words # a note\nbroken\n', errors)]
    assert.deepStrictEqual(entries, [{ key: 'a', value: '1' }, { key: 'b', value: 'two words' }])
    assert.deepStrictEqual(errors, ["malformed line 'broken' (expected 'key: value')"])
  })

  it('blank a fenced block line by line', () => {
    assert.equal(withoutFencedBlocks('a\n```md\nb\n```\nc\n~~~\nd\n~~~\ne'), 'a\n\n\n\nc\n\n\n\ne')
    assert.equal(withoutFencedBlocks('````\n```\nstill inside\n````\nout'), '\n\n\n\nout')
  })
})
