// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownExtendWordSelectionTest: LightPlatformCodeInsightTestCase() {
  fun testSingleInsideOneWord() = doTest(
    "`Some <caret>text`",
    "`Some <selection><caret>text</selection>`",
    1
  )

  fun testSingleInsideAll() = doTest(
    "`Some <caret>text`",
    "`<selection>Some <caret>text</selection>`",
    2
  )

  fun testSingleOutside() = doTest(
    "`Some <caret>text`",
    "<selection>`Some <caret>text`</selection>",
    3
  )

  fun testDoubleInsideAll() = doTest(
    "`Some <caret>text`",
    "`<selection>Some <caret>text</selection>`",
    2
  )

  fun testSingleMultilineInsideAll() = doTest(
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

  fun testDoubleMultilineInsideAll() = doTest(
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

  fun testAlmostFenceInsideAll() = doTest(
    "```Some <caret>text and the rest```",
    "```<selection>Some <caret>text and the rest</selection>```",
    2
  )

  fun testAlmostFenceOutside() = doTest(
    "```Some <caret>text and the rest```",
    "<selection>```Some <caret>text and the rest```</selection>",
    3
  )

  fun testWithSelected() = doTest(
    "`So<selection>me <caret>text and t</selection>he rest`",
    "`<selection>Some <caret>text and the rest</selection>`",
    1
  )

  fun testWordIsSelected() = doTest(
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

  fun testLineIsSelected() = doTest(
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

  fun testParagraphIsSelected() = doTest(
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

  fun testSectionWithHeaderIsSelected() = doTest(
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

  fun testFileIsSelected() = doTest(
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

  private fun doTest(before: String, expected: String, count: Int) {
    configureFromFileText("test.md", before)
    repeat(count) {
      executeAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
    }
    checkResultByText(expected)
  }

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/actions/extendWordSelection/"
  }
}
