// An explainer (ADR 0203): short chapters, one hand-laid diagram each. The left pane holds the
// chapter rail, the prose, and the step list; the right pane holds the canvas with its step bar, and
// the card of the selected part. useExplainerRoute owns the route: the chapter, the step, and the node.
// The step bar walks the whole explainer: past the last step it hands off to the next chapter.

import { lazy, Suspense, useEffect, useRef } from 'react'
import { Island } from '../components/Island'
import { PageBreadcrumb } from '../components/PageBreadcrumb'
import { cn } from '../lib/utils'
import type { HarvestedDiagramStep } from '../../scripts/diagram.ts'
import { crumbsForRoute } from '../crumbs'
import type { StepBarWalk } from '../graph/DiagramStepBar'
import { DocPage, NotFoundPage } from '../furniture'
import { MarkdownDoc } from '../markdown'
import { explainersRoute } from '../router'
import type { KernelRoute } from '../routeTable.ts'
import { site } from '../site.ts'
import { ChapterRail } from './explainer/ChapterRail'
import { HandOff } from './explainer/HandOff'
import { NodeCard } from './explainer/NodeCard'
import { useExplainerRoute, type ExplainerRouteModel } from './explainer/useExplainerRoute'

const HandLaidCanvas = lazy(() => import('../graph/HandLaidDiagram').then(module => ({ default: module.HandLaidCanvas })))

