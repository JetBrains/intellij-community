// In-site source viewer for files bundled by harvest (generated/sources/**). Highlighting is
// a fine-grained shiki core (JS regex engine, IntelliJ light/dark dual themes: each token
// carries --shiki-light/--shiki-dark vars and source.css picks one per theme class);
// every line gets an id and a clickable gutter anchor so `?line=<n>` deep links and
// "copy link to line" work. This whole module is a lazy chunk.

import { CheckIcon, CopyIcon, LinkIcon } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { createHighlighterCore, type HighlighterCore, type ShikiTransformer } from 'shiki/core'
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript'
import { Button } from '../components/ui/button'
import { ButtonGroup } from '../components/ui/button-group'
import { intellijDark, intellijLight } from '../shikiThemes'
import { languageForPath } from '../../scripts/sourcePaths.ts'
import { DocPage, EntityAction, EntityActions, PageIntro } from '../furniture'
import { navigate, sourceRoute, type SourceLineSelection } from '../router'
import { site } from '../site.ts'
import { githubUrl } from '../sourceLink'
import { isBundledSource, loadSource } from '../sources'

let highlighterPromise: Promise<HighlighterCore> | null = null

function getHighlighter(): Promise<HighlighterCore> {
  highlighterPromise ??= createHighlighterCore({
    themes: [intellijLight, intellijDark],
    langs: [
      import('@shikijs/langs/kotlin'),
      import('@shikijs/langs/java'),
      import('@shikijs/langs/typescript'),
      import('@shikijs/langs/tsx'),
      import('@shikijs/langs/javascript'),
      import('@shikijs/langs/xml'),
      import('@shikijs/langs/markdown'),
      import('@shikijs/langs/json'),
      import('@shikijs/langs/yaml'),
      import('@shikijs/langs/properties'),
      import('@shikijs/langs/css'),
    ],
    engine: createJavaScriptRegexEngine({ forgiving: true }),
  })
  return highlighterPromise
}

/** Adds `id="L<n>"` and a clickable line-number gutter anchor to every rendered line. */
function lineLinkTransformer(path: string): ShikiTransformer {
  return {
    line(node, line) {
      node.properties.id = `L${line}`
      node.properties['data-line'] = line
      node.children.unshift({
        type: 'element',
        tagName: 'a',
        properties: { className: ['line-no'], href: sourceRoute(path, line), 'data-line': line },
        children: [{ type: 'text', value: String(line) }],
      })
    },
  }
}

/** Applies and scrolls to the routed selection after plain or Shiki-rendered DOM is ready. */
function useLineSelection(selection: SourceLineSelection | null, ready: unknown): void {
  useEffect(() => {
    if (!ready) {
      return
    }
    const sourceView = document.querySelector('.source-view')
    if (sourceView === null) {
      return
    }
    const previouslySelected = Array.from(sourceView.querySelectorAll('.line-selected'))
    const selected: Element[] = []
    if (selection !== null) {
      for (let line = selection.start; line <= selection.end; line++) {
        const element = sourceView.querySelector(`#L${line}`)
        if (element !== null) {
          selected.push(element)
        }
      }
    }
    previouslySelected.forEach(element => element.classList.remove('line-selected'))
    selected.forEach(element => element.classList.add('line-selected'))
    selected[0]?.scrollIntoView({ block: 'center' })
  }, [selection?.end, selection?.start, ready])
}

/** Plain numbered rendering, shown until (or instead of) the highlighted version. */
function PlainSource({ path, text }: { path: string, text: string }) {
  const lines = useMemo(() => text.split('\n'), [text])
  return (
    <pre className="shiki">
      <code>
        {lines.map((content, index) => (
          <span className="line" data-line={index + 1} id={`L${index + 1}`} key={index}>
            <a className="line-no" data-line={index + 1} href={sourceRoute(path, index + 1)}>{index + 1}</a>
            {content}
            {'\n'}
          </span>
        ))}
      </code>
    </pre>
  )
}

