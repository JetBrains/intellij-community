// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView

import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownStructureViewWithListsTest : MarkdownStructureViewTestCase() {
  override fun getTestDataPath() = MarkdownTestingUtil.TEST_DATA_PATH + "/structureView/withLists"

  override fun doTest(listVisibility: Boolean) {
    super.doTest(true)
  }

  fun testNormalATXDocument() {
    doTest()
  }

  fun testHeadersUnderBlockquotesAndLists() {
    doTest()
  }

  fun testPuppetlabsCoreTypes() {
    doTest()
  }

  fun testOnlyTrivialChildrenInListItems() {
    doTest()
  }

  fun testSimpleNestedList() {
    doTest()
  }

  fun testMultipleCompositeChildrenInLists() {
    doTest()
  }
}
