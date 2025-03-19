package org.intellij.plugins.markdown.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
internal class MarkdownApplicationScope(private val coroutineScope: CoroutineScope) {
  companion object {
    fun createChildScope(): CoroutineScope {
      return scope().childScope()
    }

    fun scope(): CoroutineScope {
      return service<MarkdownApplicationScope>().coroutineScope
    }
  }
}
