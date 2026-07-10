// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useRef, useState, type CSSProperties, type HTMLAttributes } from "react"
import { select } from "d3-selection"
import { zoom, zoomIdentity, type D3ZoomEvent, type ZoomBehavior } from "d3-zoom"
import { classNames } from "./markdownReactUtils"
import {
  MARKDOWN_ZOOM_BUTTON_FACTOR,
  MARKDOWN_ZOOM_SCALE_EXTENT,
  MarkdownZoomToolbar,
  shouldHandleZoomEvent,
} from "./MarkdownZoomControls"

interface MarkdownImageBlockProps extends HTMLAttributes<HTMLDivElement> {
  src: string
  alt?: string
  title?: string
}

export function MarkdownImageBlock({ src, alt, title, className, style, ...props }: MarkdownImageBlockProps) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const imageRef = useRef<HTMLImageElement>(null)
  const zoomBehaviorRef = useRef<ZoomBehavior<HTMLDivElement, unknown> | null>(null)
  const [aspectRatio, setAspectRatio] = useState<number | undefined>()

  useEffect(() => {
    const viewport = viewportRef.current
    const image = imageRef.current
    if (!viewport || !image) return

    const zoomBehavior = zoom<HTMLDivElement, unknown>()
      .filter(shouldHandleZoomEvent)
      .scaleExtent(MARKDOWN_ZOOM_SCALE_EXTENT)
      .on("zoom", (event: D3ZoomEvent<HTMLDivElement, unknown>) => {
        image.style.transform = `translate(${event.transform.x}px, ${event.transform.y}px) scale(${event.transform.k})`
      })
    zoomBehaviorRef.current = zoomBehavior

    const viewportSelection = select<HTMLDivElement, unknown>(viewport)
    viewportSelection.call(zoomBehavior)
    viewportSelection.call(zoomBehavior.transform, zoomIdentity)

    return () => {
      viewportSelection.on(".zoom", null)
      image.style.removeProperty("transform")
      zoomBehaviorRef.current = null
    }
  }, [src])

  useEffect(() => {
    updateAspectRatio()
  }, [src])

  function zoomBy(factor: number): void {
    const viewport = viewportRef.current
    const zoomBehavior = zoomBehaviorRef.current
    if (!viewport || !zoomBehavior) return
    select<HTMLDivElement, unknown>(viewport).call(zoomBehavior.scaleBy, factor)
  }

  function resetZoom(): void {
    const viewport = viewportRef.current
    const zoomBehavior = zoomBehaviorRef.current
    if (!viewport || !zoomBehavior) return
    select<HTMLDivElement, unknown>(viewport).call(zoomBehavior.transform, zoomIdentity)
  }

  function updateAspectRatio(): void {
    const image = imageRef.current
    const width = image?.naturalWidth ?? 0
    const height = image?.naturalHeight ?? 0
    setAspectRatio(width > 0 && height > 0 ? width / height : undefined)
  }

  const blockStyle = aspectRatio === undefined
    ? style
    : ({ ...style, "--markdown-image-aspect-ratio": String(aspectRatio) } as CSSProperties)

  return (
    <div {...props} className={classNames("markdownImageBlock", "isInteractive", className)} style={blockStyle}>
      <div className="markdownImageViewport" ref={viewportRef}>
        <img className="markdownImage" ref={imageRef} src={src} alt={alt ?? ""} title={title} draggable={false} onLoad={updateAspectRatio} />
      </div>
      <MarkdownZoomToolbar
        targetLabel="image"
        className="markdownImageToolbar"
        buttonClassName="markdownImageToolbarButton"
        onZoomOut={() => zoomBy(1 / MARKDOWN_ZOOM_BUTTON_FACTOR)}
        onResetZoom={resetZoom}
        onZoomIn={() => zoomBy(MARKDOWN_ZOOM_BUTTON_FACTOR)}
      />
    </div>
  )
}
