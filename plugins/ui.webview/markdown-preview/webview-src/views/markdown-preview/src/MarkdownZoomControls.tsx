// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { AllIcons } from "@jetbrains/intellij-webview"
import { classNames } from "./markdownReactUtils"

export const MARKDOWN_ZOOM_SCALE_EXTENT: [number, number] = [0.25, 4]
export const MARKDOWN_ZOOM_BUTTON_FACTOR = 1.2

interface MarkdownZoomToolbarProps {
  targetLabel: string
  className?: string
  buttonClassName?: string
  onZoomOut: () => void
  onResetZoom: () => void
  onZoomIn: () => void
}

export function MarkdownZoomToolbar({ targetLabel, className, buttonClassName, onZoomOut, onResetZoom, onZoomIn }: MarkdownZoomToolbarProps) {
  const normalizedTargetLabel = targetLabel.toLowerCase()
  const accessibleTargetLabel = `${normalizedTargetLabel.charAt(0).toUpperCase()}${normalizedTargetLabel.slice(1)}`
  const buttonClass = classNames("markdownZoomToolbarButton", buttonClassName)

  return (
    <div className={classNames("markdownZoomToolbar", className)} aria-label={`${accessibleTargetLabel} zoom controls`}>
      <button type="button" className={buttonClass} aria-label={`Zoom out ${normalizedTargetLabel}`} title="Zoom out" onClick={onZoomOut}>
        <img src={AllIcons.src("graph/zoomOut.svg")} alt="" draggable={false} />
      </button>
      <button type="button" className={buttonClass} aria-label={`Reset ${normalizedTargetLabel} zoom`} title="Reset zoom" onClick={onResetZoom}>
        <img src={AllIcons.src("general/reset.svg")} alt="" draggable={false} />
      </button>
      <button type="button" className={buttonClass} aria-label={`Zoom in ${normalizedTargetLabel}`} title="Zoom in" onClick={onZoomIn}>
        <img src={AllIcons.src("graph/zoomIn.svg")} alt="" draggable={false} />
      </button>
    </div>
  )
}

export function shouldHandleZoomEvent(event: Event): boolean {
  // Chromium reports trackpad pinch as a ctrlKey wheel event; plain wheel must keep page scrolling.
  return event.type !== "wheel" || (event as WheelEvent).ctrlKey
}
