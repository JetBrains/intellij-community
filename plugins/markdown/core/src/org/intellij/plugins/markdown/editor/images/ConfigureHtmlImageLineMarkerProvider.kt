// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.xml.XmlTokenType
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ConfigureHtmlImageLineMarkerProvider : ConfigureImageLineMarkerProviderBase<HtmlTag>() {
  override fun getName(): String {
    return MarkdownBundle.message("markdown.configure.html.image.line.marker.provider.name")
  }

  override fun obtainLeafElement(element: PsiElement): PsiElement? {
    // Providing line markers for html only inside a markdown file
    if (element.containingFile?.viewProvider?.baseLanguage != MarkdownLanguage.INSTANCE) {
      return null
    }
    return element.takeIf { getImageByNameElement(element) != null }
  }

  override fun obtainOuterElement(element: PsiElement): HtmlTag? {
    if (element is HtmlTag && element.name == "img") {
      return element
    }
    return getImageByNameElement(element)
  }

  override fun obtainPathText(element: PsiElement): String? {
    return obtainOuterElement(element)?.getAttributeValue("src")
  }

  override fun createDialog(element: HtmlTag): ConfigureImageDialog {
    return ConfigureImageDialog(
      element.project,
      MarkdownBundle.message("markdown.configure.image.title.text"),
      path = obtainPathText(element),
      width = element.getAttributeValue("width"),
      height = element.getAttributeValue("height"),
      linkTitle = element.getAttributeValue("title"),
      linkDescriptionText = element.getAttributeValue("alt"),
      shouldConvertToHtml = true
    )
  }

  override fun applyChanges(element: PsiElement, imageData: MarkdownImageData) {
    val image = obtainOuterElement(element) ?: return
    when {
      imageData.shouldConvertToHtml -> updateAttributes(image, imageData)
      else -> convertToMarkdown(image, imageData)
    }
  }

  private fun updateAttributes(image: HtmlTag, imageData: MarkdownImageData) {
    val action = Runnable {
      image.setAttributeIfNotEmpty("src", imageData.path)
      image.setAttributeIfNotEmpty("width", imageData.width)
      image.setAttributeIfNotEmpty("height", imageData.height)
      image.setAttributeIfNotEmpty("title", imageData.title)
      image.setAttributeIfNotEmpty("alt", imageData.description)
    }
    WriteCommandAction.runWriteCommandAction(
      image.project,
      MarkdownBundle.message("markdown.configure.image.line.marker.update.html.image.attributes.command.name"),
      null,
      action
    )
  }

  private fun convertToMarkdown(image: HtmlTag, imageData: MarkdownImageData) {
    val action = Runnable {
      // As we are operating on HTML PSI, we are replacing img tag with
      // the text representation of Markdown img.
      val text = ImageUtils.createMarkdownImageText(imageData.description, imageData.path, imageData.title)
      XmlElementFactory.getInstance(image.project).createDisplayText(text)
      image.replace(XmlElementFactory.getInstance(image.project).createDisplayText(text))
    }
    WriteCommandAction.runWriteCommandAction(
      image.project,
      MarkdownBundle.message("markdown.configure.image.line.marker.convert.html.to.markdown.command.name"),
      null,
      action
    )
  }

  private fun HtmlTag.setAttributeIfNotEmpty(attributeName: String, value: String) {
    if (value.isEmpty() && getAttribute(attributeName) == null) {
      return
    }
    setAttribute(attributeName, value)
  }

  companion object {
    fun isImgTagName(element: PsiElement): Boolean {
      return element.hasType(XmlTokenType.XML_NAME) && element.text == "img"
    }

    fun getImageByNameElement(element: PsiElement): HtmlTag? {
      if (!isImgTagName(element)) {
        return null
      }
      return element.parent as? HtmlTag
    }
  }
}
