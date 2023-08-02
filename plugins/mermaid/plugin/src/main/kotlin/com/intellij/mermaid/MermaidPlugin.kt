package com.intellij.mermaid

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

@Service
internal class MermaidPlugin(val coroutineScope: CoroutineScope): Disposable {
  override fun dispose() {
    coroutineScope.cancel()
  }
}
