// The chapter list of an explainer: numbered, the active one marked. A vertical list on a desktop,
// a chip strip that scrolls sideways on a phone.

import { cn } from '../../lib/utils'
import type { ExplainerChapter } from '../../../scripts/explainerDoc'

export function ChapterRail({ chapters, active, hrefOf }: {
  chapters: ExplainerChapter[]
  active: string
  hrefOf: (chapter: ExplainerChapter) => string
}) {
  return (
    <nav aria-label="Chapters" className="chapter-rail -mx-1 mb-4 flex gap-1 overflow-x-auto px-1 pb-1 md:mx-0 md:mb-5 md:grid md:gap-0.5 md:overflow-visible md:px-0 md:pb-0">
      {chapters.map((chapter, index) => {
        const current = chapter.id === active
        return (
          <a
            key={chapter.id}
            href={hrefOf(chapter)}
            aria-current={current ? 'page' : undefined}
            className={cn(
              'chapter-link flex flex-none items-center gap-2 rounded-md border px-2.5 py-1.5 text-detail leading-tight hover:no-underline md:flex-auto md:border-0 md:border-l-2 md:rounded-none md:px-2.5 md:py-1',
              current
                ? 'border-primary bg-primary/8 text-primary md:bg-transparent'
                : 'border-border text-muted-foreground hover:text-foreground md:border-border',
            )}
          >
            <i className={cn('grid size-4.5 flex-none place-items-center rounded-full text-3xs font-bold not-italic', current ? 'bg-primary text-background' : 'bg-muted text-muted-foreground')}>{index + 1}</i>
            <span className="whitespace-nowrap md:whitespace-normal">{chapter.title}</span>
          </a>
        )
      })}
    </nav>
  )
}
