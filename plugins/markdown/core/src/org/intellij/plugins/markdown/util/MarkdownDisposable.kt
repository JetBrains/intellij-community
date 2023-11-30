package org.intellij.plugins.markdown.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MarkdownDisposable: Disposable {
  override fun dispose() = Unit

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MarkdownDisposable = project.service()
  }
}
