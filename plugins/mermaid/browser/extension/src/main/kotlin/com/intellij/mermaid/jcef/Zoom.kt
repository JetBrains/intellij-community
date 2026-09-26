package com.intellij.mermaid.jcef

import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.dom.Element
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.min

private const val WRAPPER_CLASS = "mermaid-wrapper"
private const val CONTROLS_CLASS = "mermaid-zoom"
private const val CONTROLS_HIDDEN_CLASS = "mermaid-zoom-hidden"
private const val LEVEL_CLASS = "mermaid-zoom-level"
private const val BUTTON_CLASS = "mermaid-zoom-button"
private const val ZOOM_OUT_CLASS = "mermaid-zoom-out"
private const val ZOOM_IN_CLASS = "mermaid-zoom-in"
private const val ZOOM_RESET_CLASS = "mermaid-zoom-reset"

private val zoomLadder = listOf(25, 33, 50, 67, 75, 100, 125, 150, 200, 300, 400, 600, 800)

const val DEFAULT_ZOOM: Int = 100

private val blockToZoom = mutableMapOf<Element, Int>()

private var lastBlockCount = -1

fun zoomOf(block: Element): Int = blockToZoom[block] ?: DEFAULT_ZOOM

fun isZoomed(block: Element): Boolean = zoomOf(block) != DEFAULT_ZOOM

fun canZoom(block: Element, direction: Int): Boolean =
  naturalWidthOf(block) != null && nextZoom(zoomOf(block), direction) != null

fun stepZoom(block: Element, direction: Int) {
  setZoom(block, nextZoom(zoomOf(block), direction) ?: return)
}

fun setZoom(block: Element, percent: Int) {
  if (naturalWidthOf(block) == null) return
  val current = zoomOf(block)
  val target = percent.coerceIn(zoomLadder.first(), zoomLadder.last())
  val centre = block.scrollCentre()
  if (target == DEFAULT_ZOOM) blockToZoom.remove(block) else blockToZoom[block] = target
  applyZoom(block)
  block.restoreScrollCentre(centre, scale = target.toDouble() / current)
}

internal data class ScrollCentre(val x: Double, val y: Double)

internal fun Element.scrollCentre(): ScrollCentre =
  ScrollCentre(scrollLeft + clientWidth / 2.0, scrollTop + clientHeight / 2.0)

internal fun Element.restoreScrollCentre(centre: ScrollCentre, scale: Double = 1.0) {
  scrollLeft = centre.x * scale - clientWidth / 2.0
  scrollTop = centre.y * scale - clientHeight / 2.0
}

fun resetZoom(block: Element) {
  blockToZoom.remove(block)
}

fun forgetZoomIfBlockSetChanged(blockCount: Int) {
  if (blockCount != lastBlockCount) {
    lastBlockCount = blockCount
    blockToZoom.clear()
  }
}

fun applyZoom(block: Element) {
  val wrapper = block.parentElement
  val zoom = zoomOf(block)
  val natural = naturalWidthOf(block)
  if (zoom == DEFAULT_ZOOM || natural == null) {
    natural?.let { block.findSvgElement()?.setAttribute("style", "max-width: ${it}px;") }
    wrapper?.removeAttribute("data-zoomed")
  }
  else {
    block.findSvgElement()?.setAttribute("style", "width: ${fittedWidthOf(block, natural) * zoom / 100.0}px; max-width: none;")
    wrapper?.setAttribute("data-zoomed", "")
  }
  refreshZoomControls(block)
}

private fun refreshZoomControls(block: Element) {
  val controls = block.parentElement?.querySelector(".$CONTROLS_CLASS") ?: return
  val usable = naturalWidthOf(block) != null && block.querySelector(".error-text") == null
  controls.classList.toggle(CONTROLS_HIDDEN_CLASS, !usable)
  val level = controls.querySelector(".$LEVEL_CLASS") as? HTMLInputElement
  if (level != null && level != window.document.activeElement) {
    level.value = "${zoomOf(block)}%"
  }
  controls.querySelector(".$ZOOM_IN_CLASS")?.setEnabled(canZoom(block, 1))
  controls.querySelector(".$ZOOM_OUT_CLASS")?.setEnabled(canZoom(block, -1))
  controls.querySelector(".$ZOOM_RESET_CLASS")?.setEnabled(isZoomed(block))
}

private fun Element.setEnabled(enabled: Boolean) {
  if (enabled) removeAttribute("disabled") else setAttribute("disabled", "")
}

fun zoomResetRequests(): Flow<Element> = callbackFlow {
  window.addEventListener("click") { event ->
    val clicked = event.target as? Element ?: return@addEventListener
    val button = clicked.parents(withSelf = true).firstOrNull { it.classList.contains(BUTTON_CLASS) }
                 ?: return@addEventListener
    val block = button.diagramBlock() ?: return@addEventListener
    event.preventDefault()
    when {
      button.classList.contains(ZOOM_IN_CLASS) -> stepZoom(block, 1)
      button.classList.contains(ZOOM_OUT_CLASS) -> stepZoom(block, -1)
      else -> trySend(block)
    }
  }
  awaitCancellation()
}

fun installZoomLevelEditing() {
  window.addEventListener("change") { event ->
    val input = event.target as? HTMLInputElement ?: return@addEventListener
    if (!input.classList.contains(LEVEL_CLASS)) return@addEventListener
    val block = input.diagramBlock() ?: return@addEventListener
    input.value.filter { it.isDigit() }.toIntOrNull()?.let { setZoom(block, it) } ?: applyZoom(block)
  }
  window.addEventListener("keydown") { event ->
    val input = event.target as? HTMLInputElement ?: return@addEventListener
    if (!input.classList.contains(LEVEL_CLASS)) return@addEventListener
    when ((event as KeyboardEvent).key) {
      "Enter" -> input.blur()
      "Escape" -> {
        input.diagramBlock()?.let { input.value = "${zoomOf(it)}%" }
        input.blur()
      }
    }
  }
}

private fun Element.diagramBlock(): Element? =
  parents(withSelf = false)
    .firstOrNull { it.classList.contains(WRAPPER_CLASS) }
    ?.querySelector(".mermaid")

fun shouldRefitAfterResize(block: Element, widened: Boolean): Boolean {
  if (!isZoomed(block)) return true
  if (!widened) return false
  if (refittingWouldShrink(block)) return false
  resetZoom(block)
  return true
}

private fun refittingWouldShrink(block: Element): Boolean = zoomOf(block) > DEFAULT_ZOOM

private fun naturalWidthOf(block: Element): Double? =
  block.findSvgElement()?.getAttribute("data-natural-width")?.toDoubleOrNull()

private fun fittedWidthOf(block: Element, natural: Double): Double =
  min((block.parentElement ?: block).clientWidth.toDouble(), natural)

private fun nextZoom(current: Int, direction: Int): Int? =
  if (direction > 0) zoomLadder.firstOrNull { it > current } else zoomLadder.lastOrNull { it < current }
