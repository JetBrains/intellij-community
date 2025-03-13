package org.intellij.plugins.markdown.frontend.preview.accessor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownLinkOpener
import org.intellij.plugins.markdown.ui.preview.accessor.impl.MarkdownLinkOpenerImpl

class DelegatingMarkdownLinkOpener : MarkdownLinkOpener {
  private val oldImplementation: MarkdownLinkOpenerImpl by lazy {
    service<MarkdownLinkOpenerImpl>()
  }
  private val newImplementation: org.intellij.plugins.markdown.frontend.preview.accessor.impl.MarkdownLinkOpenerImpl by lazy {
    service<org.intellij.plugins.markdown.frontend.preview.accessor.impl.MarkdownLinkOpenerImpl>()
  }

  private val useFallbackLinkOpener: Boolean = Registry.`is`("markdown.use.fallback.link.opener", false)

  override fun openLink(project: Project?, link: String, virtualFile: VirtualFile?) {
    if (useFallbackLinkOpener) {
      oldImplementation.openLink(project, link, virtualFile)
    } else {
      newImplementation.openLink(project, link, virtualFile)
    }
  }

  override fun isSafeLink(project: Project?, link: String): Boolean {
    return if (useFallbackLinkOpener) {
      oldImplementation.isSafeLink(project, link)
    } else {
      newImplementation.isSafeLink(project, link)
    }
  }
}