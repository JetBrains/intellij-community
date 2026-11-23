// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownExtendWordSelectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testSingleInsideOneWord(): Unit = doTest(
    "`Some <caret>text`",
    "`Some <selection><caret>text</selection>`",
    1
  )

  @Test
  fun testSingleInsideAll(): Unit = doTest(
    "`Some <caret>text`",
    "`<selection>Some <caret>text</selection>`",
    2
  )

  @Test
  fun testSingleOutside(): Unit = doTest(
    "`Some <caret>text`",
    "<selection>`Some <caret>text`</selection>",
    3
  )

  @Test
  fun testDoubleInsideAll(): Unit = doTest(
    "`Some <caret>text`",
    "`<selection>Some <caret>text</selection>`",
    2
  )

  @Test
  fun testSingleMultilineInsideAll(): Unit = doTest(
    """
    `Some <caret>text
    other line?`
    """.trimIndent(),
    """
    `<selection>Some <caret>text
    other line?</selection>`
    """.trimIndent(),
    3
  )

  @Test
  fun testDoubleMultilineInsideAll(): Unit = doTest(
    """
    ``Some <caret>text
    other line?``
    """.trimIndent(),
    """
    ``<selection>Some <caret>text
    other line?</selection>``
    """.trimIndent(),
    3
  )

  @Test
  fun testAlmostFenceInsideAll(): Unit = doTest(
    "```Some <caret>text and the rest```",
    "```<selection>Some <caret>text and the rest</selection>```",
    2
  )

  @Test
  fun testAlmostFenceOutside(): Unit = doTest(
    "```Some <caret>text and the rest```",
    "<selection>```Some <caret>text and the rest```</selection>",
    3
  )

  @Test
  fun testWithSelected(): Unit = doTest(
    "`So<selection>me <caret>text and t</selection>he rest`",
    "`<selection>Some <caret>text and the rest</selection>`",
    1
  )

  @Test
  fun testWordIsSelected(): Unit = doTest(
    sections,
    """
    # Head

    ## Second l 1

    This is my <selection><caret>text</selection>

    ## Second l 2

    # Other
    """.trimIndent(),
    1
  )

  @Test
  fun testLineIsSelected(): Unit = doTest(
    sections,
    """
    # Head

    ## Second l 1

    <selection>This is my <caret>text</selection>

    ## Second l 2

    # Other
    """.trimIndent(),
    2
  )

  @Test
  fun testParagraphIsSelected(): Unit = doTest(
    sections,
    """
    # Head

    ## Second l 1

    <selection>This is my <caret>text
    </selection>
    ## Second l 2

    # Other
    """.trimIndent(),
    3
  )

  @Test
  fun testSectionWithHeaderIsSelected(): Unit = doTest(
    sections,
    """
    # Head

    <selection>## Second l 1

    This is my <caret>text
    </selection>
    ## Second l 2

    # Other
    """.trimIndent(),
    4
  )

  @Test
  fun testFileIsSelected(): Unit = doTest(
    sections,
    """
    <selection># Head

    ## Second l 1

    This is my <caret>text

    ## Second l 2
    </selection>
    # Other
    """.trimIndent(),
    5
  )

  private val sections = """
    # Head

    ## Second l 1

    This is my <caret>text

    ## Second l 2

    # Other
  """.trimIndent()

  private fun doTest(before: String, expected: String, count: Int): Unit = timeoutRunBlocking {
    myFixture.configureByText("test.md", before)
    withContext(Dispatchers.EDT) {
      repeat(count) {
        myFixture.executeAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
      }
      myFixture.checkResult(expected)
    }
  }
}
