// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceDownloadLineMarkerProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import java.util.*

internal class MermaidCodeFenceDownloadLineMarkerProvider: MarkdownCodeFenceDownloadLineMarkerProvider() {
  override fun shouldProcessElement(element: PsiElement): Boolean {
    return (element as? MarkdownCodeFence)?.fenceLanguage == MermaidLanguage.INSTANCE.displayName.lowercase(Locale.getDefault())
  }

  override fun getExtension(): MarkdownExtensionWithExternalFiles? {
    return MarkdownBrowserPreviewExtension.Provider.all.find { it is MermaidBrowserExtension.Provider } as? MermaidBrowserExtension.Provider
  }

  override val tooltipText: String
    get() = name

  override fun getName(): String {
    @Suppress("DialogTitleCapitalization")
    return MarkdownBundle.message("markdown.extensions.mermaid.download.line.marker.text")
  }
}
