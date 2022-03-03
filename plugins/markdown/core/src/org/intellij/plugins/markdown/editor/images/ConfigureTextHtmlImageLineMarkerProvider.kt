// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ConfigureTextHtmlImageLineMarkerProvider : ConfigureImageLineMarkerProviderBase<PsiElement>() {
  override fun obtainLeafElement(element: PsiElement): PsiElement? {
    if (element.elementType != MarkdownTokenTypes.HTML_TAG) {
      return null
    }
    return element.takeIf { ImageUtils.createImageTagFromText(element) != null }
  }

  override fun obtainOuterElement(element: PsiElement): PsiElement? {
    // HtmlTag is an outer element for itself
    return obtainLeafElement(element)
  }

  override fun obtainPathText(element: PsiElement): String? {
    return ImageUtils.createImageTagFromText(element)?.getAttributeValue("src")
  }

  override fun createDialog(element: PsiElement): ConfigureImageDialog? {
    val image = ImageUtils.createImageTagFromText(element) ?: return null
    return ConfigureImageDialog(
      image.project,
      MarkdownBundle.message("markdown.configure.image.title.text"),
      path = obtainPathText(element),
      width = image.getAttributeValue("width"),
      height = image.getAttributeValue("height"),
      linkTitle = image.getAttributeValue("title"),
      linkDescriptionText = image.getAttributeValue("alt"),
      shouldConvertToHtml = true
    )
  }

  override fun applyChanges(element: PsiElement, imageData: MarkdownImageData) {
    when {
      imageData.shouldConvertToHtml -> updateAttributes(element, imageData)
      else -> convertToMarkdown(element, imageData)
    }
  }

  private fun updateAttributes(element: PsiElement, imageData: MarkdownImageData) {
    val action = Runnable {
      val replacement = MarkdownPsiElementFactory.createHtmlImageTag(element.project, imageData)
      element.replace(replacement)
    }
    WriteCommandAction.runWriteCommandAction(
      element.project,
      MarkdownBundle.message("markdown.configure.image.line.marker.update.html.image.attributes.command.name"),
      null,
      action
    )
  }

  private fun convertToMarkdown(element: PsiElement, imageData: MarkdownImageData) {
    val action = Runnable {
      element.replace(MarkdownPsiElementFactory.createImage(
        element.project,
        imageData.description,
        imageData.path,
        imageData.title
      ))
    }
    WriteCommandAction.runWriteCommandAction(
      element.project,
      MarkdownBundle.message("markdown.configure.image.line.marker.convert.html.to.markdown.command.name"),
      null,
      action
    )
  }
}
