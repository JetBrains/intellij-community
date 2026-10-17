// A hand-laid diagram on React Flow (ADR 0203). The grid comes from the author, the pixels from
// diagramLayout.ts of the kernel, the cards from its DiagramNode.tsx, and the edges from the shared ArchEdge in its
// orthogonal shape. Two modes: a compact figure inside a guide, and the full canvas of an explainer.
// Lazily loaded, so a page without a diagram never downloads React Flow.

import '@xyflow/react/dist/style.css'
import { Controls, type Edge, type Node, type NodeTypes, Panel, ReactFlow } from '@xyflow/react'
import { useMemo, useState } from 'react'
import type { HarvestedDiagram } from '../../scripts/diagram.ts'
import type { DiagramKindStyle } from '../../scripts/siteConfig.ts'
import { explainerRoute, navigate } from '../router'
import { site } from '../site.ts'
import { guideSlug } from '../siteData.ts'
import { useResolvedTheme } from '../theme'
import { layoutDiagram, stepClasses } from './diagramLayout'
import { createDiagramNode, kindStyles } from './DiagramNode'
import { DiagramStepBar, type StepBarWalk } from './DiagramStepBar'
import { archEdgeTypes } from './edges'
import { GroupNode } from './GroupNode'

/** The node renderers and the kind skins of the site vocabulary; this module is a lazy chunk, so the site is installed. */
let vocabulary: { nodeTypes: NodeTypes, kindStyle: (kind: string) => DiagramKindStyle } | null = null

function diagramVocabulary(): NonNullable<typeof vocabulary> {
  vocabulary ??= {
    // the layout emits only the lane group boxes and the cards
    nodeTypes: { archGroup: GroupNode, diagramNode: createDiagramNode(site().config.diagram) },
    kindStyle: kindStyles(site().config.diagram),
  }
  return vocabulary
}

/** The explainer chapter that shows the diagram, for the "open in the explainer" link. */
export function explainerChapterOf(diagramId: string): { slug: string, chapter: string } | null {
  for (const doc of site().index.explainerDocs) {
    const chapter = doc.chapters.find(candidate => candidate.diagram === diagramId)
    if (chapter) {
      return { slug: guideSlug(doc.path), chapter: chapter.id }
    }
  }
  return null
}

/** The key of the diagram: the kinds and the marks in use, so a reader never guesses a shape. */
function Legend({ diagram }: { diagram: HarvestedDiagram }) {
  const kinds = [...new Set(diagram.nodes.map(node => node.kind))].map(diagramVocabulary().kindStyle)
  const planned = diagram.nodes.some(node => node.planned !== null)
  const dashedEdge = diagram.edges.some(edge => edge.dashed)
  return (
    <dl className="diagram-legend m-0 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-border bg-card/90 px-2.5 py-1 text-3xs text-muted-foreground backdrop-blur-sm">
      <dt className="sr-only">Key</dt>
      {kinds.map(kind => (
        <dd key={kind.id} className="m-0 flex items-center gap-1">
          <i className={`inline-block size-3 rounded-sm border ${kind.className.replace(/rounded-\S+/g, '')}`} aria-hidden="true" />
          {kind.glyph !== null && <span className="not-italic" aria-hidden="true">{kind.glyph}</span>}
          {kind.label}
        </dd>
      ))}
      {planned && <dd className="m-0 font-bold tracking-[0.05em] uppercase text-primary">Planned</dd>}
      {dashedEdge && (
        <dd className="m-0 flex items-center gap-1">
          <i className="inline-block w-4 border-t border-dashed border-foreground" aria-hidden="true" />
          planned edge
        </dd>
      )}
    </dl>
  )
}

