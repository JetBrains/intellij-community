// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.backspace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/lists/backspace")
class MarkdownListBackspaceHandlerDelegatesTest {
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
  fun testDeleteMarker(): Unit = doTest()

  @Test
  fun testDeleteMarkerInSublist(): Unit = doTest()

  @Test
  fun testDeleteIndent(): Unit = doTest()

  @Test
  fun testDeleteTabIndent(): Unit = doTest("*\n\t<caret>abcdefg", "*\n<caret>abcdefg")

  @Test
  fun testDeleteTabIndentAfterIndentedMarker(): Unit = doTest(" *\n\t<caret>abcdefg", " *\n<caret>abcdefg")

  @Test
  fun testDeleteTabIndentBeforeShortText(): Unit = doTest("*\n\t<caret>a", "*\n<caret>a")

  @Test
  fun testDeleteSpaceAfterMixedIndent(): Unit = doTest("* outer\n \t <caret>text", "* outer\n<caret>text")

  @Test
  fun testDeleteSpaceAfterMixedIndentBeforeTab(): Unit = doTest("* outer\n \t <caret>\ttext", "* outer\n<caret>    text")

  @Test
  fun testExpandTabAtParentIndent(): Unit = doTest("* outer\n\t* inner\n\t\t<caret>text", "* outer\n\t* inner\n    <caret>text")

  @Test
  fun testDeleteTabIndentBeforeSpaces(): Unit = doTest("*\n\t<caret>  text", "*\n<caret>  text")

  @Test
  fun testDeleteIndentCaretAfterEndOfLine(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.md")
    withContext(Dispatchers.EDT) {
      val originalPosition = myFixture.editor.caretModel.logicalPosition
      val newPosition = LogicalPosition(originalPosition.line, originalPosition.column + 10, originalPosition.leansForward)
      myFixture.editor.settings.isVirtualSpace = true
      myFixture.editor.caretModel.moveToLogicalPosition(newPosition)
      myFixture.backspace()
      assertEquals(originalPosition, myFixture.editor.caretModel.logicalPosition)
    }
  }

  @Test
  fun testIterateIndentLevels(): Unit = doTest(2)

  @Test
  fun testIterateIndentLevelsInBlockQuote(): Unit = doTest(2)

  private fun doTest(before: String, after: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("test.md", before)
    withContext(Dispatchers.EDT) {
      myFixture.editor.settings.setTabSize(4)
      myFixture.backspace()
      myFixture.checkResult(after)
    }
  }

  private fun doTest(repeats: Int = 1): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.md")
    withContext(Dispatchers.EDT) {
      repeat(repeats) { i ->
        myFixture.backspace()
        val suffix = if (repeats == 1) "" else "-${i + 1}"
        myFixture.checkResultByFile("$testName-after$suffix.md")
      }
    }
  }
}
