// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.formatter

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.autodetect.IndentAutoDetectionTestUtil.doTestIndentSizeFromText
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownIndentDetectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test nested list indent is detected correctly`() {
    doTestIndentSizeFromText(
      myFixture,
      "a.md",
      """
        - Playback:
            - first
            - second
            - third
        - Recording:
            - fourth
            - fifth
      """.trimIndent(),
      null,
      4
    )
  }

  @Test
  fun `test one-item nested list indent is detected correctly`() {
    doTestIndentSizeFromText(
      myFixture,
      "a.md",
      """
        - parent:
            - child
        - sibling
      """.trimIndent(),
      CommonCodeStyleSettings.IndentOptions().apply { INDENT_SIZE = 2 },
      4
    )
  }
}
