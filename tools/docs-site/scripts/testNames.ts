// The test names of a `[@test]` link of a spec, and the matcher that finds the declaration of a test in a source file.
// The spec check (specRefs.ts) reads both.
//
// The gate of a site loads this file on bun without node_modules, so it imports nothing from npm.

/** A Rust `#[test]` or `#[tokio::test(…)]`, then any other attributes, then the `fn` of the test. */
export const RUST_TEST_FN = String.raw`#\[(?:tokio::)?test(?:\([^)]*\))?\]\s*(?:#\[[^\]]*\]\s*)*(?:async\s+)?fn\s+`

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * The backticked test names that follow a `[@test]` path, or an empty list when the link names none. `afterPath` is
 * the offset just past the path. The group opens on the line of the path, and it can close on a later line, so the
 * scan crosses a line break. A split on backtick pairs keeps a name with a comma whole. Only that group counts,
 * because the prose of a spec puts many other identifiers in backticks.
 */
export function testLinkNames(text: string, afterPath: number): string[] {
  let open = afterPath
  while (open < text.length && (text[open] === ' ' || text[open] === '\t')) {
    open++
  }
  if (open >= text.length || text[open] !== '(') {
    return []
  }
  let depth = 0
  let close = -1
  for (let index = open; index < text.length; index++) {
    if (text[index] === '(') {
      depth++
    }
    else if (text[index] === ')') {
      depth--
      if (depth === 0) {
        close = index
        break
      }
    }
  }
  if (close < 0) {
    return []
  }
  const parts = text.slice(open + 1, close).split('`')
  const names: string[] = []
  for (let index = 1; index < parts.length; index += 2) {
    const name = parts[index].trim()
    if (name.length > 0) {
      names.push(name)
    }
  }
  return names
}

/**
 * True when `source`, the text of the file at `path`, declares the test `name` in the form of the language of the file.
 *
 * - Rust: only a function under `#[test]` or `#[tokio::test]`, with or without arguments. Other attributes such as
 *   `#[ignore]` can stand between. So a helper of the same name declares no test.
 * - TypeScript and JavaScript: `test('name', …)` or `it('name', …)`, in any quote, plain or with `.only`, `.skip`,
 *   `.fixme` or `.fail`. The title of a `test.describe` is no test.
 * - Python and Starlark: `def name(`, as a `unittest` method or a test macro of a `.bzl` file.
 * - JSON and NDJSON: `"name": "name"`, as a flow profile or a case of a corpus.
 * - Any other file, such as Kotlin: a backticked sentence name or a bare name after `fun`.
 */
export function declaresTest(path: string, source: string, name: string): boolean {
  const escaped = escapeRegex(name)
  const extension = path.slice(path.lastIndexOf('.') + 1)
  switch (extension) {
    case 'rs':
      return new RegExp(`${RUST_TEST_FN}${escaped}\\s*\\(`).test(source)
    case 'ts':
    case 'tsx':
    case 'js':
    case 'mjs':
      return new RegExp(`\\b(?:test|it)(?:\\.(?:only|skip|fixme|fail))?\\(\\s*(['"\`])${escaped}\\1`).test(source)
    case 'py':
    case 'bzl':
      return new RegExp(`def\\s+${escaped}\\s*\\(`).test(source)
    case 'json':
    case 'ndjson':
      return new RegExp(`"name"\\s*:\\s*"${escaped}"`).test(source)
    default:
      return source.includes(`fun \`${name}\``) || new RegExp(`fun\\s+${escaped}\\s*\\(`).test(source)
  }
}
