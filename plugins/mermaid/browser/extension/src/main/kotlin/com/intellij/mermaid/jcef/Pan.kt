package com.intellij.mermaid.jcef

import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.events.MouseEvent
import kotlin.math.abs

private const val DRAG_THRESHOLD_PX = 4.0

private const val PANNING_CLASS = "mermaid-panning"
private const val BLOCK_CLASS = "mermaid"
private const val CONTROLS_CLASS = "mermaid-zoom"

private class Pan(
  val block: Element,
  val startX: Double,
  val startY: Double,
  val startScrollLeft: Double,
  val startScrollTop: Double,
) {
  var started: Boolean = false
}

private var activePan: Pan? = null
private var swallowNextClick = false

fun installDiagramPanning() {
  window.addEventListener("mousedown") { event -> beginPan(event as MouseEvent) }
  window.addEventListener("mousemove") { event -> continuePan(event as MouseEvent) }
  window.addEventListener("mouseup") { endPan() }
  window.addEventListener("dragstart") { event -> if (activePan != null) event.preventDefault() }
  window.addEventListener("click", { event ->
    if (swallowNextClick) {
      swallowNextClick = false
      event.stopPropagation()
      event.preventDefault()
    }
  }, true)
}

private fun beginPan(event: MouseEvent) {
  swallowNextClick = false
  if (event.button.toInt() != 0) return
  val target = event.target as? Element ?: return
  val ancestors = target.parents(withSelf = true)
  if (ancestors.any { it.classList.contains(CONTROLS_CLASS) }) return
  val block = ancestors.firstOrNull { it.classList.contains(BLOCK_CLASS) } ?: return
  if (!block.overflows()) return
  activePan = Pan(block, event.clientX.toDouble(), event.clientY.toDouble(), block.scrollLeft, block.scrollTop)
}

private fun continuePan(event: MouseEvent) {
  val pan = activePan ?: return
  if (event.buttons.toInt() and 1 == 0) {
    endPan()
    return
  }
  val dx = event.clientX.toDouble() - pan.startX
  val dy = event.clientY.toDouble() - pan.startY
  if (!pan.started) {
    if (abs(dx) < DRAG_THRESHOLD_PX && abs(dy) < DRAG_THRESHOLD_PX) return
    pan.started = true
    pan.block.classList.add(PANNING_CLASS)
  }
  pan.block.scrollLeft = pan.startScrollLeft - dx
  pan.block.scrollTop = pan.startScrollTop - dy
  event.preventDefault()
}

private fun endPan() {
  val pan = activePan ?: return
  activePan = null
  if (pan.started) {
    pan.block.classList.remove(PANNING_CLASS)
    swallowNextClick = true
  }
}

private fun Element.overflows(): Boolean = scrollWidth > clientWidth || scrollHeight > clientHeight
