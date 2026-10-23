// The detail card of a clicked diagram card (ADR 0203 of Air): what the part does, where its code is, the
// specs and the decision behind it, the steps it appears in, and the other chapters that show it.
// One level deep, as the explainer checklist asks: no nested disclosure. The module, concept, and spec chips come
// from the reference links of the site (RefLinks); a site without them shows none.

import { adrNumberedTitle } from '../../../scripts/docMeta.ts'
import { SourceLink } from '../../components/SourceLink'
import { Badge } from '../../components/ui/badge'
import { Button } from '../../components/ui/button'
import type { HarvestedDiagram, HarvestedDiagramNode } from '../../../scripts/diagram.ts'
import { LayerBadge } from '../../components/LayerBadge'
import { ChipLink, ChipLinks } from '../../furniture'
import { kindStyles } from '../../graph/DiagramNode'
import { Markdown } from '../../markdown'
import { DetailPanelShell } from '../../panels/DetailPanelShell'
import { decisionRoute, explainerRoute } from '../../router'
import { site } from '../../site.ts'
import { guideSlug } from '../../siteData.ts'

/** The other chapters whose diagram shows the same symbol or module. */
function alsoIn(diagram: HarvestedDiagram, node: HarvestedDiagramNode): { href: string, title: string }[] {
  const matches: { href: string, title: string }[] = []
  const { diagramsById, explainerDocs } = site().index
  for (const doc of explainerDocs) {
    for (const chapter of doc.chapters) {
      if (chapter.diagram === null || chapter.diagram === diagram.id) {
        continue
      }
      const other = diagramsById.get(chapter.diagram)
      const twin = other?.nodes.find(candidate =>
        (node.symbol !== null && candidate.symbol === node.symbol) || (node.module !== null && candidate.module === node.module && node.kind === 'module'))
      if (other && twin) {
        matches.push({ href: explainerRoute(guideSlug(doc.path), chapter.id, { node: twin.id }), title: chapter.title })
      }
    }
  }
  return matches
}

export function NodeCard({ diagram, node, laneTitle, color, baseDir, onStep, onClose }: {
  diagram: HarvestedDiagram
  node: HarvestedDiagramNode
  laneTitle: string
  color: string
  baseDir: string
  onStep: (step: number) => void
  onClose: () => void
}) {
  const { config, index, refLinks } = site()
  const kindStyle = kindStyles(config.diagram)
  const plannedText = config.diagram.planned.find(phase => phase.id === node.planned)?.cardText
  const steps = diagram.steps.flatMap((step, index) => step.nodes.includes(node.id) ? [{ number: index + 1, title: step.title }] : [])
  const moduleLink = node.module === null ? null : refLinks.moduleLink?.(node.module) ?? null
  const spec = node.spec === null ? null : refLinks.specLink?.(node.spec) ?? null
  const concept = node.concept === null ? null : refLinks.conceptLink?.(node.concept) ?? null
  const adrDoc = node.adrPath === null ? undefined : index.docsByPath.get(node.adrPath)
  const related = alsoIn(diagram, node)
  const hasCode = node.symbolRef !== null || node.sourceRef !== null || node.targetRef !== undefined || moduleLink !== null || concept !== null
  return (
    <DetailPanelShell onClose={onClose}>
      <header className="node-card-header">
        <ChipLinks>
          <LayerBadge color={color}>{kindStyle(node.kind).label}</LayerBadge>
          <Badge variant="outline">{laneTitle}</Badge>
          {node.planned !== null && <Badge variant="secondary" className="uppercase tracking-[0.05em]">Planned</Badge>}
        </ChipLinks>
        <h2 className={node.kind === 'module' ? 'font-mono' : undefined}>{node.title}</h2>
        {node.text !== null && <p>{node.text}</p>}
      </header>
      {(node.detail !== null || node.planned !== null) && (
        <section className="node-card-detail">
          <h3>What it does</h3>
          {node.detail !== null && <Markdown source={node.detail} baseDir={baseDir} />}
          {node.planned !== null && <p className="m-0 mt-2 text-xs text-muted-foreground">{plannedText}</p>}
        </section>
      )}
      {hasCode && (
        <section className="node-card-code">
          <h3>In the code</h3>
          <div className="grid gap-1.5 text-xs">
            {node.symbolRef !== null && (
              <div><code>{node.symbol}</code> · <SourceLink file={node.symbolRef.file} line={node.symbolRef.line} /></div>
            )}
            {node.sourceRef !== null && <div><SourceLink file={node.sourceRef.file} line={node.sourceRef.line} /></div>}
            {node.targetRef !== undefined && (
              <div><code>{node.target}</code> · <SourceLink file={node.targetRef.file} line={node.targetRef.line} /></div>
            )}
            <ChipLinks>
              {moduleLink !== null && <ChipLink href={moduleLink.href}><code>{node.module}</code></ChipLink>}
              {concept !== null && <ChipLink href={concept.href}>{concept.title}</ChipLink>}
            </ChipLinks>
          </div>
        </section>
      )}
      {(spec !== null || adrDoc !== undefined) && (
        <section className="node-card-refs">
          <h3>Specs and decisions</h3>
          <ChipLinks>
            {spec !== null && <ChipLink href={spec.href}>{spec.title}</ChipLink>}
            {adrDoc !== undefined && node.adrPath !== null && <ChipLink href={decisionRoute(node.adrPath)}>{adrDoc.adr === undefined ? adrDoc.title : adrNumberedTitle(adrDoc.adr, adrDoc.title)}</ChipLink>}
          </ChipLinks>
        </section>
      )}
      {steps.length > 0 && (
        <section className="node-card-steps">
          <h3>In steps</h3>
          <div className="flex flex-wrap gap-1">
            {steps.map(step => (
              <Button key={step.number} variant="outline" size="sm" className="h-auto py-1 text-xs" onClick={() => onStep(step.number)}>
                <i className="mr-1 grid size-4 place-items-center rounded-full bg-primary text-3xs font-bold not-italic text-background">{step.number}</i>
                {step.title}
              </Button>
            ))}
          </div>
        </section>
      )}
      {related.length > 0 && (
        <section className="node-card-also">
          <h3>Also in</h3>
          <ChipLinks>
            {related.map(entry => <ChipLink key={entry.href} href={entry.href}>{entry.title}</ChipLink>)}
          </ChipLinks>
        </section>
      )}
    </DetailPanelShell>
  )
}
