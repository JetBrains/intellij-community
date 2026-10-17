// The one owner of an explainer page's route (ADR 0203 of Air, the pattern of its useFlowRoute). The URL holds the
// chapter, the step, and the selected node; every mutation writes it through `navigate`, and the
// keyboard walks the whole explainer with the arrow keys.

import { useEffect, useEffectEvent } from 'react'
import type { HarvestedDiagram } from '../../../scripts/diagram.ts'
import type { ExplainerChapter } from '../../../scripts/explainerDoc.ts'
import { ownsKeys } from '../../keys'
import { explainerRoute, navigate } from '../../router'
import { site } from '../../site.ts'
import { type ExplainerDoc, guideSlug } from '../../siteData.ts'

export interface ExplainerRouteInput {
  chapter: string | null
  step: number | null
  node: string | null
}

export interface ExplainerRouteModel {
  doc: ExplainerDoc
  chapters: ExplainerChapter[]
  chapter: ExplainerChapter
  index: number
  /** The chapter before this one, or null on the first chapter. */
  previous: ExplainerChapter | null
  /** The chapter after this one, or null on the last chapter. */
  next: ExplainerChapter | null
  /** The explainer after this one in the index order, or null after the last explainer. */
  nextExplainer: { href: string, title: string } | null
  diagram: HarvestedDiagram | null
  /** The 1-based active step, or null for the overview. */
  step: number | null
  /** The selected card id, or null. */
  node: string | null
  hrefOf: (chapter: ExplainerChapter) => string
  /** The number of steps of the chapter diagram; 0 for a chapter without a diagram. */
  stepsOf: (chapter: ExplainerChapter) => number
  goStep: (step: number | null) => void
  goNext: () => void
  goPrevious: () => void
  goChapter: (offset: number) => void
  select: (node: string | null) => void
}

/** The route of the explainer `slug`, or null for an unknown explainer. */
export function useExplainerRoute(slug: string, input: ExplainerRouteInput): ExplainerRouteModel | null {
  const { diagramsById, docForExplainerSlug, explainerDocs } = site().index
  const diagramOf = (target: ExplainerChapter): HarvestedDiagram | null => target.diagram === null ? null : diagramsById.get(target.diagram) ?? null
  const doc = docForExplainerSlug(slug)
  const chapters = doc?.chapters ?? []
  const index = Math.max(0, chapters.findIndex(candidate => candidate.id === input.chapter))
  const chapter = chapters[index] ?? null
  const diagram = chapter === null ? null : diagramOf(chapter)
  const total = diagram?.steps.length ?? 0
  const step = input.step !== null && input.step >= 1 && input.step <= total ? input.step : null
  const node = input.node !== null && diagram?.nodes.some(candidate => candidate.id === input.node) ? input.node : null

  // a bare or unknown chapter, step, or node becomes the canonical form (replace, so back stays clean)
  const canonicalHref = chapter === null ? null : explainerRoute(slug, chapter.id, { step, node })
  const inputHref = chapter === null ? null : explainerRoute(slug, input.chapter, { step: input.step, node: input.node })
  useEffect(() => {
    if (canonicalHref !== null && canonicalHref !== inputHref) {
      navigate(canonicalHref, { replace: true })
    }
  }, [canonicalHref, inputHref])

  const following = doc === undefined ? undefined : explainerDocs[explainerDocs.indexOf(doc) + 1]
  const go = (target: ExplainerChapter, next: number | null, selected: string | null = null): void => {
    navigate(explainerRoute(slug, target.id, { step: next, node: selected }))
  }
  const model: ExplainerRouteModel | null = doc === undefined || chapter === null
    ? null
    : {
        doc,
        chapters,
        chapter,
        index,
        previous: chapters[index - 1] ?? null,
        next: chapters[index + 1] ?? null,
        nextExplainer: following === undefined ? null : { href: explainerRoute(guideSlug(following.path)), title: following.title },
        diagram,
        step,
        node,
        hrefOf: target => explainerRoute(slug, target.id),
        stepsOf: target => diagramOf(target)?.steps.length ?? 0,
        goStep: next => go(chapter, next, node),
        goNext: () => {
          if (step !== null && step >= total) {
            // past the last step: the next chapter at its overview
            if (index + 1 < chapters.length) {
              go(chapters[index + 1], null)
            }
          }
          else if (total > 0) {
            go(chapter, (step ?? 0) + 1, node)
          }
          else if (index + 1 < chapters.length) {
            go(chapters[index + 1], null)
          }
        },
        goPrevious: () => {
          if (step !== null && step > 1) {
            go(chapter, step - 1, node)
          }
          else if (step !== null) {
            go(chapter, null, node)
          }
          else if (index > 0) {
            // before the first step: the previous chapter at its last step
            const previous = chapters[index - 1]
            const previousSteps = diagramOf(previous)?.steps.length ?? 0
            go(previous, previousSteps === 0 ? null : previousSteps)
          }
        },
        goChapter: (offset) => {
          const target = chapters[index + offset]
          if (target !== undefined) {
            go(target, null)
          }
        },
        select: selected => go(chapter, step, selected),
      }

  const onKeyDown = useEffectEvent((event: KeyboardEvent) => {
    if (model === null || ownsKeys(event.target) || event.metaKey || event.ctrlKey || event.altKey) {
      return
    }
    if (event.key === 'ArrowRight' && !event.shiftKey) {
      event.preventDefault()
      model.goNext()
    }
    else if (event.key === 'ArrowLeft' && !event.shiftKey) {
      event.preventDefault()
      model.goPrevious()
    }
    else if (event.key === 'PageDown' || (event.key === 'ArrowRight' && event.shiftKey)) {
      event.preventDefault()
      model.goChapter(1)
    }
    else if (event.key === 'PageUp' || (event.key === 'ArrowLeft' && event.shiftKey)) {
      event.preventDefault()
      model.goChapter(-1)
    }
    else if (/^[1-9]$/.test(event.key) && Number(event.key) <= total) {
      model.goStep(Number(event.key))
    }
    else if (event.key === '0') {
      model.goStep(null)
    }
    else if (event.key === 'Escape') {
      if (model.node !== null) {
        model.select(null)
      }
      else if (model.step !== null) {
        model.goStep(null)
      }
    }
  })
  useEffect(() => {
    if (doc === undefined) {
      return
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [doc])

  return model
}
