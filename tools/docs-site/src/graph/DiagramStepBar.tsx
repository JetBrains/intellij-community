// The step bar of a hand-laid diagram: the pager, the progress dots, the caption of the active step, and the row of
// a step that binds to a site entity (the flow step of Air). Both the compact figure in a guide and the explainer
// canvas use it.

import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import type { ComponentType } from 'react'
import { StepProgress } from '../components/StepProgress'
import { Button } from '../components/ui/button'
import { ButtonGroup, ButtonGroupText } from '../components/ui/button-group'
import { Tooltip, TooltipContent, TooltipTrigger } from '../components/ui/tooltip'
import { cn } from '../lib/utils'
import type { HarvestedDiagramStep } from '../../scripts/diagram.ts'
import { Markdown } from '../markdown'

export function DiagramStepBar({ steps, active, onGo, className, baseDir, stepRef: StepRef }: {
  steps: HarvestedDiagramStep[]
  /** The 1-based active step, or null for the overview. */
  active: number | null
  onGo: (step: number | null) => void
  className?: string
  /** The directory the caption markdown resolves relative links against. */
  baseDir: string
  /** The row of a step that binds to a site entity, or null when the site has none. */
  stepRef: ComponentType<{ step: HarvestedDiagramStep, className?: string }> | null
}) {
  const step = active === null ? null : steps[active - 1] ?? null
  return (
    <div className={cn('diagram-step-bar flex flex-col gap-1.5 rounded-lg border border-border bg-card/95 px-3 py-2 shadow-xs backdrop-blur-sm', className)} role="group" aria-label="Diagram steps">
      <div className="flex items-center gap-1.5">
        <ButtonGroup className="diagram-pager">
          <Tooltip>
            <TooltipTrigger
              render={(
                <Button
                  variant="outline"
                  size="icon-sm"
                  aria-label="Previous step"
                  aria-keyshortcuts="ArrowLeft"
                  disabled={active === null}
                  onClick={() => onGo(active === null || active <= 1 ? null : active - 1)}
                />
              )}
            >
              <ChevronLeftIcon aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent>Previous step (←)</TooltipContent>
          </Tooltip>
          <ButtonGroupText className="diagram-steps-counter h-7 text-xs font-normal whitespace-nowrap text-muted-foreground tabular-nums">
            {active === null ? `${steps.length} steps` : `Step ${active} of ${steps.length}`}
          </ButtonGroupText>
          <Tooltip>
            <TooltipTrigger
              render={(
                <Button
                  variant="outline"
                  size="icon-sm"
                  aria-label="Next step"
                  aria-keyshortcuts="ArrowRight"
                  disabled={active === steps.length}
                  onClick={() => onGo(active === null ? 1 : active + 1)}
                />
              )}
            >
              <ChevronRightIcon aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent>Next step (→)</TooltipContent>
          </Tooltip>
        </ButtonGroup>
        <StepProgress total={steps.length} active={active} onGo={onGo} />
        <Button
          variant="outline"
          size="sm"
          className={cn('diagram-overview', active === null && 'active')}
          disabled={active === null}
          onClick={() => onGo(null)}
        >
          Overview
        </Button>
      </div>
      <div className="diagram-step-caption min-h-5 text-detail leading-snug" aria-live="polite">
        {step === null
          ? <span className="text-muted-foreground">Pick a step, or press → to walk through.</span>
          : (
              <>
                <strong className="mr-1.5">{step.title}.</strong>
                <Markdown source={step.caption} baseDir={baseDir} inline />
              </>
            )}
      </div>
      {step !== null && step.flowRef !== null && StepRef !== null && <StepRef step={step} />}
    </div>
  )
}
