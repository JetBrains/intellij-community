import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { describe, it } from 'node:test'
import { currentIslandsCss, ISLANDS_CSS_PATH, islandsThemeCss, resolveThemeColor, semanticNames, type IslandsThemeJson } from './islandsTheme.ts'

function theme(name: string, dark: boolean, colors: Record<string, string>, ui: Record<string, unknown> = { 'Island.arc': 20, 'Button': { arc: 8 }, 'Component.arc': 8 }): IslandsThemeJson {
  return { name, dark, colors, ui }
}

describe('the generated Islands file', () => {
  it('is current; run `node scripts/islandsTheme.ts` after a change of a theme', () => {
    assert.equal(readFileSync(ISLANDS_CSS_PATH, 'utf8'), currentIslandsCss())
  })
})

describe('the Islands generator', () => {
  const light = theme('Light', false, { 'gray-10': '#191A1C', 'white': '#FFFFFF', 'layer-0-bg': 'white', 'tool-window-bg': 'layer-0-bg', 'grad-a1': '#FFF', 'main-window-bg-alt': '#EEE' })
  const dark = theme('Dark', true, { 'gray-10': '#191A1C', 'white': '#FFFFFF', 'layer-0-bg': 'gray-10', 'tool-window-bg': 'layer-0-bg', 'grad-a1': '#000', 'main-window-bg-alt': '#111' })

  it('resolves a name through the names that lead to it', () => {
    assert.deepEqual(resolveThemeColor(light, 'tool-window-bg'), { hex: '#ffffff', chain: ['layer-0-bg', 'white'] })
  })

  it('keeps the semantic families and leaves out the gradients and the alternatives', () => {
    assert.deepEqual(semanticNames(light), ['layer-0-bg', 'tool-window-bg'])
  })

  it('writes the palette once, the radii, and each semantic color for each theme', () => {
    const css = islandsThemeCss(light, dark)
    assert.ok(css.includes('  --islands-gray-10: #191a1c;'))
    assert.ok(css.includes('  --islands-island-radius: 10px; /* Island.arc 20 */'))
    assert.ok(css.includes('  --islands-button-radius: 4px; /* Button.arc 8 */'))
    assert.ok(css.includes('  --islands-tool-window-bg-light: #ffffff; /* layer-0-bg, white */'))
    assert.ok(css.includes('  --islands-tool-window-bg-dark: #191a1c; /* layer-0-bg, gray-10 */'))
  })

  it('fails on an unknown name, a loop, a palette that differs, and a name only one theme has', () => {
    assert.throws(() => resolveThemeColor(theme('T', false, { 'text-a': 'missing' }), 'text-a'), /has no color missing \(from text-a\)/)
    assert.throws(() => resolveThemeColor(theme('T', false, { 'text-a': 'text-b', 'text-b': 'text-a' }), 'text-a'), /loop/)
    assert.throws(() => islandsThemeCss(light, theme('Dark', true, { ...dark.colors, 'gray-10': '#000000' })), /palette step gray-10/)
    assert.throws(() => islandsThemeCss(light, theme('Dark', true, { ...dark.colors, 'text-x': 'white' })), /only one theme has the colors text-x/)
  })
})
