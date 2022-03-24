// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import java.io.File

class MarkdownLineMarkerApplyChangesTest: LightPlatformCodeInsightTestCase() {
  fun `test markdown image simple changes`() {
    configureFromFileText("some.md", "![some](image.png)")
    val markerProvider = ConfigureMarkdownImageLineMarkerProvider()
    // file > root > paragraph > image > exclamation mark
    val imageElement = file.firstChild.firstChild.firstChild
    val data = MarkdownImageData(
      path = "myImage.png",
      width = "",
      height = "",
      title = "",
      description = "changed description",
      shouldConvertToHtml = false
    )
    markerProvider.applyChanges(imageElement, data)
    val image = file.firstChild.firstChild.firstChild as MarkdownImage
    assertEquals(data.path, image.linkDestination?.text)
    assertEquals(data.title, image.collectLinkTitleText() ?: "")
    assertEquals(data.description, image.collectLinkDescriptionText() ?: "")
  }

  fun `test markdown image change to html image`() {
    configureFromFileText("some.md", "![some](image.png)")
    val markerProvider = ConfigureMarkdownImageLineMarkerProvider()
    // file > root > paragraph > image > exclamation mark
    val imageElement = file.firstChild.firstChild.firstChild
    val data = MarkdownImageData(
      path = "image.png",
      width = "200",
      height = "300",
      title = "",
      description = "some",
      shouldConvertToHtml = true
    )
    markerProvider.applyChanges(imageElement, data)
    val image = XmlElementFactory.getInstance(project).createTagFromText(file.text, HTMLLanguage.INSTANCE)
    val srcAttribute = image.getAttributeValue("src")!!
    assertEquals(data.path, File(srcAttribute).name)
    assertEquals(data.width, image.getAttributeValue("width"))
    assertEquals(data.height, image.getAttributeValue("height"))
    assertEquals(data.description, image.getAttributeValue("alt"))
  }

  fun `test html image simple change`() {
    configureFromFileText("some.md", "<img src=\"image.png\" alt=\"description\">")
    val markerProvider = ConfigureHtmlImageLineMarkerProvider()
    val tag = PsiTreeUtil.findChildOfType(file.viewProvider.getPsi(HTMLLanguage.INSTANCE), HtmlTag::class.java)!!
    val data = MarkdownImageData(
      path = "myImage.png",
      width = "",
      height = "",
      title = "my added title",
      description = "changed description",
      shouldConvertToHtml = false
    )
    markerProvider.applyChanges(tag, data)
    val image = file.firstChild.firstChild.firstChild as MarkdownImage
    assertEquals(data.path, image.linkDestination?.text)
    assertEquals(data.description, image.collectLinkDescriptionText())
    assertEquals(data.title, image.collectLinkTitleText())
  }

  fun `test html image change to markdown image`() {
    configureFromFileText("some.md", "<img src=\"image.png\" alt=\"description\">")
    val markerProvider = ConfigureHtmlImageLineMarkerProvider()
    val tag = PsiTreeUtil.findChildOfType(file.viewProvider.getPsi(HTMLLanguage.INSTANCE), HtmlTag::class.java)!!
    val data = MarkdownImageData(
      path = "image.png",
      width = "",
      height = "",
      title = "",
      description = "changed description",
      shouldConvertToHtml = false
    )
    markerProvider.applyChanges(tag, data)
    val image = file.firstChild.firstChild.firstChild as MarkdownImage
    assertEquals(data.path, image.linkDestination?.text)
    assertEquals(data.description, image.collectLinkDescriptionText())
  }
}
