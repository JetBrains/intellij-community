/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.rest

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

class RestFormatterTest : LightPlatformCodeInsightFixtureTestCase() {

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


  override fun getBasePath(): String {
    return "python/rest/testData"
  }

  override fun isCommunity(): Boolean = true
}
