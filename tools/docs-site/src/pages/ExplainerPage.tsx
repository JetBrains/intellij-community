// An explainer (ADR 0203): short chapters, one hand-laid diagram each. The left pane holds the
// chapter rail, the prose, and the step list; the right pane holds the canvas with its step bar, and
// the card of the selected part. useExplainerRoute owns the route: the chapter, the step, and the node.

import { lazy, Suspense } from 'react'
import { PageBreadcrumb } from '../components/PageBreadcrumb'
import { cn } from '../lib/utils'
import type { HarvestedDiagramStep } from '../../scripts/diagram.ts'
import { crumbsForRoute } from '../crumbs'
import { DocPage, NotFoundPage } from '../furniture'
import { MarkdownDoc } from '../markdown'
import { explainersRoute } from '../router'
import type { KernelRoute } from '../routeTable.ts'
import { site } from '../site.ts'
import { ChapterRail } from './explainer/ChapterRail'
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
                'step-card flex w-full cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2 text-left text-detail leading-snug transition-colors',
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
  const previous = model.chapters[model.index - 1]
  const next = model.chapters[model.index + 1]
  return (
    <nav aria-label="Chapter pager" className="explainer-pager mt-6 flex justify-between gap-3 border-t border-border pt-3 text-detail">
      {previous ? <a href={model.hrefOf(previous)}>← {previous.title}</a> : <span />}
      {next ? <a href={model.hrefOf(next)}>{next.title} →</a> : <span />}
    </nav>
  )
}

export function ExplainerPage({ slug, chapter, step, node }: { slug: string, chapter: string | null, step: number | null, node: string | null }) {
  const model = useExplainerRoute(slug, { chapter, step, node })
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
  const prose = (
    <>
      <PageBreadcrumb crumbs={crumbs} className="mb-3.5" />
      <h1 className="explainer-title mt-0 mb-1 text-xl tracking-[-0.02em]">{doc.title}</h1>
      {doc.description !== '' && <p className="mt-0 mb-4 text-detail text-muted-foreground">{doc.description}</p>}
      <ChapterRail chapters={chapters} active={current.id} hrefOf={model.hrefOf} />
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
    return (
      <DocPage className="explainer-page explainer-page-prose">
        {prose}
      </DocPage>
    )
  }
  return (
    <div className="explainer-page grid min-h-0 flex-1 grid-cols-1 grid-rows-[minmax(0,45%)_minmax(0,1fr)] gap-0 md:grid-cols-[minmax(360px,440px)_1fr] md:grid-rows-none">
      <div className="explainer-prose row-start-2 overflow-y-auto border-t border-border p-3.5 md:row-start-auto md:border-t-0 md:border-r md:p-5.5">
        {prose}
      </div>
      <div className="explainer-canvas-pane sticky top-0 row-start-1 flex h-full min-h-0 min-w-0 md:col-start-2 md:row-start-auto">
        <div className="relative min-w-0 flex-1">
          <Suspense fallback={<div className="diagram-loading grid h-full place-items-center text-muted-foreground">Loading…</div>}>
            <HandLaidCanvas diagram={diagram} step={model.step} selected={model.node} onStep={model.goStep} onSelect={model.select} />
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
      </div>
    </div>
  )
}
