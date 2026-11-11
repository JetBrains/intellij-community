// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownListBackspaceHandlerDelegatesTest: LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/editor/lists/backspace/"

  fun testDeleteMarker() = doTest()

  fun testDeleteMarkerInSublist() = doTest()

  fun testDeleteIndent() = doTest()

  fun testDeleteTabIndent() = doTest("*\n\t<caret>abcdefg", "*\n<caret>abcdefg")

  fun testDeleteTabIndentAfterIndentedMarker() = doTest(" *\n\t<caret>abcdefg", " *\n<caret>abcdefg")

  fun testDeleteTabIndentBeforeShortText() = doTest("*\n\t<caret>a", "*\n<caret>a")

  fun testDeleteSpaceAfterMixedIndent() = doTest("* outer\n \t <caret>text", "* outer\n<caret>text")

  fun testDeleteSpaceAfterMixedIndentBeforeTab() = doTest("* outer\n \t <caret>\ttext", "* outer\n<caret>    text")

  fun testExpandTabAtParentIndent() = doTest("* outer\n\t* inner\n\t\t<caret>text", "* outer\n\t* inner\n    <caret>text")

  fun testDeleteTabIndentBeforeSpaces() = doTest("*\n\t<caret>  text", "*\n<caret>  text")

  fun testDeleteIndentCaretAfterEndOfLine() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    val originalPosition = editor.caretModel.logicalPosition
    val newPosition = LogicalPosition(originalPosition.line, originalPosition.column + 10, originalPosition.leansForward)
    editor.settings.isVirtualSpace = true
    editor.caretModel.moveToLogicalPosition(newPosition)
    backspace()
    TestCase.assertEquals(originalPosition, editor.caretModel.logicalPosition)
  }

  fun testIterateIndentLevels() = doTest(2)

  fun testIterateIndentLevelsInBlockQuote() = doTest(2)

  private fun doTest(before: String, after: String) {
    configureFromFileText("test.md", before)
    editor.settings.setTabSize(4)
    backspace()
    checkResultByText(after)
  }

  private fun doTest(repeats: Int = 1) {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    repeat(repeats) { i ->
      backspace()
      val suffix = if (repeats == 1) "" else "-${i + 1}"
      checkResultByFile("$testName-after$suffix.md")
    }
  }
}