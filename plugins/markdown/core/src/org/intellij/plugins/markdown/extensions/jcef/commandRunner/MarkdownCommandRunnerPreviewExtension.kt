// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.jetbrains.annotations.ApiStatus

/**
 * Adds run icons for commands to the Markdown preview.
 * The implementation is in the `intellij.markdown.execution` module.
 */
@ApiStatus.Internal
interface MarkdownCommandRunnerPreviewExtension : MarkdownBrowserPreviewExtension {
  fun resetCommands()

  fun processCodeLine(rawCodeLine: String, insideFence: Boolean): String

  fun processCodeBlock(codeFenceRawContent: String, language: String): String

  interface Provider : MarkdownBrowserPreviewExtension.Provider {
    fun getExtension(file: VirtualFile): MarkdownCommandRunnerPreviewExtension?
  }

  companion object {
    const val EXTENSION_ID: String = "MarkdownCommandRunnerExtension"

    fun isEnabled(): Boolean {
      return MarkdownExtensionsSettings.getInstance().extensionsEnabledState[EXTENSION_ID] ?: true
    }

    fun getByFile(file: VirtualFile): MarkdownCommandRunnerPreviewExtension? {
      val provider = MarkdownBrowserPreviewExtension.Provider.EP.extensionList.filterIsInstance<Provider>().firstOrNull()
      return provider?.getExtension(file)
    }
  }
}
