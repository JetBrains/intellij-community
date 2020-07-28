// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.javafx

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension

interface MarkdownJavaFXPreviewExtension : MarkdownBrowserPreviewExtension {
  companion object {
    @JvmStatic
    val all: List<MarkdownJavaFXPreviewExtension> =
      MarkdownBrowserPreviewExtension.all.filterIsInstance<MarkdownJavaFXPreviewExtension>()

    @JvmStatic
    val allSorted: List<MarkdownJavaFXPreviewExtension> =
      MarkdownBrowserPreviewExtension.allSorted.filterIsInstance<MarkdownJavaFXPreviewExtension>()
  }
}
