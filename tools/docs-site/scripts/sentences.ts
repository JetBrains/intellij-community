// The sentences and the words of a block of prose. The prose check (proseLint.ts) and the spec check (specRefs.ts)
// count with it, so a sentence of an explainer and a sentence of a spec take the same budget the same way.
//
// A word is a whitespace-separated token with a letter or a digit. A code span is one word, and a link target is no
// word. A sentence ends at `.`, `!` or `?` with its closing marks, before whitespace or the end of the block. The
// abbreviations `e.g.`, `i.e.` and `vs.` do not end a sentence. A block end ends a sentence.
//
// The gate on bun loads this file, so it imports nothing from npm.

/** A run of text with the 1-based source line of each character. */
export interface LinedText {
  text: string
  lines: number[]
}

/** One sentence of a block, with the line of its first character. */
export interface Sentence {
  text: string
  line: number
}

const CODE_SPAN = /(`+)([\s\S]*?[^`])\1(?!`)/g
const LINK_TARGET = /]\([^)]*\)/g
const WORD = /[\p{L}\p{N}]/u
const ABBREVIATIONS: ReadonlySet<string> = new Set(['e.g.', 'i.e.', 'vs.'])

/**
 * Rewrites the matches of `pattern` in `source`, and keeps the line of each character. A replacement takes the line
 * of the first character of its match.
 */
function replaceLined(source: LinedText, pattern: RegExp, replace: (match: RegExpExecArray) => string): LinedText {
  let text = ''
  const lines: number[] = []
  let from = 0
  for (const match of source.text.matchAll(pattern)) {
    const index = match.index
    text += source.text.slice(from, index)
    lines.push(...source.lines.slice(from, index))
    const replacement = replace(match as RegExpExecArray)
    text += replacement
    lines.push(...Array.from(replacement, () => source.lines[index]))
    from = index + match[0].length
  }
  text += source.text.slice(from)
  lines.push(...source.lines.slice(from))
  return { text, lines }
}

/** The text with each code span as one word and each link target removed, so a word count and a match read only prose. */
export function normalize(source: LinedText): LinedText {
  const compact = replaceLined(source, CODE_SPAN, (match) => {
    const body = match[2].replace(/\s+/g, '')
    return `\`${WORD.test(body) ? body : 'code'}\``
  })
  return replaceLined(compact, LINK_TARGET, () => ']')
}

/** The words of a text: the whitespace-separated tokens with a letter or a digit. */
export function countWords(text: string): number {
  return text.split(/\s+/).filter(token => WORD.test(token)).length
}

/** The sentences of a block, each with the line of its first character. A block end ends a sentence. */
export function splitSentences(block: LinedText): Sentence[] {
  const sentences: Sentence[] = []
  let start = 0
  const push = (end: number): void => {
    const raw = block.text.slice(start, end)
    const offset = raw.length - raw.trimStart().length
    const text = raw.trim()
    if (text.length > 0) {
      sentences.push({ text, line: block.lines[start + offset] })
    }
    start = end
  }
  // a closing bracket, quote or emphasis mark stays with its sentence: `**A lead-in.** The text.` is two sentences
  const end = /[.!?][)"'\]”’*_]*(?=\s|$)/g
  for (const match of block.text.matchAll(end)) {
    const stop = match.index + match[0].length
    const word = /[\p{L}.]*$/u.exec(block.text.slice(start, stop))?.[0] ?? ''
    if (ABBREVIATIONS.has(word.toLowerCase())) {
      continue
    }
    push(stop)
  }
  push(block.text.length)
  return sentences
}
