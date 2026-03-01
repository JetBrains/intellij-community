// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.hyperlinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Allows custom handling of terminal hyperlink navigation.
 *
 * Interceptors are consulted before default hyperlink handling in the terminal backend.
 * The first interceptor returning `true` wins.
 */
@ApiStatus.Internal
interface TerminalHyperlinkNavigationInterceptor {
  /**
   * @return `true` when navigation is fully handled and terminal should skip default handling.
   */
  suspend fun intercept(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TerminalHyperlinkNavigationInterceptor> =
      ExtensionPointName.create("org.jetbrains.plugins.terminal.hyperlinkNavigationInterceptor")

    suspend fun intercept(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?): Boolean {
      for (interceptor in EP_NAME.extensionList) {
        val handled = try {
          interceptor.intercept(project, hyperlinkInfo, mouseEvent)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          LOG.warn("Terminal hyperlink interceptor failed: ${interceptor::class.java.name}", t)
          false
        }
        if (handled) {
          return true
        }
      }
      return false
    }
  }
}

private val LOG = logger<TerminalHyperlinkNavigationInterceptor>()
