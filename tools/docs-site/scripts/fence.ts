// The fenced-block helpers of the doc checks. The diagram grammar and the `air-view` grammar of the Air site
// read a fence body as `key: value` lines. The decision check and the prose check blank the fenced blocks of a
// Markdown file. This file imports nothing, so the browser and bun without `node_modules` can load it.

/**
 * The `key: value` lines of a fence body, in order. A `#` starts a comment, and a blank line is
 * skipped. A malformed line adds an error to `errors` when the iteration reaches it, so the errors
 * stay in line order with the errors of the caller.
 */
export function* fenceEntries(text: string, errors: string[]): Generator<{ key: string, value: string }> {
  for (const rawLine of text.split('\n')) {
    const line = rawLine.replace(/#.*$/, '').trim()
    if (line === '') {
      continue
    }
    const keyValue = /^(\w+):\s*(.+)$/.exec(line)
    if (!keyValue) {
      errors.push(`malformed line '${rawLine.trim()}' (expected 'key: value')`)
      continue
    }
    yield { key: keyValue[1], value: keyValue[2] }
  }
}

/** The source with every fenced block blanked line by line, so a line number of the rest stays true. */
export function withoutFencedBlocks(source: string): string {
  const lines = source.split('\n')
  let fence: string | null = null
  for (let index = 0; index < lines.length; index++) {
    const opening = /^\s*(`{3,}|~{3,})/.exec(lines[index])
    if (fence === null) {
      if (opening !== null) {
        fence = opening[1]
        lines[index] = ''
      }
    }
    else {
      const closes = opening !== null && opening[1][0] === fence[0] && opening[1].length >= fence.length
      lines[index] = ''
      if (closes) {
        fence = null
      }
    }
  }
  return lines.join('\n')
}
