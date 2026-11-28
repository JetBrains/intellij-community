// The proposal rule of the spec check (specRefs.ts). A spec says what the code does now. A section that only proposes a
// behavior ends its heading with ` (Proposed)`, and two rules keep the proposal from reading as present behavior:
//   - A proposed section holds no `[@test]` line, because a proposal has no test yet. The section ends at the next
//     heading of its level or a higher one.
//   - A spec that names the section in quotes names it with the mark, so the pointer says that it is a proposal.
//
// The gate of a site loads this file on bun without node_modules, so it imports nothing from npm.

/** The end of a heading that marks a spec section as a proposal. The code of the section is not built yet. */
export const PROPOSED_MARK = ' (Proposed)'

const HEADING = /^(#{1,6})\s+(.+?)\s*$/

interface Heading {
  /** The 1-based line. */
  line: number
  level: number
  text: string
}

function lines(text: string): string[] {
  return text.split(/\r\n|\n|\r/)
}

/** The headings of a markdown text. A line in a fenced block is no heading. */
function headings(all: readonly string[]): Heading[] {
  const result: Heading[] = []
  let inFence = false
  for (const [index, line] of all.entries()) {
    if (line.trim().startsWith('```')) {
      inFence = !inFence
    }
    if (inFence) {
      continue
    }
    const match = HEADING.exec(line)
    if (match !== null) {
      result.push({ line: index + 1, level: match[1].length, text: match[2] })
    }
  }
  return result
}

/** One spec of the proposal rule: its repository-relative path and its text. */
export interface ProposalSpec {
  path: string
  text: string
}

/** The findings of the proposal rule over `specs`, as `<path>:<line>: proposal: <text>`. */
export function proposalFindings(specs: readonly ProposalSpec[]): string[] {
  const findings: string[] = []
  const titles: string[] = []
  for (const { path, text } of specs) {
    const all = lines(text)
    const specHeadings = headings(all)
    for (const heading of specHeadings) {
      if (!heading.text.endsWith(PROPOSED_MARK)) {
        continue
      }
      titles.push(heading.text.slice(0, -PROPOSED_MARK.length))
      const next = specHeadings.find(other => other.line > heading.line && other.level <= heading.level)
      const end = next === undefined ? all.length + 1 : next.line
      // the index of a line after the heading is the 1-based number of the heading line
      for (let index = heading.line; index < end - 1; index++) {
        if (all[index].trim().startsWith('[@test]')) {
          findings.push(`${path}:${index + 1}: proposal: a [@test] link stands in the proposed section "${heading.text}"; a proposal has no test, so remove the link, or build the code and drop '${PROPOSED_MARK}'`)
        }
      }
    }
  }
  for (const title of titles) {
    const unmarked = `"${title}"`
    for (const { path, text } of specs) {
      for (const [index, line] of lines(text).entries()) {
        if (line.includes(unmarked)) {
          findings.push(`${path}:${index + 1}: proposal: names the proposed section ${unmarked} without '${PROPOSED_MARK}'; write "${title}${PROPOSED_MARK}", so the reader knows that the behavior is not built`)
        }
      }
    }
  }
  return findings
}
