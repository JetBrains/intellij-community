// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

class RestTitleAnnotatorTest : LightPlatformCodeInsightFixtureTestCase() {
  fun `test underline match overline`() {
    doTest("----------------\n" +
           "``a_function()``\n" +
           "----------------\n")
  }

  fun `test no overline`() {
    doTest("``a_function()``\n" +
                  "----------------\n")
  }

  fun `test overline shorter`() {
    doTest("<warning descr=\"Overline length must match the underline\">```````````\n" +
           "``a_function()``\n" +
           "----------------\n" +
           "</warning>")
  }

  fun `test underline shorter`() {
    doTest("<warning descr=\"Overline length must match the underline\">````````````````\n" +
           "``a_function()``\n" +
           "------\n" +
           "</warning>")
  }

  fun `test different adornments`() {
    doTest("````````````````\n" +
           "``a_function()``\n" +
           "----------------")
  }

  fun `test short under and overline`() {
    doTest("````````````````\n" +
           "``a_function()``\n" +
           "----------------")
  }

  private fun doTest(fileText: String) {
    myFixture.configureByText(RestFileType.INSTANCE, fileText)
    myFixture.checkHighlighting(true, false, false)
  }
}