function StepList({ steps, active, onGo }: { steps: HarvestedDiagramStep[], active: number | null, onGo: (step: number) => void }) {
  const StepRef = site().stepRef
  return (
    <ol className="explainer-steps m-0 grid list-none gap-1.5 p-0">
      {steps.map((step, index) => {
        const number = index + 1
        const current = number === active
        return (
          <li key={number}>
            <button
              type="button"
              aria-current={current ? 'step' : undefined}
              className={cn(
                'step-card flex w-full cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2 text-left text-xs leading-snug transition-colors',
                current ? 'active border-primary bg-primary/8' : 'border-border bg-card hover:border-input',
              )}
              onClick={() => onGo(number)}
            >
              <i className={cn('mt-0.5 grid size-4.5 flex-none place-items-center rounded-full text-3xs font-bold not-italic', current ? 'bg-primary text-background' : 'bg-muted text-muted-foreground')}>{number}</i>
              <span>
                <strong className="mr-1">{step.title}.</strong>
                <span className="text-muted-foreground">{step.caption.replace(/`/g, '')}</span>
              </span>
            </button>
            {/* outside the button: a link inside a button is not valid markup */}
            {current && StepRef !== null && step.flowRef !== null && <StepRef step={step} className="mt-1 pl-10" />}
          </li>
        )
      })}
    </ol>
  )
}

function ChapterPager({ model }: { model: ExplainerRouteModel }) {
  const { previous } = model
  return (
    <nav aria-label="Chapter pager" className="explainer-pager mt-6 grid gap-2.5 border-t border-border pt-4 text-xs">
      <HandOff model={model} />
      {previous !== null && <a href={model.hrefOf(previous)} className="justify-self-start">← {previous.title}</a>}
    </nav>
  )
}

/** The eyebrow, the end row, and the actions of the step bar, so the bar walks on past the last step. */
function stepBarWalk(model: ExplainerRouteModel, atEnd: boolean): StepBarWalk {
  const { next, previous } = model
  const lastOfAll = atEnd && next === null
  return {
    onNext: model.goNext,
    onPrevious: model.goPrevious,
    canNext: !lastOfAll,
    canPrevious: model.step !== null || previous !== null,
    nextLabel: atEnd && next !== null ? { text: 'Next chapter', name: `Next chapter: ${next.title}` } : null,
    hint: previous === null
      ? undefined
      : `Pick a step, or press → to walk through. ← returns to ${previous.title}.`,
    eyebrow: (
      <div className="diagram-step-chapter hidden min-w-0 items-baseline gap-1.5 text-2xs text-muted-foreground md:flex">
        <span className="flex-none font-bold tracking-[0.07em] uppercase">{`Chapter ${model.index + 1} of ${model.chapters.length}`}</span>
        <span aria-hidden="true">·</span>
        <span className="truncate">{model.chapter.title}</span>
      </div>
    ),
    // a phone keeps the canvas visible: there the Next chapter button is enough
    end: atEnd ? <HandOff model={model} compact className="max-md:hidden" /> : null,
  }
}

export function ExplainerPage({ slug, chapter, step, node }: { slug: string, chapter: string | null, step: number | null, node: string | null }) {
  const model = useExplainerRoute(slug, { chapter, step, node })
  // the page stays mounted across chapters, so a new chapter starts at the top of its text
  const proseRef = useRef<HTMLDivElement>(null)
  const chapterId = model?.chapter.id
  useEffect(() => {
    proseRef.current?.scrollTo({ top: 0 })
  }, [chapterId])
  if (model === null) {
    return <NotFoundPage kind="explainer" name={slug} backHref={explainersRoute()} backLabel="Back to explainers" />
  }
  const { doc, chapters, diagram } = model
  const explainersDir = site().config.docs.explainers
  const current = model.chapter
  const explainerHead: KernelRoute = { page: 'explainer', slug, chapter: null, step: null, node: null }
  const crumbs = crumbsForRoute(explainerHead)
  const selected = diagram === null || model.node === null ? null : diagram.nodes.find(candidate => candidate.id === model.node) ?? null
  const lane = selected === null ? null : diagram?.columns.find(column => column.id === selected.col) ?? null
  const total = diagram?.steps.length ?? 0
  const atEnd = total === 0 || model.step === total
  const prose = (
    <>
      <PageBreadcrumb crumbs={crumbs} className="mb-3.5" />
      <h1 className="explainer-title mt-0 mb-1 text-xl tracking-[-0.02em]">{doc.title}</h1>
      {doc.description !== '' && <p className="mt-0 mb-4 text-xs text-muted-foreground">{doc.description}</p>}
      <ChapterRail chapters={chapters} active={current.id} hrefOf={model.hrefOf} stepsOf={model.stepsOf} upNext={atEnd ? model.next?.id ?? null : null} />
      <h2 className="explainer-chapter-title mt-0 mb-2 text-lg">
        <span className="mr-1.5 text-muted-foreground">{model.index + 1}.</span>
        {current.title}
      </h2>
      {model.index === 0 && doc.intro !== undefined && doc.intro !== '' && (
        <div className="explainer-intro mb-5 border-b border-border pb-1">
          <MarkdownDoc source={doc.intro} baseDir={explainersDir} />
        </div>
      )}
      <MarkdownDoc source={current.markdown} baseDir={explainersDir} />
      {diagram !== null && diagram.steps.length > 0 && (
        <section className="explainer-step-section mt-4">
          <h3 className="mt-0 mb-2 text-2xs font-bold tracking-[0.07em] uppercase text-muted-foreground">Step through the picture</h3>
          <StepList steps={diagram.steps} active={model.step} onGo={model.goStep} />
        </section>
      )}
      <ChapterPager model={model} />
    </>
  )
  if (diagram === null) {
    // the route outlet of an explainer does not scroll, so this pane does
    return (
      <Island ref={proseRef} className="explainer-page-scroll min-h-0 flex-1 overflow-y-auto text-base">
        <DocPage className="explainer-page explainer-page-prose">
          {prose}
        </DocPage>
      </Island>
    )
  }
  // two islands, as a tool window beside the editor: the prose, and the diagram on its canvas
  return (
    <div className="explainer-page grid min-h-0 flex-1 grid-cols-1 grid-rows-[minmax(0,45%)_minmax(0,1fr)] gap-2 md:grid-cols-[minmax(360px,440px)_1fr] md:grid-rows-none">
      <Island ref={proseRef} className="explainer-prose row-start-2 overflow-y-auto text-base p-3.5 md:row-start-auto md:p-5.5">
        {prose}
      </Island>
      <Island className="explainer-canvas-pane sticky top-0 row-start-1 flex h-full min-h-0 min-w-0 overflow-hidden md:col-start-2 md:row-start-auto">
        <div className="relative min-w-0 flex-1">
          <Suspense fallback={<div className="diagram-loading grid h-full place-items-center text-muted-foreground">Loading…</div>}>
            <HandLaidCanvas diagram={diagram} step={model.step} selected={model.node} onStep={model.goStep} onSelect={model.select} walk={stepBarWalk(model, atEnd)} />
          </Suspense>
        </div>
        {selected !== null && (
          <NodeCard
            diagram={diagram}
            node={selected}
            laneTitle={lane?.title ?? ''}
            color={lane?.color ?? 'neutral'}
            baseDir={site().config.docs.diagrams}
            onStep={model.goStep}
            onClose={() => model.select(null)}
          />
        )}
      </Island>
    </div>
  )
}
