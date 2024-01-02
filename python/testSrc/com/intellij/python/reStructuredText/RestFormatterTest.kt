// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RestFormatterTest : BasePlatformTestCase() {

  fun `test directive`() {
    doTest()
  }

  fun `test title`() {
    doTest()
  }

  fun `test field list`() {
    doTest()
  }

  private fun doTest() {
    val testName = getTestName(true).trim()
    myFixture.configureByFile("formatter/$testName.rst")
    WriteCommandAction.runWriteCommandAction(null) {
      val codeStyleManager = CodeStyleManager.getInstance(myFixture.project)
      val file = myFixture.file
      codeStyleManager.reformat(file)
    }
    myFixture.checkResultByFile("formatter/${testName}_after.rst")
  }


  override fun getBasePath(): String = "python/python-rest/testData"

  override fun isCommunity(): Boolean = true
}
