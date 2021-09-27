// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MarkdownExtensionsUtil {
  fun collectConfigurableExtensions(enabledOnly: Boolean = false): Set<MarkdownConfigurableExtension> {
    val baseExtensions = MarkdownExtension.all.filterIsInstance<MarkdownConfigurableExtension>().toMutableSet()
    baseExtensions.addAll(MarkdownBrowserPreviewExtension.Provider.all.filterIsInstance<MarkdownConfigurableExtension>())
    return when {
      enabledOnly -> baseExtensions.filter { it.isEnabled }.toSet()
      else -> baseExtensions
    }
  }

  inline fun <reified T> findBrowserExtensionProvider(): T? {
    return MarkdownBrowserPreviewExtension.Provider.all.find { it is T } as? T
  }
}
