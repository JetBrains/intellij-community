// The contrast check of the theme tokens. It reads the `:root` and `.dark` token blocks of `styles/tailwind.css`,
// resolves the colors of the main token pairs, and reports each pair under its WCAG contrast minimum: 4.5:1 for text
// and 3:1 for a UI mark. `themeContrast.test.ts` runs it in `pnpm run test:scripts`.
//
// No npm import: a gate can load this module on Bazel bun.

/** An sRGB color with channels from 0 to 1. */
export type Rgb = readonly [number, number, number]

/** The custom properties of one token block, by name. */
export type TokenBlock = Readonly<Record<string, string>>

/** What a pair shows: text needs 4.5:1, a UI mark (a border, a focus ring, an edge) needs 3:1. */
export type PairKind = 'text' | 'mark'

export interface TokenPair {
  kind: PairKind
  foreground: string
  background: string
}

export const MIN_RATIO: Readonly<Record<PairKind, number>> = { text: 4.5, mark: 3 }

/** The lane colors. A lane names a node and colors a chip label, so each lane is checked as text. */
export const LANE_TOKENS = ['indigo', 'sky', 'blue', 'green', 'amber', 'red', 'neutral'].map(lane => `--layer-${lane}`)

/** The main token pairs of the theme, checked in the light and in the dark block. */
export const THEME_PAIRS: readonly TokenPair[] = [
  { kind: 'text', foreground: '--foreground', background: '--background' },
  { kind: 'text', foreground: '--foreground', background: '--card' },
  { kind: 'text', foreground: '--muted-foreground', background: '--background' },
  { kind: 'text', foreground: '--muted-foreground', background: '--muted' },
  { kind: 'text', foreground: '--muted-foreground', background: '--card' },
  { kind: 'text', foreground: '--primary', background: '--background' },
  { kind: 'text', foreground: '--primary-foreground', background: '--primary' },
  { kind: 'text', foreground: '--destructive', background: '--background' },
  { kind: 'text', foreground: '--warning', background: '--background' },
  { kind: 'text', foreground: '--success', background: '--background' },
  { kind: 'text', foreground: '--tag-durable', background: '--background' },
  { kind: 'text', foreground: '--tag-structured', background: '--background' },
  { kind: 'text', foreground: '--info', background: '--info-surface' },
  { kind: 'text', foreground: '--success', background: '--success-surface' },
  { kind: 'text', foreground: '--warning', background: '--warning-surface' },
  { kind: 'text', foreground: '--destructive', background: '--destructive-surface' },
  { kind: 'mark', foreground: '--ring', background: '--background' },
  { kind: 'mark', foreground: '--edge-stroke', background: '--canvas' },
  { kind: 'mark', foreground: '--input', background: '--background' },
  ...LANE_TOKENS.map((lane): TokenPair => ({ kind: 'text', foreground: lane, background: '--background' })),
]

/** The `:root` block, and the `.dark` block over it, of a token style sheet. Throws when a block is missing. */
export function parseThemeBlocks(css: string): { light: TokenBlock, dark: TokenBlock } {
  const blocks = new Map<string, Record<string, string>>()
  for (const match of css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{};]+)\{([^{}]*)\}/g)) {
    const properties: Record<string, string> = {}
    for (const declaration of match[2].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
      properties[declaration[1]] = declaration[2].trim()
    }
    blocks.set(match[1].trim(), properties)
  }
  const light = blocks.get(':root')
  const dark = blocks.get('.dark')
  if (light === undefined || dark === undefined) {
    throw new Error('themeContrast: the style sheet has no :root or no .dark token block')
  }
  return { light, dark: { ...light, ...dark } }
}

function toLinear(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
}

function fromLinear(channel: number): number {
  const value = channel <= 0.0031308 ? 12.92 * channel : 1.055 * channel ** (1 / 2.4) - 0.055
  return Math.min(1, Math.max(0, value))
}

