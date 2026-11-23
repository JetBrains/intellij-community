// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.images.editor.ConfigureHtmlImageLineMarkerProvider
import org.intellij.plugins.markdown.images.editor.ConfigureMarkdownImageLineMarkerProvider
import org.intellij.plugins.markdown.images.editor.MarkdownImageData
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class MarkdownLineMarkerApplyChangesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test markdown image simple changes`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "![some](image.png)")
    withContext(Dispatchers.EDT) {
      val markerProvider = ConfigureMarkdownImageLineMarkerProvider()
      // file > root > paragraph > image > exclamation mark
      val imageElement = myFixture.file.firstChild.firstChild.firstChild
      val data = MarkdownImageData(
        path = "myImage.png",
        width = "",
        height = "",
        title = "",
        description = "changed description",
        shouldConvertToHtml = false
      )
      markerProvider.applyChanges(imageElement, data)
      val image = myFixture.file.firstChild.firstChild as MarkdownImage
      assertEquals(data.path, image.linkDestination?.text)
      assertEquals(data.title, image.collectLinkTitleText() ?: "")
      assertEquals(data.description, image.collectLinkDescriptionText() ?: "")
    }
  }

  @Test
  fun `test markdown image change to html image`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "![some](image.png)")
    withContext(Dispatchers.EDT) {
      val markerProvider = ConfigureMarkdownImageLineMarkerProvider()
      // file > root > paragraph > image > exclamation mark
      val imageElement = myFixture.file.firstChild.firstChild.firstChild
      val data = MarkdownImageData(
        path = "image.png",
        width = "200",
        height = "300",
        title = "",
        description = "some",
        shouldConvertToHtml = true
      )
      markerProvider.applyChanges(imageElement, data)
      val image = XmlElementFactory.getInstance(myFixture.project).createTagFromText(myFixture.file.text, HTMLLanguage.INSTANCE)
      val srcAttribute = image.getAttributeValue("src")!!
      assertEquals(data.path, File(srcAttribute).name)
      assertEquals(data.width, image.getAttributeValue("width"))
      assertEquals(data.height, image.getAttributeValue("height"))
      assertEquals(data.description, image.getAttributeValue("alt"))
    }
  }

  @Test
  fun `test html image simple change`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "<img src=\"image.png\" alt=\"description\">")
    withContext(Dispatchers.EDT) {
      val markerProvider = ConfigureHtmlImageLineMarkerProvider()
      val tag = PsiTreeUtil.findChildOfType(myFixture.file.viewProvider.getPsi(HTMLLanguage.INSTANCE), HtmlTag::class.java)!!
      val data = MarkdownImageData(
        path = "myImage.png",
        width = "",
        height = "",
        title = "my added title",
        description = "changed description",
        shouldConvertToHtml = false
      )
      markerProvider.applyChanges(tag, data)
      val image = myFixture.file.firstChild.firstChild as MarkdownImage
      assertEquals(data.path, image.linkDestination?.text)
      assertEquals(data.description, image.collectLinkDescriptionText())
      assertEquals(data.title, image.collectLinkTitleText())
    }
  }

  @Test
  fun `test html image change to markdown image`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "<img src=\"image.png\" alt=\"description\">")
    withContext(Dispatchers.EDT) {
      val markerProvider = ConfigureHtmlImageLineMarkerProvider()
      val tag = PsiTreeUtil.findChildOfType(myFixture.file.viewProvider.getPsi(HTMLLanguage.INSTANCE), HtmlTag::class.java)!!
      val data = MarkdownImageData(
        path = "image.png",
        width = "",
        height = "",
        title = "",
        description = "changed description",
        shouldConvertToHtml = false
      )
      markerProvider.applyChanges(tag, data)
      val image = myFixture.file.firstChild.firstChild as MarkdownImage
      assertEquals(data.path, image.linkDestination?.text)
      assertEquals(data.description, image.collectLinkDescriptionText())
    }
  }
}
