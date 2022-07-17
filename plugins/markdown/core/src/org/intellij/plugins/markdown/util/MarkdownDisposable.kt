package org.intellij.plugins.markdown.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service
class MarkdownDisposable: Disposable {
  override fun dispose() = Unit

  companion object {
    @JvmStatic
    @JvmOverloads
    fun getInstance(project: Project? = null): MarkdownDisposable {
      return when (project) {
        null -> service()
        else -> project.service()
      }
    }
  }
}
