// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceDownloadLineMarkerProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFenceImpl

internal class PlantUMLCodeFenceDownloadLineMarkerProvider : MarkdownCodeFenceDownloadLineMarkerProvider() {
  override fun shouldProcessElement(element: PsiElement): Boolean {
    return (element as? MarkdownCodeFenceImpl)?.fenceLanguage == PlantUMLLanguage.INSTANCE.displayName.toLowerCase()
  }

  override fun getExtension(): MarkdownExtensionWithExternalFiles? {
    return MarkdownExtension.all.find { it is PlantUMLCodeGeneratingProvider } as? PlantUMLCodeGeneratingProvider
  }

  override val tooltipText: String
    get() = name

  override fun getName(): String {
    return MarkdownBundle.message("markdown.extensions.plantuml.download.line.marker.text")
  }
}
