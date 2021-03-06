// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension

/**
 * Extension for the JCEF-based browser preview.
 */
interface MarkdownJCEFPreviewExtension : MarkdownBrowserPreviewExtension {
  /**
   * Map of browser events that should be registered and it's IDE callbacks.
   */
  val events: Map<String, (String) -> Unit>
    get() = emptyMap()

  companion object {
    @JvmStatic
    val all: List<MarkdownJCEFPreviewExtension> =
      MarkdownBrowserPreviewExtension.all.filterIsInstance<MarkdownJCEFPreviewExtension>()

    @JvmStatic
    val allSorted: List<MarkdownJCEFPreviewExtension> =
      MarkdownBrowserPreviewExtension.allSorted.filterIsInstance<MarkdownJCEFPreviewExtension>()
  }
}
