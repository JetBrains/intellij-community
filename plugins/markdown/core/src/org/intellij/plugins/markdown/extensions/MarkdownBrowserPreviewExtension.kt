// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

/**
 * Base interface for browser-based preview extensions.
 */
interface MarkdownBrowserPreviewExtension: Comparable<MarkdownBrowserPreviewExtension>, Disposable {
  /**
   * The value on which the resource load order will be based on. Any non-special extension should use [Priority.DEFAULT] value.
   */
  enum class Priority(val value: Int) {
    BEFORE_ALL(4),
    HIGH(3),
    DEFAULT(2),
    LOW(1),
    AFTER_ALL(0)
  }

  /**
   * See [Priority]
   */
  val priority: Priority
    get() = Priority.DEFAULT

  /**
   * List of available scripts names (can be paths), that will be used to build CSP.
   */
  val scripts: List<String>
    get() = emptyList()

  /**
   * List of available styles names (can be paths), that will be used to build CSP.
   */
  val styles: List<String>
    get() = emptyList()

  /**
   * Dedicated [ResourceProvider] for current extension.
   */
  val resourceProvider: ResourceProvider
    get() = ResourceProvider.default

  override fun compareTo(other: MarkdownBrowserPreviewExtension): Int {
    return priority.value.compareTo(other.priority.value).inv()
  }

  fun interface Provider {
    fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension?

    companion object {
      val EP = ExtensionPointName<Provider>("org.intellij.markdown.browserPreviewExtensionProvider")

      val all: List<Provider>
        get() = EP.extensionList
    }
  }
}
