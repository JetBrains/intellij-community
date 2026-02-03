// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.actions

import com.intellij.testFramework.LightPlatformCodeInsightTestCase

@Suppress("unused")
class MarkdownStylingActionsConsistencyTest {
  abstract class BaseTest(
    private val actionId: String,
    private val wrapPrefix: String,
    private val wrapSuffix: String = wrapPrefix
  ): LightPlatformCodeInsightTestCase() {
    fun `test single word`() {
      // language=Markdown
      val content = """
      Some <selection>arbitrary</selection> text
      Some other line
      """.trimIndent()
      // language=Markdown
      val applied = """
      Some $wrapPrefix<selection>arbitrary</selection>$wrapSuffix text
      Some other line
      """.trimIndent()
      configureFromFileText("some.md", content)
      executeAction(actionId)
      checkResultByText(applied)
      executeAction(actionId)
      checkResultByText(content)
    }

    fun `test whole line`() {
      // language=Markdown
      val content = """
      <selection>Some arbitrary text</selection>
      Some other line
      """.trimIndent()
      // language=Markdown
      val applied = """
      $wrapPrefix<selection>Some arbitrary text</selection>$wrapSuffix
      Some other line
      """.trimIndent()
      configureFromFileText("some.md", content)
      executeAction(actionId)
      checkResultByText(applied)
      executeAction(actionId)
      checkResultByText(content)
    }

    //fun `test whole line selected by action`() {
    //  // language=Markdown
    //  val content = """
    //  Some arbitrary<caret> text
    //  Some other line
    //  """.trimIndent()
    //  // language=Markdown
    //  val applied = """
    //  $wrapPrefix<selection>Some arbitrary text$wrapSuffix
    //  <caret></selection>Some other line
    //  """.trimIndent()
    //  configureFromFileText("some.md", content)
    //  executeAction("EditorSelectLine")
    //  executeAction(actionId)
    //  checkResultByText(applied)
    //  executeAction(actionId)
    //  checkResultByText(content)
    //}
  }

  class BoldActionConsistency: BaseTest("org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction", "**")

  class ItalicActionConsistency: BaseTest("org.intellij.plugins.markdown.ui.actions.styling.ToggleItalicAction", "_")

  class StrikethroughActionConsistency: BaseTest(
    "org.intellij.plugins.markdown.ui.actions.styling.ToggleStrikethroughAction",
    "~~"
  )
}