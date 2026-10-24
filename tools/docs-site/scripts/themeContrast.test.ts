import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { describe, it } from 'node:test'
import { fileURLToPath } from 'node:url'
import { contrastRatio, gradientStops, parseThemeBlocks, resolveColor, THEME_PAIRS, themeContrast, themeContrastFindings } from './themeContrast.ts'

const tokens = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '../styles/tailwind.css'), 'utf8')

describe('the theme tokens', () => {
  it('keep text at 4.5:1 and UI marks at 3:1 in the light and the dark block', () => {
    assert.deepEqual(themeContrastFindings(tokens), [])
  })

  it('resolve every pair in both blocks', () => {
    const results = themeContrast(tokens)
    assert.equal(results.length, 2 * (THEME_PAIRS.length + 1))
    assert.ok(results.every(result => result.ratio !== null))
  })
})

describe('the contrast check', () => {
  const css = `
    /* a comment { --foreground: #fff; } */
    :root { --background: #ffffff; --foreground: #767676; --muted: oklch(98.5% 0 0); --alias: var(--foreground); }
    .dark { --background: #000; }
  `

  it('reads the dark block over the light block', () => {
    const { light, dark } = parseThemeBlocks(css)
    assert.equal(light['--background'], '#ffffff')
    assert.equal(dark['--background'], '#000')
    assert.equal(dark['--foreground'], '#767676')
  })

  it('resolves a hex color, an oklch color, and a var() reference', () => {
    const { light } = parseThemeBlocks(css)
    assert.deepEqual(resolveColor(light['--alias'], light), resolveColor('#767676', light))
    const muted = resolveColor(light['--muted'], light)!
    assert.ok(muted.every(channel => Math.abs(channel - 0.98) < 0.01))
    assert.equal(resolveColor('color-mix(in srgb, red 50%, blue)', light), null)
  })

  it('computes the WCAG ratio', () => {
    assert.equal(contrastRatio([0, 0, 0], [1, 1, 1]), 21)
    const { light } = parseThemeBlocks(css)
    // #767676 on white is the darkest gray under 4.5:1 that rounds to 4.54
    assert.equal(contrastRatio(resolveColor('#767676', light)!, [1, 1, 1]).toFixed(2), '4.54')
  })

  it('reads the stops of a gradient', () => {
    assert.equal(gradientStops('linear-gradient(135deg, #3369d6 /* Blue3 */, oklch(50% 0.2 300))', {}).length, 2)
  })

  it('names a pair under its minimum and a pair that does not resolve', () => {
    const findings = themeContrastFindings(':root { --background: #ffffff; --foreground: #999999; } .dark { }')
    assert.ok(findings.includes('light: --foreground on --background is 2.85:1, under 4.5:1 for text'))
    assert.ok(findings.includes('dark: --ring on --background does not resolve to a color'))
  })
})
