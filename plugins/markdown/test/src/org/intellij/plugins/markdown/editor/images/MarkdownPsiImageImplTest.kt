// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage

class MarkdownPsiImageImplTest: LightPlatformCodeInsightTestCase() {
  fun `test image`() = doTest(
    "![some](some.png)",
    description = "some",
    destination = "some.png",
    title = null
  )

  fun `test image with double quote in description`() = doTest(
    "![\"some](some.png)",
    description = "\"some",
    destination = "some.png",
    title = null
  )

  fun `test image with two double quotes in description`() = doTest(
    "![\"some\"](some.png)",
    description = "\"some\"",
    destination = "some.png",
    title = null
  )

  fun `test image with title`() = doTest(
    "![some](some.png \"title\")",
    description = "some",
    destination = "some.png",
    title = "title"
  )

  fun `test image without description and destination`() = doTest(
    "![]()",
    description = null,
    destination = null,
    title = null
  )

  fun `test image without description`() = doTest(
    "![](some.png)",
    description = null,
    destination = "some.png",
    title = null
  )

  private fun doTest(text: String, description: String? = null, destination: String? = null, title: String? = null) {
    configureFromFileText("some.md", text)
    val root = file.firstChild!!
    val image = root.firstChild!!.firstChild!!
    assertInstanceOf(image, MarkdownImage::class.java)
    image as MarkdownImage
    assertEquals(description, image.collectLinkDescriptionText())
    assertEquals(title, image.collectLinkTitleText())
    assertEquals(destination, image.linkDestination?.text)
  }
}
