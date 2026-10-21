import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { openingQuestion, splitExplainerChapters } from './explainerDoc.ts'

describe('openingQuestion', () => {
  it('takes the first sentence of the first paragraph when it is a question', () => {
    assert.equal(openingQuestion('Where does a launch target come from? The generator gives\neach configuration a target.'), 'Where does a launch target come from?')
  })

  it('joins a question that wraps over lines', () => {
    assert.equal(openingQuestion('How do the declared sources become\nBazel declarations? The two generators.'), 'How do the declared sources become Bazel declarations?')
  })

  it('returns null when a statement comes first', () => {
    assert.equal(openingQuestion('The launcher starts the JVM. Why does it?'), null)
    assert.equal(openingQuestion('Hello! What now?'), null)
  })

  it('returns null when the first paragraph has no question', () => {
    assert.equal(openingQuestion('The launcher starts the JVM.\n\nWhy does it?'), null)
    assert.equal(openingQuestion(''), null)
  })

  it('keeps the code text with its underscores and dots', () => {
    assert.equal(openingQuestion('Who reads `IJ_JAVA_OPTIONS` and `product-info.json`? The launcher.'), 'Who reads IJ_JAVA_OPTIONS and product-info.json?')
  })

  it('ignores a question mark inside code', () => {
    assert.equal(openingQuestion('Why is `a?b` here? Because.'), 'Why is a?b here?')
  })

  it('returns null when "e.g." comes before the question mark', () => {
    assert.equal(openingQuestion('Which tool, e.g. the converter, runs first? The converter.'), null)
  })

  it('drops the link target and the emphasis marks', () => {
    assert.equal(openingQuestion('What does [the composer](../guides/a.md) do with a **component**? It places it.'), 'What does the composer do with a component?')
  })

  it('returns null for a list or a heading', () => {
    assert.equal(openingQuestion('- What is this?'), null)
    assert.equal(openingQuestion('### Why?'), null)
  })
})

describe('splitExplainerChapters', () => {
  it('gives each chapter its opening question', () => {
    const { chapters } = splitExplainerChapters('# E\n\n## Start\n\nWhat starts? The launcher.\n\n## End\n\nThe end.\n', 'e.md')
    assert.deepEqual(chapters.map(chapter => chapter.question), ['What starts?', null])
  })
})
