import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { collectPictureReferences, isExternalPicture, picturePath } from './docAssets.ts'

/** The content root of the Air site. */
const AIR = 'plugins/air'

describe('picture references', () => {
  it('resolves a relative picture with its title from the directory of the body', () => {
    const body = 'Intro.\n\n![The model](../plans/air-host-model/host-target.svg "Target: a session is state")\n'
    assert.deepStrictEqual(collectPictureReferences(AIR, body, 'docs/guides'), [{
      source: '../plans/air-host-model/host-target.svg',
      path: 'docs/plans/air-host-model/host-target.svg',
      title: 'Target: a session is state',
      line: 3,
    }])
  })

  it('reads a picture without a title, in angle brackets, or with a single-quoted title', () => {
    const body = '![a](a.svg)\n![b](<plans/b.svg>)\n![d](d.png \'D\')'
    assert.deepStrictEqual(collectPictureReferences(AIR, body, 'docs').map(reference => [reference.path, reference.title]), [
      ['docs/a.svg', null],
      ['docs/plans/b.svg', null],
      ['docs/d.png', 'D'],
    ])
  })

  it('skips an https or a data source', () => {
    const body = '![remote](https://example.com/a.svg)\n![inline](data:image/svg+xml,%3Csvg%3E)\n![host](//example.com/a.svg)'
    assert.deepStrictEqual(collectPictureReferences(AIR, body, 'docs'), [])
    assert.equal(isExternalPicture('HTTPS://example.com/a.png'), true)
    assert.equal(isExternalPicture('../a.png'), false)
  })

  it('skips a picture in a fenced block or in a code span', () => {
    const body = [
      'A prompt writes `![title](<agent-path>)`.',
      '```markdown',
      '![inside](inside.svg)',
      '```',
      '  ~~~',
      '  ![indented](indented.svg)',
      '  ~~~',
      '![after](after.svg)',
    ].join('\n')
    assert.deepStrictEqual(collectPictureReferences(AIR, body, 'docs').map(reference => [reference.source, reference.line]), [
      ['after.svg', 8],
    ])
  })

  it('gives no path for a source that leaves the content root', () => {
    assert.equal(picturePath(AIR, 'docs/guides', '../../../../community/a.svg'), null)
    assert.equal(picturePath(AIR, 'docs/guides', '/air/a.svg'), null)
    assert.equal(picturePath(AIR, 'docs', '%zz.svg'), null)
  })
})
