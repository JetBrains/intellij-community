// The clickable step progress of a walkthrough: the flow walkthrough bar of a site and the step bar of a hand-laid
// diagram both show it.

/** Clickable step progress: dots for short flows, a segmented bar once dots would crowd the pane. */
export function StepProgress({ total, active, onGo }: { total: number, active: number | null, onGo: (step: number) => void }) {
  const dots = total <= 12
  return (
    <div
      className={`flow-progress flex flex-1 items-center justify-center min-w-16 ${dots ? 'gap-0.5' : 'gap-px'}`}
      role="group"
      aria-label="Walkthrough progress"
    >
      {Array.from({ length: total }, (_, index) => {
        const step = index + 1
        const fill = active === null || step > active
          ? 'bg-border'
          : step === active ? 'bg-sidebar-primary' : 'bg-sidebar-primary/40'
        return dots
          ? (
              <button
                key={step}
                type="button"
                aria-label={`Go to step ${step}`}
                aria-current={step === active ? 'step' : undefined}
                className="group flex flex-1 min-w-2 max-w-4 items-center justify-center h-4 p-0 border-none bg-transparent cursor-pointer"
                onClick={() => onGo(step)}
              >
                <i className={`w-2 h-2 rounded-full transition-colors group-hover:bg-sidebar-primary ${fill}`} />
              </button>
            )
          : (
              <button
                key={step}
                type="button"
                aria-label={`Go to step ${step}`}
                aria-current={step === active ? 'step' : undefined}
                className={`flex-1 min-w-0 h-1.5 p-0 border-none cursor-pointer hover:bg-sidebar-primary first:rounded-l-full last:rounded-r-full ${fill}`}
                onClick={() => onGo(step)}
              />
            )
      })}
    </div>
  )
}
