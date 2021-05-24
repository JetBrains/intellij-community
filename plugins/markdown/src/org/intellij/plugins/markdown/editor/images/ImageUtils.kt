// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.XmlElementFactoryImpl
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.util.hasType

internal object ImageUtils {
  @JvmStatic
  fun createMarkdownImageText(description: String = "", path: String, title: String = ""): String {
    val actualTitle = when {
      title.isNotEmpty() -> " ${XmlElementFactoryImpl.quoteValue(title)}"
      else -> title
    }
    return "![$description]($path${actualTitle})"
  }

  @JvmStatic
  fun createHtmlImageText(imageData: MarkdownImageData): String {
    val (path, width, height, title, description) = imageData
    val attributes = listOf(
      "src" to path,
      "width" to width,
      "height" to height,
      "title" to title,
      "alt" to description
    )
    val attributesString = attributes.filter { it.second.isNotEmpty() }.joinToString(" ") {
      "${it.first}=${XmlElementFactoryImpl.quoteValue(it.second)}"
    }
    return "<img $attributesString/>"
  }

  @JvmStatic
  fun createImageTagFromText(element: PsiElement): XmlTag? {
    if (!element.hasType(MarkdownTokenTypes.HTML_TAG)) {
      return null
    }
    try {
      val tag = XmlElementFactory.getInstance(element.project).createTagFromText(element.text, HTMLLanguage.INSTANCE)
      return tag.takeIf { it.name == "img" }
    } catch (exception: IncorrectOperationException) {
      return null
    }
  }
}
