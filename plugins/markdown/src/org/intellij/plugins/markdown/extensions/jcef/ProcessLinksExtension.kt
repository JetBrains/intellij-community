// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.jcef.JBCefPsiNavigationUtils
import org.intellij.plugins.markdown.ui.preview.accessor.MarkdownAccessor
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class ProcessLinksExtension : MarkdownJCEFPreviewExtension, ResourceProvider {
  private fun openLink(link: String) {
    if (!Registry.`is`("markdown.open.link.in.external.browser")) {
      return
    }
    if (JBCefPsiNavigationUtils.navigateTo(link)) {
      return
    }
    MarkdownAccessor.getSafeOpenerAccessor().openLink(link)
  }

  override val events: Map<String, (String) -> Unit> = mapOf("openLink" to this::openLink)

  override val scripts: List<String> = listOf("processLinks/processLinks.js")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in scripts

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return ResourceProvider.loadInternalResource(this::class, resourceName)
  }
}
