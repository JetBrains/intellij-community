// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownPsiImageImplTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test image`(): Unit = doTest(
    "![some](some.png)",
    description = "some",
    destination = "some.png",
    title = null
  )

  @Test
  fun `test image with double quote in description`(): Unit = doTest(
    "![\"some](some.png)",
    description = "\"some",
    destination = "some.png",
    title = null
  )

  @Test
  fun `test image with two double quotes in description`(): Unit = doTest(
    "![\"some\"](some.png)",
    description = "\"some\"",
    destination = "some.png",
    title = null
  )

  @Test
  fun `test image with title`(): Unit = doTest(
    "![some](some.png \"title\")",
    description = "some",
    destination = "some.png",
    title = "title"
  )

  @Test
  fun `test image without description and destination`(): Unit = doTest(
    "![]()",
    description = null,
    destination = null,
    title = null
  )

  @Test
  fun `test image without description`(): Unit = doTest(
    "![](some.png)",
    description = null,
    destination = "some.png",
    title = null
  )

  private fun doTest(text: String, description: String? = null, destination: String? = null, title: String? = null): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", text)
    readAction {
      val image = myFixture.file.firstChild!!.firstChild!!
      assertInstanceOf(MarkdownImage::class.java, image)
      image as MarkdownImage
      assertEquals(description, image.collectLinkDescriptionText())
      assertEquals(title, image.collectLinkTitleText())
      assertEquals(destination, image.linkDestination?.text)
    }
  }
}
