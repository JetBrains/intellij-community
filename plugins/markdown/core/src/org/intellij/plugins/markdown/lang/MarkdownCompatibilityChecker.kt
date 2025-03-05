// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Provides support for languages that either are Markdown or are compatible with Markdown-like features.
 * This interface is used to determine if a language should be treated as Markdown-supported.
 *
 * Example:
 * - MarkdownLanguage is the default Markdown implementation.
 * - Jupyter notebooks contain Markdown cells and are considered compatible.
 */
interface MarkdownCompatibilityChecker {
  companion object {
    val EP_NAME: ExtensionPointName<MarkdownCompatibilityChecker> =
      ExtensionPointName.create("org.intellij.markdown.markdownCompatibilityChecker")
  }

  fun isSupportedLanguage(language: Language): Boolean
}

class DefaultMarkdownCompatibilityChecker : MarkdownCompatibilityChecker {
  override fun isSupportedLanguage(language: Language): Boolean = language == MarkdownLanguage.INSTANCE
}
