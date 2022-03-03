package org.intellij.plugins.markdown.ui.preview.accessor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Application level service to provide an implementation for opening links from Markdown document preview.
 */
interface MarkdownLinkOpener {
  /**
   * Will try to open a specified [link] from Markdown document.
   * If the specified [link] is unsafe ([isSafeLink] == false), the user will be prompted with
   * a confirmation dialog before actually trying to navigate to this link.
   *
   * The confirmation dialog will show "Do not ask again" checkbox if [project] is not null.
   *
   * If there is no application to handle specified link, method will *fail silently*.
   * If the specified link could not be opened for some other reason, will show a balloon notification.
   *
   * Note: it is possible to add custom url handler with [com.intellij.ide.browsers.UrlOpener] EP.
   */
  fun openLink(project: Project?, link: String)

  fun isSafeLink(project: Project?, link: String): Boolean

  companion object {
    @JvmStatic
    fun getInstance(): MarkdownLinkOpener {
      return service()
    }
  }
}
