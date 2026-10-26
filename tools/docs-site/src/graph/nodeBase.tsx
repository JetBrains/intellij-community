// The node chrome that the cards of every diagram share: the base box, the small text line, and the step badges.
// A card appends its own skin to `nodeBase`, and the two never set the same utility.

export const nodeBase = 'flex size-full flex-col justify-center gap-0.5 rounded-lg border px-3.5 py-2 text-sm'
export const nodeSmall = 'text-xs leading-[1.3] text-muted-foreground wrap-anywhere'

/** Sequence overlay for flow walkthroughs: the step numbers this node participates in. */
export function StepBadges({ data }: { data: Record<string, unknown> }) {
  const steps = data.stepBadges as number[] | undefined
  if (!steps || steps.length === 0) {
    return null
  }
  return (
    <span className="step-badges absolute -top-2.5 right-2 flex gap-0.75" aria-hidden="true">
      {steps.map(step => (
        <i key={step} className="inline-grid h-4.25 min-w-4.25 place-items-center rounded-full border border-background bg-primary px-1 text-xs font-bold not-italic text-primary-foreground">{step}</i>
      ))}
    </span>
  )
}
