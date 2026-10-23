// What comes after the current chapter: the next chapter with its opening question, or the end of the explainer with
// the way to the next one. The step bar shows it at the last step, and the prose shows it at its end, so a chapter
// without a diagram hands off too.

import { ArrowRightIcon } from 'lucide-react'
import { cn } from '../../lib/utils'
import { explainersRoute } from '../../router'
import type { ExplainerRouteModel } from './useExplainerRoute'

export function HandOff({ model, compact = false, className }: { model: ExplainerRouteModel, compact?: boolean, className?: string }) {
  const { next, nextExplainer } = model
  const box = cn(
    'hand-off group flex items-center gap-3 rounded-lg border text-left hover:no-underline',
    compact ? 'border-border/70 bg-muted/50 px-2.5 py-1.5' : 'border-border bg-card px-3.5 py-3 hover:border-primary',
    className,
  )
  const eyebrow = 'text-3xs font-bold tracking-[0.07em] uppercase text-muted-foreground'
  if (next !== null) {
    return (
      <a href={model.hrefOf(next)} className={box} data-hand-off="chapter">
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className={eyebrow}>{`Next · chapter ${model.index + 2} of ${model.chapters.length}`}</span>
          <strong className={cn('text-foreground group-hover:text-primary', compact ? 'truncate text-xs' : 'text-sm')}>{next.title}</strong>
          {next.question !== null && <span className={cn('text-muted-foreground', compact ? 'truncate text-2xs' : 'text-xs')}>{next.question}</span>}
        </span>
        <ArrowRightIcon className="size-4 flex-none text-muted-foreground group-hover:text-primary" aria-hidden="true" />
      </a>
    )
  }
  const target = nextExplainer ?? { href: explainersRoute(), title: null }
  return (
    <a href={target.href} className={box} data-hand-off="end">
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className={eyebrow}>End of the explainer</span>
        <strong className={cn('text-foreground group-hover:text-primary', compact ? 'truncate text-xs' : 'text-sm')}>
          {target.title === null ? 'Back to explainers' : `Next explainer: ${target.title}`}
        </strong>
      </span>
      <ArrowRightIcon className="size-4 flex-none text-muted-foreground group-hover:text-primary" aria-hidden="true" />
    </a>
  )
}
