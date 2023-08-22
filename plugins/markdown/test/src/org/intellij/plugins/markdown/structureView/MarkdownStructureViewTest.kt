// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView

import org.intellij.plugins.markdown.MarkdownTestingUtil

open class MarkdownStructureViewTest : MarkdownStructureViewTestCase() {

  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/structureView/base/"

  fun testOneParagraph() {
    doTest()
  }

  fun testTwoParagraphs() {
    doTest()
  }

  fun testNormalATXDocument() {
    doTest()
  }

  fun testNormalSetextDocument() {
    doTest()
  }

  fun testHeadersLadder() {
    doTest()
  }

  fun testHeadersUnderBlockquotesAndLists() {
    doTest()
  }

  fun testPuppetlabsCoreTypes() {
    doTest()
  }
}