function oklchToRgb(lightness: number, chroma: number, hue: number): Rgb {
  const a = chroma * Math.cos(hue * Math.PI / 180)
  const b = chroma * Math.sin(hue * Math.PI / 180)
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3
  return [
    fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
  ]
}

/**
 * The color of a token value: a hex color, an `oklch()` color, or a `var()` of another token of the block. Null
 * for any other value, for example a `color-mix()`.
 */
export function resolveColor(value: string | undefined, block: TokenBlock, depth = 0): Rgb | null {
  if (value === undefined || depth > 8) {
    return null
  }
  const text = value.trim()
  const reference = /^var\((--[\w-]+)\)$/.exec(text)
  if (reference !== null) {
    return resolveColor(block[reference[1]], block, depth + 1)
  }
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(text)
  if (hex !== null) {
    const digits = hex[1].length === 3 ? [...hex[1]].map(digit => digit + digit).join('') : hex[1]
    return [0, 2, 4].map(start => Number.parseInt(digits.slice(start, start + 2), 16) / 255) as unknown as Rgb
  }
  const oklch = /^oklch\(([\d.]+)(%?)\s+([\d.]+)\s+([\d.]+)\)$/.exec(text)
  if (oklch !== null) {
    return oklchToRgb(Number(oklch[1]) / (oklch[2] === '%' ? 100 : 1), Number(oklch[3]), Number(oklch[4]))
  }
  return null
}

function luminance([red, green, blue]: Rgb): number {
  return 0.2126 * toLinear(red) + 0.7152 * toLinear(green) + 0.0722 * toLinear(blue)
}

/** The WCAG contrast ratio of two colors, from 1 to 21. */
export function contrastRatio(first: Rgb, second: Rgb): number {
  const [lighter, darker] = [luminance(first), luminance(second)].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

/** The colors of the stops of a `linear-gradient()` token, in order. */
export function gradientStops(value: string, block: TokenBlock): Rgb[] {
  return [...value.matchAll(/#[0-9a-f]{6}\b|#[0-9a-f]{3}\b|oklch\([^)]*\)/gi)].map(match => resolveColor(match[0], block)).filter(color => color !== null)
}

/** The result of one pair in one block. */
export interface PairResult extends TokenPair {
  scheme: 'light' | 'dark'
  ratio: number | null
}

const WHITE: Rgb = [1, 1, 1]

/**
 * The contrast of each theme pair in both blocks, and of white text on each stop of `--brand-gradient`. A pair
 * whose color does not resolve has the ratio null.
 */
export function themeContrast(css: string): PairResult[] {
  const blocks = parseThemeBlocks(css)
  const results: PairResult[] = []
  for (const scheme of ['light', 'dark'] as const) {
    const block = blocks[scheme]
    for (const pair of THEME_PAIRS) {
      const foreground = resolveColor(block[pair.foreground], block)
      const background = resolveColor(block[pair.background], block)
      results.push({ ...pair, scheme, ratio: foreground === null || background === null ? null : contrastRatio(foreground, background) })
    }
    const stops = gradientStops(block['--brand-gradient'] ?? '', block)
    results.push({
      kind: 'text',
      foreground: 'white',
      background: '--brand-gradient',
      scheme,
      ratio: stops.length === 0 ? null : Math.min(...stops.map(stop => contrastRatio(WHITE, stop))),
    })
  }
  return results
}

/** One line for each pair that does not resolve or is under its minimum. */
export function themeContrastFindings(css: string): string[] {
  return themeContrast(css)
    .filter(result => result.ratio === null || result.ratio < MIN_RATIO[result.kind])
    .map(result => result.ratio === null
      ? `${result.scheme}: ${result.foreground} on ${result.background} does not resolve to a color`
      : `${result.scheme}: ${result.foreground} on ${result.background} is ${result.ratio.toFixed(2)}:1, under ${MIN_RATIO[result.kind]}:1 for ${result.kind === 'text' ? 'text' : 'a UI mark'}`)
}
