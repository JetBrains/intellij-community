package com.intellij.mermaid.jcef

import kotlinx.browser.window
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import org.w3c.dom.DOMRectReadOnly
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.js.Console
import kotlin.js.Promise
import kotlin.math.abs

const val RESIZE_DEBOUNCE_MS = 200L

// Ignore sub-pixel/jitter resizes; only react to a meaningful width change (either direction).
const val MIN_WIDTH_CHANGE = 8.0

external interface ResizeObserverEntry {
  val contentRect: DOMRectReadOnly
}

external class ResizeObserver(callback: (entries: Array<ResizeObserverEntry>, observer: ResizeObserver) -> Unit) {
  fun observe(target: Element)
  fun disconnect()
}

/**
 * Emits when `<body>`'s width changes (debounced, either direction). Used by the markdown extension
 * to re-render diagrams, whose SVGs are pinned to their render-time `max-width`. A [ResizeObserver]
 * is used rather than a window "resize" event, which an off-screen browser may not deliver.
 */
@OptIn(FlowPreview::class)
fun bodyWidthChangeEvents(): Flow<Unit> = callbackFlow {
  val target = window.document.body ?: window.document.documentElement ?: return@callbackFlow
  var lastWidth = -1.0
  val observer = ResizeObserver { entries, _ ->
    val width = entries.firstOrNull()?.contentRect?.width ?: return@ResizeObserver
    if (lastWidth < 0.0) {
      lastWidth = width // first callback is the initial size; nothing to re-render yet
    }
    else if (abs(width - lastWidth) >= MIN_WIDTH_CHANGE) {
      lastWidth = width
      trySend(Unit)
    }
  }
  observer.observe(target)
  awaitClose { observer.disconnect() }
}.debounce(RESIZE_DEBOUNCE_MS)

inline fun <T> promise(crossinline block: ((T) -> Unit) -> Unit): Promise<T> {
  return Promise { resolve, _ -> block(resolve)  }
}

inline fun Console.time(name: String) {
  asDynamic().time(name)
}

inline fun Console.timeEnd(name: String) {
  asDynamic().timeEnd(name)
}

fun EventTarget.addEventListener(type: String, callback: (Event) -> Unit) {
  addEventListener(type, callback)
}

fun <T: Event> EventTarget.eventFlowOf(type: String): Flow<T> {
  return callbackFlow {
    addEventListener(type) { event ->
      trySend(event.unsafeCast<T>())
    }
    awaitCancellation()
  }
}

internal fun Element.parents(withSelf: Boolean): Sequence<Element> {
  return sequence {
    if (withSelf) {
      yield(this@parents)
    }
    var parent = parentElement
    while (parent != null && parent != undefined) {
      yield(parent)
      parent = parent.parentElement
    }
  }
}
