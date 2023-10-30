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

  fun coroutineScope(): CoroutineScope {
    return coroutineScope
  }

  companion object {
    /**
     * Be aware, that you should not try to obtain the scope
     * when the plugin is unloading or the service is already disposed.
     *
     * Use `serviceOrNull<MermaidPlugin>()` in sensitive places.
     */
    fun coroutineScope(project: Project): CoroutineScope {
      return project.service<MermaidPlugin>().coroutineScope
    }
  }
}
