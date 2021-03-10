// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Test

class MarkdownCodeSpanExtendWordSelectionHandlerTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun testSingleInsideOneWord() = doTest(
    "`Some <caret>text`",
    "`Some <selection><caret>text</selection>`",
    1
  )

  @Test
  fun testSingleInsideAll() = doTest(
    "`Some <caret>text`",
    "`<selection>Some <caret>text</selection>`",
    2
  )

  @Test
  fun testSingleOutside() = doTest(
    "`Some <caret>text`",
    "<selection>`Some <caret>text`</selection>",
    3
  )

  @Test
  fun testDoubleInsideAll() = doTest(
    "`Some <caret>text`",
    "`<selection>Some <caret>text</selection>`",
    2
  )

  @Test
  fun testSingleMultilineInsideAll() = doTest(
    "`Some <caret>text\nother line?`",
    "`<selection>Some <caret>text\nother line?</selection>`",
    3
  )

  @Test
  fun testDoubleMultilineInsideAll() = doTest(
    "``Some <caret>text\nother line?``",
    "``<selection>Some <caret>text\nother line?</selection>``",
    3
  )

  @Test
  fun testAlmostFenceInsideAll() = doTest(
    "```Some <caret>text and the rest```",
    "```<selection>Some <caret>text and the rest</selection>```",
    2
  )

  @Test
  fun testAlmostFenceOutside() = doTest(
    "```Some <caret>text and the rest```",
    "<selection>```Some <caret>text and the rest```</selection>",
    3
  )

  @Test
  fun testWithSelected() = doTest(
    "`So<selection>me <caret>text and t</selection>he rest`",
    "`<selection>Some <caret>text and the rest</selection>`",
    1
  )

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
