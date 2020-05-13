// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.suggested

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.RefactoringBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase

class PySuggestedRefactoringTest : PyTestCase() {

  // PY-42285
  fun testImportedClassRename() {
    doRenameImportedTest(
      """
        class A<caret>C:
          pass
      """.trimIndent(),
      """
        class ABC:
          pass
      """.trimIndent(),
      "AC",
      "ABC",
      "B"
    )
  }

  // PY-42285
  fun testImportedFunctionRename() {
    doRenameImportedTest(
      """
        def a<caret>c():
          pass
      """.trimIndent(),
      """
        def abc():
          pass
      """.trimIndent(),
      "ac",
      "abc",
      "b"
    )
  }

  // PY-42285
  fun testMethodRename() {
    doRenameTest(
      """
        class A:
          def method<caret>(self):
            pass
            
        a = A()
        a.method()
      """.trimIndent(),
      """
        class A:
          def method1(self):
            pass
            
        a = A()
        a.method1()
      """.trimIndent(),
      "method",
      "method1",
      "1"
    )
  }

  // PY-42285
  fun testImportedVariableRename() {
    doRenameImportedTest(
      """
        A<caret>C = 10
      """.trimIndent(),
      """
        ABC = 10
      """.trimIndent(),
      "AC",
      "ABC",
      "B"
    )
  }

  // PY-42285
  fun testInstanceVariableRename() {
    doRenameTest(
      """
        class A:
          def __init__(self):
            self.a<caret>c = 10
            
        a = A()
        a.ac
      """.trimIndent(),
      """
        class A:
          def __init__(self):
            self.abc = 10
            
        a = A()
        a.abc
      """.trimIndent(),
      "ac",
      "abc",
      "b"
    )
  }

  // PY-42285
  fun testClassVariableRename() {
    doRenameTest(
      """
        class A:
            a<caret>c = 10

        A.ac
      """.trimIndent(),
      """
        class A:
            abc = 10

        A.abc
      """.trimIndent(),
      "ac",
      "abc",
      "b"
    )
  }

  // PY-42285
  fun testParameterRename() {
    doRenameTest(
      """
        def foo(pa<caret>am):
          print(paam)
      """.trimIndent(),
      """
        def foo(param):
          print(param)
      """.trimIndent(),
      "paam",
      "param",
      "r"
    )
  }

  private fun doRenameTest(before: String, after: String, oldName: String, newName: String, type: String) {
    myFixture.configureByText(PythonFileType.INSTANCE, before)
    myFixture.checkHighlighting()

    val project = myFixture.project

    myFixture.type(type)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val intention = RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName, newName)
    myFixture.findSingleIntention(intention).also {
      CommandProcessor.getInstance().executeCommand(project, { it.invoke(project, myFixture.editor, myFixture.file) }, it.text, null)
    }

    myFixture.checkResult(after)
    myFixture.checkHighlighting()
  }

  private fun doRenameImportedTest(
    importedBefore: String,
    importedAfter: String,
    oldName: String,
    newName: String,
    type: String
  ) {
    val testDataPathPrefix = "refactoring/suggested/${getTestName(true)}"
    val source = "a.py"

    myFixture.copyFileToProject("$testDataPathPrefix/$source", source)
    doRenameTest(importedBefore, importedAfter, oldName, newName, type)
    myFixture.checkResultByFile(source, "$testDataPathPrefix/a.after.py", true)
  }
}