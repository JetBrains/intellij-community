// The chapter list of an explainer as a progress line: the chapters before the current one, the current one, and the
// ones ahead each have their own marker, so the list does not look like the step badges of the diagram. A vertical
// line on a desktop, a chip strip that scrolls sideways on a phone. The progress is positional, so a deep link shows
// it too and no storage is needed.

import { useEffect, useRef } from 'react'
import { Badge } from '../../components/ui/badge'
import { cn } from '../../lib/utils'
import type { ExplainerChapter } from '../../../scripts/explainerDoc'

const MARKER = {
  done: 'bg-primary/15 text-primary',
  current: 'bg-primary text-background',
  ahead: 'border border-border bg-background text-muted-foreground',
} as const

export function ChapterRail({ chapters, active, hrefOf, stepsOf, upNext }: {
  chapters: ExplainerChapter[]
  active: string
  hrefOf: (chapter: ExplainerChapter) => string
  /** The number of steps of the chapter diagram; 0 for a chapter without a diagram. */
  stepsOf: (chapter: ExplainerChapter) => number
  /** The id of the chapter that the reader reaches next, or null before the end of the current chapter. */
  upNext: string | null
}) {
  const activeIndex = chapters.findIndex(chapter => chapter.id === active)
  const railRef = useRef<HTMLElement>(null)
  // the chip strip of a phone: keep the current chip in view. Not scrollIntoView, which also scrolls the route outlet.
  useEffect(() => {
    const rail = railRef.current
    const link = rail?.querySelector('[aria-current="page"]')
    if (rail == null || link == null || rail.scrollWidth <= rail.clientWidth) {
      return
    }
    const railBox = rail.getBoundingClientRect()
    const linkBox = link.getBoundingClientRect()
    if (linkBox.left < railBox.left) {
      rail.scrollLeft += linkBox.left - railBox.left - 8
    }
    else if (linkBox.right > railBox.right) {
      rail.scrollLeft += linkBox.right - railBox.right + 8
    }
  }, [active])
  return (
    <section className="chapter-rail-section mb-4 md:mb-5">
      <h3 className="mt-0 mb-1.5 hidden text-2xs font-bold tracking-[0.07em] uppercase text-muted-foreground md:block">Chapters</h3>
      <nav ref={railRef} aria-label="Chapters" className="chapter-rail -mx-1 flex gap-1 overflow-x-auto px-1 pb-1 md:mx-0 md:grid md:gap-0 md:overflow-visible md:px-0 md:pb-0">
        {chapters.map((chapter, index) => {
          const state = index < activeIndex ? 'done' : index === activeIndex ? 'current' : 'ahead'
          const steps = stepsOf(chapter)
          return (
            <a
              key={chapter.id}
              href={hrefOf(chapter)}
              aria-current={state === 'current' ? 'page' : undefined}
              data-state={state}
              className={cn(
                'chapter-link relative flex flex-none items-center gap-2 rounded-md border px-2.5 py-1.5 text-xs leading-tight hover:no-underline md:flex-auto md:items-start md:border-0 md:px-1.5',
                state === 'current'
                  ? 'border-primary bg-primary/8 font-medium text-foreground'
                  : 'border-border text-muted-foreground hover:text-foreground md:hover:bg-muted',
              )}
            >
              {/* the line from this marker to the next one, filled up to the current chapter */}
              {index < chapters.length - 1 && (
                <span
                  aria-hidden="true"
                  className={cn('absolute top-6 -bottom-1.5 left-[calc(0.9375rem-1px)] hidden w-0.5 md:block', index < activeIndex ? 'bg-primary/40' : 'bg-input')}
                />
              )}
              <i className={cn('relative grid size-4.5 flex-none place-items-center rounded-full text-3xs font-bold not-italic', MARKER[state])}>{index + 1}</i>
              <span className="flex min-w-0 flex-col gap-0.5 md:pt-px">
                <span className="flex items-center gap-1.5">
                  <span className="whitespace-nowrap md:whitespace-normal">{chapter.title}</span>
                  {chapter.id === upNext && <Badge className="chapter-up-next h-4 px-1.5 text-3xs">Up next</Badge>}
                </span>
                {steps > 0 && <span className="hidden text-2xs font-normal text-muted-foreground md:inline">{steps === 1 ? '1 step' : `${steps} steps`}</span>}
              </span>
            </a>
          )
        })}
      </nav>
    </section>
  )
}
