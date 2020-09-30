// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes.CODE_FENCE
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector

class CodeFencePluginFlavourDescriptor : CommonMarkFlavourDescriptor() {
  fun createHtmlGeneratingProviders(collector: MarkdownCodeFencePluginCacheCollector): Map<IElementType, GeneratingProvider> {
    return mapOf(
      CODE_FENCE to MarkdownCodeFenceGeneratingProvider(
        MarkdownCodeFencePluginGeneratingProvider.all
          .also { all -> all.forEach { if (it is MarkdownCodeFenceCacheableProvider) it.collector = collector } }
          .toTypedArray()
      )
    )
  }
}
