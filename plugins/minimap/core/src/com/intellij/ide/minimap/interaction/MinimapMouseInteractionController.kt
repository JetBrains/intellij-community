// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.interaction

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.hover.MinimapHoverController
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class MinimapMouseInteractionController(
  private val panel: MinimapPanel,
  private val hoverController: MinimapHoverController
) : MouseAdapter(), Disposable {
  private enum class MinimapMouseInteractionState { IDLE, DRAGGING }

  private val editor = panel.editor
  private var interactionState: MinimapMouseInteractionState = MinimapMouseInteractionState.IDLE
  private var dragAnimationDisabled = false
  private var dragOffset = 0
  private var dragStartY = 0
  private var dragDistancePx = 0
  private var independentDragLastY = 0
  private var independentWheelRemainderPx = 0.0
  private var lastScrollLogTimeMs: Long = 0

  fun install() {
    panel.addMouseListener(this)
    panel.addMouseWheelListener(this)
    panel.addMouseMotionListener(this)
  }

  override fun dispose() {
    if (dragAnimationDisabled) {
      editor.scrollingModel.enableAnimation()
      dragAnimationDisabled = false
    }

    interactionState = MinimapMouseInteractionState.IDLE
    dragOffset = 0
    dragStartY = 0
    dragDistancePx = 0
    independentDragLastY = 0
    independentWheelRemainderPx = 0.0
    lastScrollLogTimeMs = 0

    panel.removeMouseListener(this)
    panel.removeMouseWheelListener(this)
    panel.removeMouseMotionListener(this)
  }

  override fun mousePressed(e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return

    if (panel.isIndependentScrollEnabled()) {
      interactionState = MinimapMouseInteractionState.DRAGGING
      hoverController.startDragging()
      dragAnimationDisabled = false
      dragOffset = 0
      dragStartY = e.y
      dragDistancePx = 0
      independentDragLastY = e.y
      independentWheelRemainderPx = 0.0
      return
    }

    val geometry = panel.currentSnapshot()?.geometry
    if (geometry == null || geometry.thumbHeight <= 0 || geometry.minimapHeight <= 0) {
      interactionState = MinimapMouseInteractionState.IDLE
      dragOffset = 0
      dragStartY = 0
      dragDistancePx = 0
      independentDragLastY = 0
      independentWheelRemainderPx = 0.0
      return
    }

    interactionState = MinimapMouseInteractionState.DRAGGING
    hoverController.startDragging()
    dragAnimationDisabled = false
    dragStartY = e.y
    dragDistancePx = 0
    independentDragLastY = 0
    independentWheelRemainderPx = 0.0

    val thumbTop = geometry.thumbStart - geometry.areaStart
    val thumbBottom = thumbTop + geometry.thumbHeight

    dragOffset = if (e.y in thumbTop until thumbBottom) {
      e.y - thumbTop
    }
    else {
      geometry.thumbHeight / 2
    }
  }

  override fun mouseReleased(e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return

    val wasDragging = interactionState == MinimapMouseInteractionState.DRAGGING
    if (interactionState == MinimapMouseInteractionState.DRAGGING &&
        dragDistancePx > 0 &&
        MinimapInteractionPolicy.isGenericInteractionLoggingEnabled(editor)) {
      logDragged()
    }

    if (dragAnimationDisabled) {
      editor.scrollingModel.enableAnimation()
      dragAnimationDisabled = false
    }

    interactionState = MinimapMouseInteractionState.IDLE
    dragOffset = 0
    dragStartY = 0
    dragDistancePx = 0
    independentDragLastY = 0
    independentWheelRemainderPx = 0.0
    if (wasDragging) {
      hoverController.stopDragging(e.point)
    }
  }

  override fun mouseWheelMoved(mouseWheelEvent: MouseWheelEvent) {
    val preciseWheelRotation = mouseWheelEvent.preciseWheelRotation
    if (preciseWheelRotation == 0.0) return
    val direction = if (preciseWheelRotation > 0) MinimapUsageCollector.ScrollDirection.DOWN else MinimapUsageCollector.ScrollDirection.UP
    if (shouldLogWheelScroll()) {
      val interactionPolicy = MinimapInteractionPolicy.forEditor(editor)
      if (interactionPolicy.isGenericInteractionLoggingEnabled(editor)) {
        logWheelScrolled(direction)
      }
      interactionPolicy.onWheelScrolled(panel, direction)
    }

    if (panel.isIndependentScrollEnabled()) {
      val independentDeltaPx = independentWheelDeltaPx(preciseWheelRotation)
      if (independentDeltaPx != 0) {
        panel.scrollIndependentViewportBy(independentDeltaPx)
        hoverController.onScroll(mouseWheelEvent.point)
      }
      return
    }

    val deltaPx = (preciseWheelRotation * editor.lineHeight * WHEEL_SCROLL_LINES).toInt()
    editor.scrollingModel.scrollVertically(
      editor.scrollingModel.verticalScrollOffset + deltaPx)
    hoverController.onScroll(mouseWheelEvent.point)
  }

  override fun mouseDragged(e: MouseEvent) {
    if (interactionState != MinimapMouseInteractionState.DRAGGING) return

    if (panel.isIndependentScrollEnabled()) {
      dragDistancePx = max(dragDistancePx, abs(e.y - dragStartY))
      val deltaPx = e.y - independentDragLastY
      independentDragLastY = e.y
      panel.scrollIndependentViewportBy(deltaPx)
      return
    }

    if (!dragAnimationDisabled) {
      editor.scrollingModel.disableAnimation()
      dragAnimationDisabled = true
    }
    dragDistancePx = max(dragDistancePx, abs(e.y - dragStartY))

    panel.scrollThumbTo(e.y, dragOffset)
  }

  override fun mouseClicked(e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return
    if (MinimapInteractionPolicy.isGenericInteractionLoggingEnabled(editor)) {
      logClicked()
    }
    handleClick(e)
  }

  override fun mouseEntered(e: MouseEvent) {
    panel.isMouseOver = true
    hoverController.onMouseEntered()
    panel.repaint()
  }

  override fun mouseMoved(e: MouseEvent) {
    panel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    if (MinimapInteractionPolicy.handleMouseMoved(panel, e)) return
    updateHover(e.point)
  }

  override fun mouseExited(e: MouseEvent) {
    panel.isMouseOver = false
    panel.repaint()
    if (MinimapInteractionPolicy.handleMouseExited(panel, e)) return
    hoverController.onMouseExited()
  }

  private fun updateHover(point: Point?) {
    hoverController.updateHover(point)
  }

  private fun handleClick(e: MouseEvent) {
    // TODO: if clicked on structure view element -> scroll to the element
    if (MinimapInteractionPolicy.handleClick(panel, e)) return
    if (panel.settings.state.rightAligned && tryDispatchToEditorInlay(e)) return
    panel.scrollTo(e.y)
  }

  /**
   * Converts a minimap click to editor content coordinates and dispatches it to the inlay at
   * that position, if one exists. Returns `true` if the event was consumed by an inlay.
   */
  private fun tryDispatchToEditorInlay(e: MouseEvent): Boolean {
    val contentComponent = editor.contentComponent
    val editorPoint = SwingUtilities.convertPoint(panel, e.point, contentComponent)
    val inlay = editor.inlayModel.getElementAt(editorPoint) ?: return false
    val renderer = inlay.renderer
    if (renderer !is InputHandler) return false
    val bounds = inlay.getBounds() ?: return false
    val translated = Point(editorPoint.x - bounds.x, editorPoint.y - bounds.y)
    val syntheticEvent = MouseEvent(
      contentComponent,
      e.id, e.`when`, e.modifiersEx,
      editorPoint.x, editorPoint.y,
      e.clickCount, false, e.button
    )
    renderer.mouseMoved(syntheticEvent, translated)
    renderer.mouseReleased(syntheticEvent, translated)
    renderer.mouseExited()
    return true
  }

  private fun fileType(): FileType? = FileDocumentManager.getInstance().getFile(editor.document)?.fileType

  private fun shouldLogWheelScroll(): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastScrollLogTimeMs >= SCROLL_LOG_COOLDOWN_MS) {
      lastScrollLogTimeMs = now
      return true
    }
    return false
  }

  private fun logClicked() {
    val settings = panel.settings.state
    MinimapUsageCollector.logClicked(
      scaleMode = settings.scaleMode,
      rightAligned = settings.rightAligned,
      fileType = fileType(),
    )
  }

  private fun logDragged() {
    val settings = panel.settings.state
    MinimapUsageCollector.logDragged(
      scaleMode = settings.scaleMode,
      rightAligned = settings.rightAligned,
      dragDistanceBucket = MinimapUsageCollector.toDragDistanceBucket(dragDistancePx),
      fileType = fileType(),
    )
  }

  private fun logWheelScrolled(direction: MinimapUsageCollector.ScrollDirection) {
    val settings = panel.settings.state
    MinimapUsageCollector.logWheelScrolled(
      scaleMode = settings.scaleMode,
      direction = direction,
      fileType = fileType(),
    )
  }

  private fun independentWheelDeltaPx(preciseWheelRotation: Double): Int {
    val sensitivity = MinimapInteractionPolicy.forEditor(editor).independentWheelSensitivity()
    val scaledDeltaPx = preciseWheelRotation * editor.lineHeight * WHEEL_SCROLL_LINES * sensitivity  // maybe squared sensitivity?
    val accumulatedDeltaPx = scaledDeltaPx + independentWheelRemainderPx
    val wholeDeltaPx = if (accumulatedDeltaPx > 0) {
      floor(accumulatedDeltaPx).toInt()
    }
    else {
      ceil(accumulatedDeltaPx).toInt()
    }
    independentWheelRemainderPx = accumulatedDeltaPx - wholeDeltaPx
    return wholeDeltaPx
  }

  companion object {
    private const val WHEEL_SCROLL_LINES: Int = 10
    private const val SCROLL_LOG_COOLDOWN_MS: Long = 10_000
  }
}
