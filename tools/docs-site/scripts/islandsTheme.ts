// The generator of the Islands theme variables. It reads the Islands Light and the Islands Dark theme of the IDE and
// writes `styles/islands.generated.css`: the palette, the semantic colors of each theme, and the radii of the arcs,
// as `--islands-*` custom properties. The kernel tokens, the fallbacks of the GenUI catalog, and Branch Deck map these
// variables to their own tokens. Each consumer keeps its mapping; this file has no token of a consumer.
//
// `node scripts/islandsTheme.ts` writes the file. `islandsTheme.test.ts` fails when the file is not current.
//
// No npm import: node and bun run this module as it is.

import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { isMainModule } from './paths.ts'

const kernelDir = join(dirname(fileURLToPath(import.meta.url)), '..')

/** The directory of the Islands themes. The path is relative to the kernel, so it holds in intellij-community too. */
export const ISLANDS_THEME_DIR = join(kernelDir, '../../platform/platform-resources/src/themes/islands')

/** The generated style sheet. */
export const ISLANDS_CSS_PATH = join(kernelDir, 'styles/islands.generated.css')

/** The part of a `*.theme.json` file that the generator reads. */
export interface IslandsThemeJson {
  name: string
  dark: boolean
  colors: Readonly<Record<string, string>>
  ui: Readonly<Record<string, unknown>>
}

/** A palette step: a hue and a number, for example `gray-160`. */
const PALETTE_NAME = /^[a-z]+-\d+$/

/**
 * The families of the semantic colors that the file holds. The other families (the gradients, the Got It tooltip, the
 * toggles, the tabs) serve one IDE component each.
 */
const SEMANTIC_FAMILIES = ['text-', 'layer-', 'accent-', 'main-window-', 'tool-window-', 'popup-', 'dialog-', 'editor-', 'control-', 'selection-', 'feedback-', 'core-', 'icon-']

/** A variant for one platform or one alternative window style. The page has neither. */
const SKIPPED_SUFFIXES = ['-alt', '-windows']

/** The arcs of the theme. An arc is a diameter, so the radius is half of it. */
const ARCS = [
  { key: 'Island.arc', name: 'island' },
  { key: 'Button.arc', name: 'button' },
  { key: 'Component.arc', name: 'component' },
] as const

/** The names of the semantic colors that the file holds, in the order of the theme file. */
export function semanticNames(theme: IslandsThemeJson): string[] {
  return Object.keys(theme.colors).filter(name => !PALETTE_NAME.test(name)
    && SEMANTIC_FAMILIES.some(family => name.startsWith(family))
    && !SKIPPED_SUFFIXES.some(suffix => name.endsWith(suffix)))
}

/** A color of the theme as a lowercase hex color, and the names that lead to it. Throws for an unknown name or a loop. */
export function resolveThemeColor(theme: IslandsThemeJson, name: string): { hex: string, chain: string[] } {
  const chain: string[] = []
  let value = name
  while (!value.startsWith('#')) {
    if (chain.includes(value)) {
      throw new Error(`islandsTheme: ${theme.name} has a loop at ${[...chain, value].join(' → ')}`)
    }
    chain.push(value)
    const next = theme.colors[value]
    if (next === undefined) {
      throw new Error(`islandsTheme: ${theme.name} has no color ${value}${chain.length > 1 ? ` (from ${chain[0]})` : ''}`)
    }
    value = next
  }
  if (!/^#([0-9a-f]{6}|[0-9a-f]{8})$/i.test(value)) {
    throw new Error(`islandsTheme: ${theme.name} gives ${name} the value ${value}, which is not a hex color`)
  }
  return { hex: value.toLowerCase(), chain: chain.slice(1) }
}

