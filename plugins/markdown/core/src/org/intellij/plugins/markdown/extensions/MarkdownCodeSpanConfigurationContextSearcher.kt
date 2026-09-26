// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface MarkdownCodeSpanConfigurationContextSearcher {
  companion object {
    internal val EP_NAME: ExtensionPointName<MarkdownCodeSpanConfigurationContextSearcher> = ExtensionPointName.create("org.intellij.markdown.codeSpanRunnableSearcher")

    @JvmStatic
    fun findAllConfigurations(runnableName: String, host: PsiElement): List<ConfigurationContext> {
      val elements = mutableListOf<ConfigurationContext>()
      DumbService.getInstance(host.project)
        .filterByDumbAwareness(EP_NAME.extensionList)
        .forEach {
          elements.addAll(it.findConfigurations(runnableName, host))
        }
      return elements
    }
  }

  fun findConfigurations(runnableName: String, host: PsiElement): List<ConfigurationContext>
}