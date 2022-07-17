// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.ui.scale.TestScaleHelper
import org.junit.AfterClass

class MarkdownTableColumnShrinkTest: LightPlatformCodeInsightTestCase() {
  fun `test right after cell content`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |  a <caret>   | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  a<caret>   | asd  |
      """.trimIndent()
    )
  }

  fun `test between spaces on the right side`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |  a   <caret> | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  a  <caret> | asd  |
      """.trimIndent()
    )
  }

  fun `test just before right pipe`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |  a    <caret>| asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  a   <caret>| asd  |
      """.trimIndent()
    )
  }

  fun `test right before cell content`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      |   <caret>a   | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |  <caret>a   | asd  |
      """.trimIndent()
    )
  }

  fun `test just after left pipe`() {
    // language=Markdown
    doTest(
      """
      | none  | none |
      |-------|------|
      | <caret>  a   | asd  |
      """.trimIndent(),
      """
      | none | none |
      |------|------|
      |<caret>  a   | asd  |
      """.trimIndent()
    )
  }

  fun `test in separator`() {
    doTest(
      """
      | none  | none |
      |---<caret>----|------|
      |  a    | asd  |
      """.trimIndent(),
      """
      | none | none |
      |--<caret>----|------|
      |  a   | asd  |
      """.trimIndent()
    )
  }

  fun `test in separator with colon`() {
    doTest(
      """
      | none  | none |
      |:<caret>------|------|
      | a     | asd  |
      """.trimIndent(),
      """
      | none | none |
      |<caret>------|------|
      | a    | asd  |
      """.trimIndent()
    )
  }

  private fun doTest(content: String, expected: String, count: Int = 1) {
    TableTestUtils.runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      repeat(count) {
        backspace()
      }
      checkResultByText(expected)
    }
  }
}
