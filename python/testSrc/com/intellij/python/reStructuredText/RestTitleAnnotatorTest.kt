// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

class RestTitleAnnotatorTest : BasePlatformTestCase() {
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
