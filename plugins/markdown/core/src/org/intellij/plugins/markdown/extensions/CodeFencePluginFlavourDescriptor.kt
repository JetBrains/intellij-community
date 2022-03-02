// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes.CODE_FENCE
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector

class CodeFencePluginFlavourDescriptor : CommonMarkFlavourDescriptor() {

  fun createHtmlGeneratingProviders(collector: MarkdownCodeFencePluginCacheCollector, project: Project? = null, file: VirtualFile? = null): Map<IElementType, GeneratingProvider> {
    return mapOf(
      CODE_FENCE to DefaultCodeFenceGeneratingProvider(
        CodeFenceGeneratingProvider.all
          .also { all -> all.forEach { if (it is MarkdownCodeFenceCacheableProvider) it.collector = collector } }
          .toTypedArray()
        , project, file)
    )
  }
}