function Canvas({ diagram, step, selected, onSelect, full, resolvedTheme, children }: {
  diagram: HarvestedDiagram
  step: number | null
  selected: string | null
  onSelect: (id: string | null) => void
  full: boolean
  resolvedTheme: 'light' | 'dark'
  children?: React.ReactNode
}) {
  const layout = useMemo(() => layoutDiagram(diagram), [diagram])
  const classes = useMemo(() => stepClasses(diagram, step), [diagram, step])
  const nodes: Node[] = useMemo(() => layout.nodes.map(node => node.type === 'diagramNode'
    ? { ...node, selected: node.id === selected, className: classes.nodes.get(node.id) }
    : node), [layout, classes, selected])
  const edges: Edge[] = useMemo(() => layout.edges.map(edge => ({
    ...edge,
    className: [edge.className, classes.edges.get(edge.id)].filter(Boolean).join(' ') || undefined,
    data: { ...edge.data, state: classes.edges.get(edge.id) },
  })), [layout, classes])
  return (
    <ReactFlow
      key={diagram.id}
      className={`hand-laid-canvas${full ? ' hand-laid-canvas-full' : ''}`}
      nodes={nodes}
      edges={edges}
      nodeTypes={diagramVocabulary().nodeTypes}
      edgeTypes={archEdgeTypes}
      fitView
      fitViewOptions={{ padding: 0.06 }}
      minZoom={0.2}
      maxZoom={1.6}
      colorMode={resolvedTheme}
      nodesConnectable={false}
      nodesDraggable={false}
      elementsSelectable
      autoPanOnNodeFocus={false}
      panOnDrag={full}
      zoomOnScroll={full}
      zoomOnPinch={full}
      zoomOnDoubleClick={false}
      preventScrolling={full}
      proOptions={{ hideAttribution: true }}
      onNodeClick={(_, node) => {
        if (node.type === 'diagramNode') {
          onSelect(node.id)
        }
      }}
      onPaneClick={() => onSelect(null)}
    >
      {full && <Controls showInteractive={false} position="bottom-right" />}
      {full && <Panel position="top-right"><Legend diagram={diagram} /></Panel>}
      {children}
    </ReactFlow>
  )
}


/**
 * The compact figure of a ```diagram fence: the canvas at the fence height, the step bar below,
 * and the caption with the way to the explainer. A click on a card opens the card in the explainer.
 */
export function HandLaidFigure({ diagram, initialStep, height, title }: {
  diagram: HarvestedDiagram
  initialStep: number | null
  height: number | null
  title: string | null
}) {
  const [step, setStep] = useState<number | null>(initialStep)
  const resolvedTheme = useResolvedTheme()
  const layout = useMemo(() => layoutDiagram(diagram), [diagram])
  const chapter = explainerChapterOf(diagram.id)
  const canvasHeight = height ?? Math.min(Math.max(layout.height + 24, 240), 560)
  const openIn = (node: string | null): void => {
    if (chapter) {
      navigate(explainerRoute(chapter.slug, chapter.chapter, { step, node }))
    }
  }
  return (
    <figure className="hand-laid-diagram my-5.5 [&_figcaption]:mt-1.5 [&_figcaption]:text-center [&_figcaption]:text-xs [&_figcaption]:text-muted-foreground">
      <div className="mini-graph overflow-hidden rounded-t-lg border border-border bg-canvas" style={{ height: canvasHeight }}>
        <Canvas diagram={diagram} step={step} selected={null} onSelect={id => id !== null && openIn(id)} full={false} resolvedTheme={resolvedTheme} />
      </div>
      {/* the key sits under the canvas here: a compact canvas has no free corner for it */}
      <div className="flex justify-end border-x border-border bg-card px-2 py-1"><Legend diagram={diagram} /></div>
      <DiagramStepBar steps={diagram.steps} active={step} onGo={setStep} className="rounded-t-none border-t-0" baseDir={site().config.docs.diagrams} stepRef={site().stepRef} />
      <figcaption>
        {title ?? diagram.summary}
        {chapter && <> — <a href={explainerRoute(chapter.slug, chapter.chapter, { step })}>open in the explainer →</a></>}
      </figcaption>
    </figure>
  )
}

/** The full canvas of an explainer chapter. The route owns the step and the selection, and `walk` goes on past the steps. */
export function HandLaidCanvas({ diagram, step, selected, onStep, onSelect, walk }: {
  diagram: HarvestedDiagram
  step: number | null
  selected: string | null
  onStep: (step: number | null) => void
  onSelect: (id: string | null) => void
  walk?: StepBarWalk
}) {
  const resolvedTheme = useResolvedTheme()
  return (
    <div className="hand-laid-diagram-full relative size-full bg-canvas">
      <Canvas diagram={diagram} step={step} selected={selected} onSelect={onSelect} full resolvedTheme={resolvedTheme}>
        <Panel position="bottom-center" className="w-[min(640px,calc(100%-24px))]">
          <DiagramStepBar {...walk} steps={diagram.steps} active={step} onGo={onStep} baseDir={site().config.docs.diagrams} stepRef={site().stepRef} />
        </Panel>
      </Canvas>
    </div>
  )
}

export default HandLaidFigure