function SourceCode({ path, text, selection, highlighter }: {
  path: string
  text: string
  selection: SourceLineSelection | null
  highlighter: HighlighterCore | null
}) {
  // codeToHtml is synchronous once the core is loaded; running it in an effect lets the plain
  // rendering paint first on large files, and keying the result by text avoids showing stale
  // highlighting while a new file is being processed
  const [rendered, setRendered] = useState<{ text: string, html: string } | null>(null)
  useEffect(() => {
    if (highlighter === null) {
      setRendered(null)
      return
    }
    setRendered({
      text,
      html: highlighter.codeToHtml(text, {
        lang: languageForPath(path) ?? 'text',
        themes: { light: 'intellij-light', dark: 'intellij-dark' },
        defaultColor: false,
        transformers: [lineLinkTransformer(path)],
      }),
    })
  }, [highlighter, path, text])
  const html = rendered !== null && rendered.text === text ? rendered.html : null
  useLineSelection(selection, html ?? text)
  return html === null
    ? <PlainSource path={path} text={text} />
    : <div dangerouslySetInnerHTML={{ __html: html }} />
}

function clampSelection(selection: SourceLineSelection | null, lineCount: number): SourceLineSelection | null {
  if (selection === null) {
    return null
  }
  return {
    start: Math.min(selection.start, lineCount),
    end: Math.min(selection.end, lineCount),
  }
}

function sameSelection(left: SourceLineSelection | null, right: SourceLineSelection | null): boolean {
  return left?.start === right?.start && left?.end === right?.end
}

