package org.intellij.plugins.markdown.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class MarkdownPluginScope(private val coroutineScope: CoroutineScope) {
  companion object {
    fun createChildScope(project: Project): CoroutineScope {
      return scope(project).childScope()
    }

    fun scope(project: Project): CoroutineScope {
      return project.service<MarkdownPluginScope>().coroutineScope
    }
  }
}
