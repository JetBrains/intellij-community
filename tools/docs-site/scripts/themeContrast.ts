// The contrast check of the theme tokens. It reads the `:root` and `.dark` token blocks of `styles/tailwind.css` and
// of the generated `styles/islands.generated.css`, resolves the colors of the main token pairs, and reports each pair
// under its WCAG contrast minimum: 4.5:1 for text and 3:1 for a UI mark. A translucent color is composited over the
// color under it first. `themeContrast.test.ts` runs it in `pnpm run test:scripts`.
//
// No npm import: a gate can load this module on Bazel bun.

/** An sRGB color with channels from 0 to 1. */
export type Rgb = readonly [number, number, number]

/** An sRGB color and its alpha, all from 0 to 1. */
export interface Rgba {
  rgb: Rgb
  alpha: number
}

/** The custom properties of one token block, by name. */
export type TokenBlock = Readonly<Record<string, string>>

/** What a pair shows: text needs 4.5:1, a UI mark (a border, a focus ring, an edge) needs 3:1. */
export type PairKind = 'text' | 'mark'

export interface TokenPair {
  kind: PairKind
  foreground: string
  background: string
  /** The opaque token under a translucent background. The default is `--background`. */
  under?: string
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
  // the window under the islands: the top bar, the crumbs, and the gaps between the islands
  { kind: 'text', foreground: '--foreground', background: '--window' },
  { kind: 'text', foreground: '--muted-foreground', background: '--window' },
  { kind: 'text', foreground: '--primary', background: '--window' },
  { kind: 'mark', foreground: '--input', background: '--window' },
  // the translucent hover and secondary surfaces, on an island
  { kind: 'text', foreground: '--foreground', background: '--hover', under: '--card' },
  { kind: 'text', foreground: '--muted-foreground', background: '--hover', under: '--card' },
  { kind: 'text', foreground: '--secondary-foreground', background: '--secondary', under: '--card' },
  { kind: 'text', foreground: '--muted-foreground', background: '--popover' },
  ...LANE_TOKENS.map((lane): TokenPair => ({ kind: 'text', foreground: lane, background: '--background' })),
]

/**
 * The `:root` block, and the `.dark` block over it, of a token style sheet. Two blocks with one selector merge, the
 * later over the earlier, so the generated variables and the tokens can come from two files. Throws when a block is
 * missing.
 */
export function parseThemeBlocks(css: string): { light: TokenBlock, dark: TokenBlock } {
  const blocks = new Map<string, Record<string, string>>()
  for (const match of css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{};]+)\{([^{}]*)\}/g)) {
    const selector = match[1].trim()
    const properties: Record<string, string> = blocks.get(selector) ?? {}
    for (const declaration of match[2].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
      properties[declaration[1]] = declaration[2].trim()
    }
    blocks.set(selector, properties)
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
  return oklabToRgb(lightness, chroma * Math.cos(hue * Math.PI / 180), chroma * Math.sin(hue * Math.PI / 180))
}

function oklabToRgb(lightness: number, a: number, b: number): Rgb {
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3
  return [
    fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
  ]
}

function rgbToOklab([red, green, blue]: Rgb): [number, number, number] {
  const [r, g, b] = [toLinear(red), toLinear(green), toLinear(blue)]
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  return [
    0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
  ]
}

/** Splits `text` at the commas outside parentheses. */
function topLevelArguments(text: string): string[] {
  const parts: string[] = []
  let depth = 0
  let start = 0
  for (let index = 0; index < text.length; index++) {
    const char = text[index]
    if (char === '(') {
      depth++
    }
    else if (char === ')') {
      depth--
    }
    else if (char === ',' && depth === 0) {
      parts.push(text.slice(start, index).trim())
      start = index + 1
    }
  }
  parts.push(text.slice(start).trim())
  return parts
}

/**
 * `color-mix(in oklab | srgb, A p%, B q%)` with premultiplied alpha, as CSS Color 5 mixes. A missing percentage
 * takes the rest of 100%. `transparent` is a color with alpha 0.
 */
