// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface MarkdownCodeFenceRunner {
  companion object {
    val EP_NAME: ExtensionPointName<MarkdownCodeFenceRunner> = ExtensionPointName.create("org.intellij.markdown.codeFenceRunner")
    private val PROVIDER_EP_NAME = ExtensionPointName.create<Provider>("org.intellij.markdown.codeFenceRunnerProvider")

    fun findRunner(language: Language?): MarkdownCodeFenceRunner? {
      for (provider in PROVIDER_EP_NAME.extensionList) {
        provider.findRunner(language)?.let { return it }
      }
      return EP_NAME.extensionList.firstOrNull { it.isApplicable(language) }
    }
  }

  fun isApplicable(language: Language?): Boolean

  fun run(command: String, project: Project, context: MarkdownRunnerContext): Boolean

  @Nls
  fun title(): String

  interface Provider {
    fun findRunner(language: Language?): MarkdownCodeFenceRunner?
  }
}
