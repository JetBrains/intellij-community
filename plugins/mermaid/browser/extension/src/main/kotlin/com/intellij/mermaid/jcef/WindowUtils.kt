package com.intellij.mermaid.jcef

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.js.Console
import kotlin.js.Promise

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
