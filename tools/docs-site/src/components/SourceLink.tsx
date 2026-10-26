// Source-linked chips shared by the pages and the detail panel: one `file:line` link into the
// in-site source viewer (or GitHub when the file is not bundled), and a concept's `api:` anchors.

import { repoPathOf } from '../../scripts/sourcePaths.ts'
import { ChipLink, ChipLinks } from '../furniture'
import { site } from '../site.ts'
import { sourceHref } from '../sourceRef'

/** A resolved symbol reference of the harvest: its text, its target, and its place. */
export interface SymbolRef {
  text: string
  target: string
  file: string | null
  line: number | null
}

/** The tooltip of a source link: the repository path, the line, and where the link opens. */
function sourceTitle(file: string | null, line: number | null | undefined, external: boolean): string {
  return `${repoPathOf(site().config.contentRoot, `${file}`)}${line ? `:${line}` : ''}${external ? ' on GitHub' : ''}`
}

/** A content-relative source path, linked to the in-site viewer (or GitHub when unbundled). */
export function SourceLink({ file, line }: { file: string, line?: number | null }) {
  const { href, external } = sourceHref(file, line)
  return (
    <a
      className="source-link [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-muted-foreground [&:hover_code]:text-foreground"
      href={href}
      {...(external ? { target: '_blank', rel: 'noreferrer' } : {})}
      title={sourceTitle(file, line, external)}
    >
      <code>{file.slice(file.lastIndexOf('/') + 1)}{line ? `:${line}` : ''}</code>
      {external && <span aria-hidden="true"> ↗</span>}
    </a>
  )
}

/** A concept's primary API anchors (`api:` line in concepts.md) — validated, source-linked. */
export function ApiChips({ api }: { api: SymbolRef[] }) {
  if (api.length === 0) {
    return null
  }
  return (
    <ChipLinks>
      {api.map(ref => {
        const { href, external } = sourceHref(ref.file!, ref.line)
        return (
          <ChipLink key={ref.target} href={href} external={external}>
            <code title={sourceTitle(ref.file, ref.line, external)}>{ref.text}</code>
            {external && <span aria-hidden="true"> ↗</span>}
          </ChipLink>
        )
      })}
    </ChipLinks>
  )
}
