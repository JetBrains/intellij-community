// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import org.intellij.plugins.markdown.ui.preview.ResourceProvider

/**
 * Base interface for browser-based preview extensions.
 */
interface MarkdownBrowserPreviewExtension : MarkdownExtension, Comparable<MarkdownBrowserPreviewExtension> {
  /**
   * The value on which the resource load order will be based on. Any non special extesnion
   * should use [Priority.DEFAULT] value.
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

  companion object {
    @JvmStatic
    val all: List<MarkdownBrowserPreviewExtension>
      get() = MarkdownExtension.all.filterIsInstance<MarkdownBrowserPreviewExtension>()

    @JvmStatic
    val allSorted: List<MarkdownBrowserPreviewExtension>
      get() = all.sorted()
  }
}
