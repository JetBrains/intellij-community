// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstings

import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.TestDataPath
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile

@TestFor(classes = [PyDocstringGenerator::class])
@TestDataPath("\$CONTENT_ROOT/../testData/docGeneration")
class PyDocstringGeneratorTestCase : PyTestCase() {
  override fun getTestDataPath(): String = super.getTestDataPath() + "/docGeneration"

  fun testModuleLevel() = doTestModuleLevelDocumentation()

  fun testModuleLevelAfterComments() = doTestModuleLevelDocumentation()

  private fun doTestModuleLevelDocumentation() {
    val testName = getTestName(true)
    val file = myFixture.configureByFile("$testName.py")
    if (file !is PyFile) {
      error("Incorrect file. Must be a Python file.")
    }
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      val docstringGenerator = PyDocstringGenerator.forDocStringOwner(file)
      docstringGenerator.buildAndInsert(DOCUMENTATION)
    }
    myFixture.checkResultByFile("$testName.after.py")
  }

  companion object {
    private const val DOCUMENTATION = "\"\"\"\n" +
                                      "This is documentation.\n" +
                                      "It has multiple lines.\n" +
                                      "\"\"\""
  }
}