function resolveColorMix(text: string, block: TokenBlock, depth: number): Rgba | null {
  const match = /^color-mix\(\s*in\s+(oklab|srgb)\s*,(.*)\)$/s.exec(text)
  if (match === null) {
    return null
  }
  const parts = topLevelArguments(match[2])
  if (parts.length !== 2) {
    return null
  }
  const stops = parts.map((part) => {
    const percentage = /\s([\d.]+)%$/.exec(part)
    const color = resolveColorWithAlpha(percentage === null ? part : part.slice(0, percentage.index), block, depth + 1)
    return { color, weight: percentage === null ? null : Number(percentage[1]) / 100 }
  })
  if (stops[0].color === null || stops[1].color === null) {
    return null
  }
  const first = stops[0].weight ?? 1 - (stops[1].weight ?? 0.5)
  const second = stops[1].weight ?? 1 - first
  const total = first + second
  const [p, q] = [first / total, second / total]
  const [a, b] = [stops[0].color, stops[1].color]
  const alpha = a.alpha * p + b.alpha * q
  if (alpha === 0) {
    return { rgb: [0, 0, 0], alpha: 0 }
  }
  const space = match[1] === 'oklab' ? rgbToOklab : (rgb: Rgb) => [...rgb] as [number, number, number]
  const [x, y] = [space(a.rgb), space(b.rgb)]
  const mixed = x.map((channel, index) => (channel * a.alpha * p + y[index] * b.alpha * q) / alpha)
  const rgb = match[1] === 'oklab' ? oklabToRgb(mixed[0], mixed[1], mixed[2]) : mixed.map(channel => Math.min(1, Math.max(0, channel))) as unknown as Rgb
  return { rgb, alpha: Math.min(1, alpha * (total > 1 ? 1 : total)) }
}

/**
 * The color and the alpha of a token value: a hex color with or without alpha, an `oklch()` color, `transparent`, a
 * `color-mix()`, or a `var()` of another token of the block. Null for any other value.
 */
export function resolveColorWithAlpha(value: string | undefined, block: TokenBlock, depth = 0): Rgba | null {
  if (value === undefined || depth > 12) {
    return null
  }
  const text = value.trim()
  const reference = /^var\((--[\w-]+)\)$/.exec(text)
  if (reference !== null) {
    return resolveColorWithAlpha(block[reference[1]], block, depth + 1)
  }
  if (text === 'transparent') {
    return { rgb: [0, 0, 0], alpha: 0 }
  }
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.exec(text)
  if (hex !== null) {
    const digits = hex[1].length === 3 ? [...hex[1]].map(digit => digit + digit).join('') : hex[1]
    const channel = (start: number) => Number.parseInt(digits.slice(start, start + 2), 16) / 255
    return { rgb: [channel(0), channel(2), channel(4)], alpha: digits.length === 8 ? channel(6) : 1 }
  }
  const oklch = /^oklch\(([\d.]+)(%?)\s+([\d.]+)\s+([\d.]+)\)$/.exec(text)
  if (oklch !== null) {
    return { rgb: oklchToRgb(Number(oklch[1]) / (oklch[2] === '%' ? 100 : 1), Number(oklch[3]), Number(oklch[4])), alpha: 1 }
  }
  return resolveColorMix(text, block, depth)
}

/** The color of a token value, composited over `under` when it is translucent. Null when a color does not resolve. */
export function resolveColor(value: string | undefined, block: TokenBlock, under: Rgb | null = null): Rgb | null {
  const color = resolveColorWithAlpha(value, block)
  if (color === null) {
    return null
  }
  if (color.alpha >= 1) {
    return color.rgb
  }
  if (under === null) {
    return null
  }
  return color.rgb.map((channel, index) => channel * color.alpha + under[index] * (1 - color.alpha)) as unknown as Rgb
}

function luminance([red, green, blue]: Rgb): number {
  return 0.2126 * toLinear(red) + 0.7152 * toLinear(green) + 0.0722 * toLinear(blue)
}

/** The WCAG contrast ratio of two colors, from 1 to 21. */
export function contrastRatio(first: Rgb, second: Rgb): number {
  const [lighter, darker] = [luminance(first), luminance(second)].sort((x, y) => y - x)
  return (lighter + 0.05) / (darker + 0.05)
}

/** The colors of the stops of a `linear-gradient()` token, in order: hex, `oklch()`, and `var()` stops. */
export function gradientStops(value: string, block: TokenBlock): Rgb[] {
  return [...value.matchAll(/#[0-9a-f]{6}\b|#[0-9a-f]{3}\b|oklch\([^)]*\)|var\(--[\w-]+\)/gi)].map(match => resolveColor(match[0], block)).filter(color => color !== null)
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
      const background = resolveColor(block[pair.background], block, resolveColor(block[pair.under ?? '--background'], block))
      const foreground = resolveColor(block[pair.foreground], block, background)
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
