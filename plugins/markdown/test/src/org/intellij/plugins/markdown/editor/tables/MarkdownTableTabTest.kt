// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.ui.scale.TestScaleHelper
import org.intellij.plugins.markdown.editor.tables.TableTestUtils.runWithChangedSettings

class MarkdownTableTabTest: LightPlatformCodeInsightTestCase() {
  override fun tearDown() {
    TestScaleHelper.restoreRegistryProperties()
    super.tearDown()
  }

  fun `test single tab forward`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | <caret>some |
    """.trimIndent()
    doTest(before, after)
  }

  fun `test multiple tabs forward`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some | some | <caret>some |
    """.trimIndent()
    doTest(before, after, count = 2)
  }

  fun `test multiple tabs forward to next row`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    | some | some | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some | some | some |
    | some | some | <caret>some |
    """.trimIndent()
    doTest(before, after, count = 5)
  }

  fun `test single tab backward`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    doTest(before, after, forward = false)
  }

  fun `test multiple tabs backward`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some | some | some<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    """.trimIndent()
    doTest(before, after, count = 2, forward = false)
  }

  fun `test multiple tabs backward to previous row`() {
    // language=Markdown
    val before = """
    | none | none | none |
    |------|------|------|
    | some | some | some |
    | some | some | some<caret> |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none | none |
    |------|------|------|
    | some<caret> | some | some |
    | some | some | some |
    """.trimIndent()
    doTest(before, after, count = 5, forward = false)
  }

  private fun doTest(content: String, expected: String, count: Int = 1, forward: Boolean = true) {
    TestScaleHelper.setRegistryProperty("markdown.tables.editing.support.enable", "true")
    runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      repeat(count) {
        when {
          forward -> executeAction("EditorTab")
          else -> executeAction("EditorUnindentSelection")
        }
      }
      checkResultByText(expected)
    }
  }
}
