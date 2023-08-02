package com.intellij.mermaid

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

@Service
internal class MermaidPlugin(private val coroutineScope: CoroutineScope): Disposable {
  override fun dispose() {
    coroutineScope.cancel()
  }

  companion object {
    fun coroutineScope(project: Project): CoroutineScope {
      return project.service<MermaidPlugin>().coroutineScope
    }
  }
}
