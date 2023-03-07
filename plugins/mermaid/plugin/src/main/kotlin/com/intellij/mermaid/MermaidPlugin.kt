package com.intellij.mermaid

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.EmptyCoroutineContext

// TODO: Replace with service constructor injection after 231
@Service
internal class MermaidPlugin: Disposable {
  val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun dispose() {
    coroutineScope.cancel()
  }
}
