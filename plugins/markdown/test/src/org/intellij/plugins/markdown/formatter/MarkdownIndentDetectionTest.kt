// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.formatter

import com.intellij.psi.autodetect.AbstractIndentAutoDetectionTest
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownIndentDetectionTest : AbstractIndentAutoDetectionTest() {
  override fun getFileNameWithExtension(): String = "a.md"
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH

  fun `test nested list indent is detected correctly`() {
    doTestIndentSizeFromText(
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

  fun `test one-item nested list indent is detected correctly`() {
    doTestIndentSizeFromText(
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
