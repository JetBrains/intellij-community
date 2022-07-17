// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.*
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import java.util.*

internal class PlantUMLCodeFenceDownloadLineMarkerProvider : CodeFenceDownloadLineMarkerProvider() {
  override fun shouldProcessElement(element: PsiElement): Boolean {
    return (element as? MarkdownCodeFence)?.fenceLanguage == PlantUMLLanguage.INSTANCE.displayName.lowercase(Locale.getDefault())
  }

  override fun getExtension(): MarkdownExtensionWithDownloadableFiles? {
    return MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<PlantUMLCodeGeneratingProvider>()
  }

  override val tooltipText: String
    get() = name

  override fun getName(): String {
    @Suppress("DialogTitleCapitalization")
    return MarkdownBundle.message("markdown.extensions.plantuml.download.line.marker.text")
  }
}
