// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PlatformTestUtil.assertTreeEqual
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

class RestStructureViewTest : LightPlatformCodeInsightFixtureTestCase() {

  fun `test plain structure`() {
    doTest("-plain structure.rst\n" +
           " Chapter 1 Title\n" +
           " Chapter 2 Title\n")
  }

  fun `test file beginning`() {
    doTest("-file beginning.rst\n" +
           " Chapter 1 Title\n" +
           " Chapter 2 Title\n")
  }

  fun `test one inner section`() {
    doTest("-one inner section.rst\n" +
           " -Chapter 1 Title\n" +
           "  -Section 1.1 Title\n" +
           "   Subsection 1.1.1 Title\n" +
           "  Section 1.2 Title\n" +
           " Chapter 2 Title\n")
  }

  fun `test tree`() {
    doTest("-tree.rst\n" +
           " -Hello, world\n" +
           "  -A section\n" +
           "   -A subsection\n" +
           "    A sub-subsection\n" +
           "    An other one\n" +
           "  -Back up\n" +
           "   And down\n" +
           "   -twice\n" +
           "    with feelings\n")
  }

  private fun doTest(expected: String) {
    myFixture.configureByFile("/structureView/" + getTestName(true).trim() + ".rst")
    myFixture.testStructureView { svc ->
      PlatformTestUtil.expandAll(svc.tree)
      assertTreeEqual(svc.tree, expected)
    }
  }

  override fun getBasePath(): String {
    return "python/rest/testData"
  }
  override fun isCommunity(): Boolean = true
}
