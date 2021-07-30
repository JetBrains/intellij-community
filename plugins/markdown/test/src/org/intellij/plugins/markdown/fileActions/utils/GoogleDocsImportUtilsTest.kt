// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class GoogleDocsImportUtilsTest : BasePlatformTestCase() {

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  @Test
  fun `test extract google docs ID from link`() {
    val link = "https://docs.google.com/document/d/3sj4shQfcgLKmAvamEn4zqTSGD__RW_TAp9oBjXiCE9A/edit#heading=h.f005fljz7miu"
    val expected = "3sj4shQfcgLKmAvamEn4zqTSGD__RW_TAp9oBjXiCE9A"
    val actual = GoogleDocsImportUtils.extractDocsId(link)

    assertEquals(expected, actual)
  }

  @Test
  fun `test correct link`() {
    val link = "https://docs.google.com/document/d/3sj4shQfcgLKmAvamEn4zqTSGD__RW_TAp9oBjXiCE9A"
    val actual = GoogleDocsImportUtils.isLinkToDocumentCorrect(link)

    assertTrue(actual)
  }

  @Test
  fun `test correct link with anchor`() {
    val link = "https://docs.google.com/document/d/3sj4shQfcgLKmAvamEn4zqTSGD__RW_TAp9oBjXiCE9A/edit#heading=h.f005fljz7miu"
    val actual = GoogleDocsImportUtils.isLinkToDocumentCorrect(link)

    assertTrue(actual)
  }

  @Test
  fun `test link without ID`() {
    val link = "https://docs.google.com/document"
    val actual = GoogleDocsImportUtils.isLinkToDocumentCorrect(link)

    assertFalse(actual)
  }

  @Test
  fun `test link with incorrect prefix`() {
    val link = "https://google/3sj4shQfcgLKmAvamEn4zqTSGD__RW_TAp9oBjXiCE9A/edit#heading=h.f005fljz7miu"
    val actual = GoogleDocsImportUtils.isLinkToDocumentCorrect(link)

    assertFalse(actual)
  }
}
