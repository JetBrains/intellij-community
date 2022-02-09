// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ConfigureMarkdownImageLineMarkerProvider : ConfigureImageLineMarkerProviderBase<MarkdownImage>() {
  override fun obtainPathText(element: PsiElement): String? {
    val image = obtainOuterElement(element) ?: return null
    return image.linkDestination?.text
  }

  override fun obtainLeafElement(element: PsiElement): PsiElement? {
    val outer = MarkdownImage.getByLeadingExclamationMark(element)
    return element.takeIf { outer != null }
  }

  override fun obtainOuterElement(element: PsiElement): MarkdownImage? {
    return when (element) {
      is MarkdownImage -> element
      else -> MarkdownImage.getByLeadingExclamationMark(element)
    }
  }

  override fun createDialog(element: MarkdownImage): ConfigureImageDialog? {
    val image = obtainOuterElement(element) ?: return null
    return ConfigureImageDialog(
      image.project,
      MarkdownBundle.message("markdown.configure.image.title.text"),
      path = obtainPathText(element),
      linkTitle = image.collectLinkTitleText(),
      linkDescriptionText = image.collectLinkDescriptionText(),
      shouldConvertToHtml = false
    )
  }

  private fun isInsideParagraph(element: PsiElement): Boolean {
    return element.parents(withSelf = false).find { it.hasType(MarkdownElementTypes.PARAGRAPH) } != null
  }

  private fun createHtmlReplacement(element: PsiElement, imageData: MarkdownImageData): PsiElement {
    // Inside paragraphs HTML is always represented by plain HTML_TAG element
    return when {
      isInsideParagraph(element) -> MarkdownPsiElementFactory.createHtmlImageTag(element.project, imageData)
      else -> MarkdownPsiElementFactory.createHtmlBlockWithImage(element.project, imageData)
    }
  }

  override fun applyChanges(element: PsiElement, imageData: MarkdownImageData) {
    val outerElement = obtainOuterElement(element) ?: return
    val project = outerElement.project
    val replacement = when {
      imageData.shouldConvertToHtml -> createHtmlReplacement(outerElement, imageData)
      else -> MarkdownPsiElementFactory.createImage(
        project,
        imageData.description,
        imageData.path,
        imageData.title
      )
    }
    val action = Runnable {
      outerElement.replace(replacement)
    }
    WriteCommandAction.runWriteCommandAction(
      project,
      MarkdownBundle.message("markdown.configure.image.title.text"),
      null,
      action
    )
  }
}
