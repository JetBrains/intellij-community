package com.intellij.mermaid.jcef

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@JsName("IncrementalDOM")
external object IncrementalDom

fun IncrementalDom.addAfterPatchListener(listener: () -> Unit) {
  asDynamic().notifications.afterPatchListeners.push(listener)
}

fun IncrementalDom.afterPatchEvents(): Flow<Unit> {
  return callbackFlow {
    addAfterPatchListener {
      trySend(Unit)
    }
    awaitCancellation()
  }
}
