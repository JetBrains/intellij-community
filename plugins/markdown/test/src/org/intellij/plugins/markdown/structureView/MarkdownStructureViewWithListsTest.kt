// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.structureView

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@RegistryKey(key = "markdown.structure.view.list.visibility", value = "true")
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/structureView/withLists")
class MarkdownStructureViewWithListsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testNormalATXDocument() {
    MarkdownStructureViewTestUtil.doTest(myFixture, testName)
  }

  @Test
  fun testHeadersUnderBlockquotesAndLists() {
    MarkdownStructureViewTestUtil.doTest(myFixture, testName)
  }

  @Test
  fun testPuppetlabsCoreTypes() {
    MarkdownStructureViewTestUtil.doTest(myFixture, testName)
  }

  @Test
  fun testOnlyTrivialChildrenInListItems() {
    MarkdownStructureViewTestUtil.doTest(myFixture, testName)
  }

  @Test
  fun testSimpleNestedList() {
    MarkdownStructureViewTestUtil.doTest(myFixture, testName)
  }

  @Test
  fun testMultipleCompositeChildrenInLists() {
    MarkdownStructureViewTestUtil.doTest(myFixture, testName)
  }
}