function arcOf(theme: IslandsThemeJson, key: string): number {
  const [group, property] = key.split('.')
  const value = (theme.ui[group] as Record<string, unknown> | undefined)?.[property] ?? theme.ui[key]
  if (typeof value !== 'number') {
    throw new Error(`islandsTheme: ${theme.name} has no number at ui.${key}`)
  }
  return value
}

/** The generated style sheet of the two themes. Throws when the themes differ in their palette, their names, or their arcs. */
export function islandsThemeCss(light: IslandsThemeJson, dark: IslandsThemeJson): string {
  if (light.dark || !dark.dark) {
    throw new Error(`islandsTheme: expected a light and a dark theme, got ${light.name} and ${dark.name}`)
  }
  const palette = Object.keys(light.colors).filter(name => PALETTE_NAME.test(name))
  for (const name of new Set([...palette, ...Object.keys(dark.colors).filter(name => PALETTE_NAME.test(name))])) {
    if (light.colors[name]?.toLowerCase() !== dark.colors[name]?.toLowerCase()) {
      throw new Error(`islandsTheme: the palette step ${name} is ${light.colors[name]} in ${light.name} and ${dark.colors[name]} in ${dark.name}`)
    }
  }
  const names = semanticNames(light)
  const darkNames = semanticNames(dark)
  const unmatched = [...names.filter(name => !darkNames.includes(name)), ...darkNames.filter(name => !names.includes(name))]
  if (unmatched.length > 0) {
    throw new Error(`islandsTheme: only one theme has the colors ${unmatched.join(', ')}`)
  }

  const lines = [
    '/* Generated by scripts/islandsTheme.ts from the Islands themes of the IDE',
    '   (platform/platform-resources/src/themes/islands/ManyIslandsLight.theme.json and ManyIslandsDark.theme.json).',
    '   Do not edit. After a change of a theme, run `node scripts/islandsTheme.ts` in community/tools/docs-site.',
    '',
    '   A semantic color has a -light and a -dark variable; its comment names the colors that lead to the value. A',
    '   consumer maps the -light variables in its light block and the -dark variables in its dark block. */',
    '',
    ':root {',
    '  /* the palette: the same steps in both themes */',
    ...palette.map(name => `  --islands-${name}: ${light.colors[name].toLowerCase()};`),
    '',
    '  /* the radii: half of each arc */',
  ]
  for (const arc of ARCS) {
    const radius = arcOf(light, arc.key)
    if (radius !== arcOf(dark, arc.key)) {
      throw new Error(`islandsTheme: ${arc.key} differs between ${light.name} and ${dark.name}`)
    }
    lines.push(`  --islands-${arc.name}-radius: ${radius / 2}px; /* ${arc.key} ${radius} */`)
  }
  for (const [theme, suffix] of [[light, 'light'], [dark, 'dark']] as const) {
    lines.push('', `  /* the semantic colors of ${theme.name} */`)
    for (const name of names) {
      const { hex, chain } = resolveThemeColor(theme, name)
      lines.push(`  --islands-${name}-${suffix}: ${hex};${chain.length > 0 ? ` /* ${chain.join(', ')} */` : ''}`)
    }
  }
  lines.push('}', '')
  return lines.join('\n')
}

/** The two Islands themes of `dir`. */
export function readIslandsThemes(dir = ISLANDS_THEME_DIR): { light: IslandsThemeJson, dark: IslandsThemeJson } {
  const read = (file: string) => JSON.parse(readFileSync(join(dir, file), 'utf8')) as IslandsThemeJson
  return { light: read('ManyIslandsLight.theme.json'), dark: read('ManyIslandsDark.theme.json') }
}

/** The style sheet that the current themes give. */
export function currentIslandsCss(): string {
  const { light, dark } = readIslandsThemes()
  return islandsThemeCss(light, dark)
}

if (isMainModule(import.meta.url)) {
  writeFileSync(ISLANDS_CSS_PATH, currentIslandsCss())
  console.log(`islandsTheme: wrote ${ISLANDS_CSS_PATH}`)
}