export default function SourcePage({ path, selection }: { path: string, selection: SourceLineSelection | null }) {
  const bundled = isBundledSource(path)
  const [text, setText] = useState<string | null>(null)
  const [highlighter, setHighlighter] = useState<HighlighterCore | null>(null)
  const [copyStatus, setCopyStatus] = useState<'idle' | 'link' | 'code' | 'failed'>('idle')
  const selectionAnchor = useRef<number | null>(selection?.start ?? null)
  const copyStatusTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    setText(null)
    if (isBundledSource(path)) {
      let cancelled = false
      loadSource(path).then(loaded => {
        if (!cancelled) {
          setText(loaded)
        }
      })
      return () => {
        cancelled = true
      }
    }
  }, [path])

  useEffect(() => {
    getHighlighter().then(setHighlighter)
  }, [])

  useEffect(() => () => {
    if (copyStatusTimer.current !== null) {
      clearTimeout(copyStatusTimer.current)
    }
  }, [])

  const fileName = path.slice(path.lastIndexOf('/') + 1)
  const owner = useMemo(() => site().refLinks.sourceModule?.(path) ?? null, [path])
  const lines = useMemo(() => text?.split('\n') ?? null, [text])
  const effectiveSelection = useMemo(
    () => lines === null ? selection : clampSelection(selection, lines.length),
    [lines, selection],
  )

  useEffect(() => {
    if (lines === null) {
      return
    }
    const currentLineParam = new URL(window.location.href).searchParams.get('line')
    const canonicalLineParam = new URL(sourceRoute(path, effectiveSelection), window.location.origin).searchParams.get('line')
    if (currentLineParam !== canonicalLineParam || !sameSelection(selection, effectiveSelection)) {
      navigate(sourceRoute(path, effectiveSelection), { replace: true })
    }
  }, [effectiveSelection, lines, path, selection])

  useEffect(() => {
    if (effectiveSelection === null) {
      selectionAnchor.current = null
    }
    else if (effectiveSelection.start === effectiveSelection.end) {
      selectionAnchor.current = effectiveSelection.start
    }
  }, [effectiveSelection])

  function selectLine(event: ReactMouseEvent<HTMLDivElement>): void {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey) {
      return
    }
    const link = (event.target as HTMLElement).closest<HTMLAnchorElement>('a.line-no')
    if (link === null || !event.currentTarget.contains(link)) {
      return
    }
    const line = Number(link.dataset.line)
    if (!Number.isSafeInteger(line) || line < 1) {
      return
    }
    event.preventDefault()
    const anchor = event.shiftKey ? selectionAnchor.current ?? effectiveSelection?.start ?? line : line
    if (!event.shiftKey) {
      selectionAnchor.current = line
    }
    setCopyStatus('idle')
    navigate(sourceRoute(path, { start: Math.min(anchor, line), end: Math.max(anchor, line) }))
  }

  async function copySelection(kind: 'link' | 'code'): Promise<void> {
    if (effectiveSelection === null || lines === null) {
      return
    }
    const value = kind === 'link'
      ? new URL(sourceRoute(path, effectiveSelection), window.location.origin).href
      : lines.slice(effectiveSelection.start - 1, effectiveSelection.end).join('\n')
    try {
      await navigator.clipboard.writeText(value)
      setCopyStatus(kind)
    }
    catch {
      setCopyStatus('failed')
    }
    if (copyStatusTimer.current !== null) {
      clearTimeout(copyStatusTimer.current)
    }
    copyStatusTimer.current = setTimeout(() => setCopyStatus('idle'), 2000)
  }

  if (!bundled) {
    return (
      <DocPage>
        <PageIntro>
          <h1>{fileName}</h1>
          <p>
            This file is not bundled with the site.{' '}
            <a href={githubUrl(path, selection)} target="_blank" rel="noreferrer">Open it on GitHub ↗</a>
          </p>
        </PageIntro>
      </DocPage>
    )
  }

  return (
    <DocPage className="source-page">
      <PageIntro>
        <h1>{fileName}</h1>
        <EntityActions>
          <EntityAction href={githubUrl(path, effectiveSelection)} external link>Open on GitHub ↗</EntityAction>
          {owner !== null && <EntityAction href={owner.href}>{owner.title}</EntityAction>}
        </EntityActions>
        <div className="source-selection-actions mt-2 flex flex-wrap items-center gap-2">
          {effectiveSelection !== null && (
            <ButtonGroup>
              <Button variant="outline" size="xs" onClick={() => copySelection('link')}>
                {copyStatus === 'link' ? <CheckIcon data-icon="inline-start" /> : <LinkIcon data-icon="inline-start" />}
                {copyStatus === 'link' ? 'Link copied' : 'Copy link'}
              </Button>
              <Button variant="outline" size="xs" onClick={() => copySelection('code')}>
                {copyStatus === 'code' ? <CheckIcon data-icon="inline-start" /> : <CopyIcon data-icon="inline-start" />}
                {copyStatus === 'code' ? 'Code copied' : 'Copy code'}
              </Button>
            </ButtonGroup>
          )}
          <span className="text-xs text-muted-foreground">Click a line number; Shift-click to select a range.</span>
          <span className="sr-only" aria-live="polite">
            {copyStatus === 'link' ? 'Source link copied.' : copyStatus === 'code' ? 'Selected code copied.' : copyStatus === 'failed' ? 'Copy failed.' : ''}
          </span>
        </div>
      </PageIntro>
      {/* the pre/code chrome rides on descendant variants: the <pre> is shiki/PlainSource output,
          but this wrapper is ours; per-line DOM (.line-no, .line) stays in source.css */}
      <div className="source-view mt-6 [&_pre]:m-0 [&_pre]:overflow-x-auto [&_pre]:rounded-lg [&_pre]:border [&_pre]:border-border [&_pre]:bg-(--code-canvas) [&_pre]:px-0 [&_pre]:py-3 [&_pre]:font-mono [&_pre]:text-xs [&_pre]:leading-[1.6] [&_pre]:text-(--code-fg) [&_pre_code]:rounded-none [&_pre_code]:bg-transparent [&_pre_code]:p-0 [&_pre_code]:text-[length:inherit]" onClick={selectLine}>
        {text === null
          ? <p className="text-muted-foreground">Loading source…</p>
          : <SourceCode path={path} text={text} selection={effectiveSelection} highlighter={highlighter} />}
      </div>
    </DocPage>
  )
}
