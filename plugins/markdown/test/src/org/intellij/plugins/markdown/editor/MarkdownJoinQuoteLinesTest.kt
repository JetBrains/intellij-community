// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class MarkdownJoinQuoteLinesTest : LightPlatformCodeInsightTestCase() {
  fun `test join lines without selection`() {
    configureFromFileText(
      "test.md",
      """
      > This is a<caret>
      > block quote.
      """.trimIndent()
    )
    executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
    checkResultByText("> This is a block quote.")
  }

  fun `test join lines nested`() {
    configureFromFileText(
      "test.md",
      """
      > This <selection>is a
      > multiline
      > > block quote
      > with five
      > li</selection>nes.
      """.trimIndent()
    )
    executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
    checkResultByText("> This is a multiline\n> > block quote\n> with five lines.")
  }

  fun `test join lines`() {
    configureFromFileText(
      "test.md",
      """
      > This <selection>is a
      > multiline
      > block quote
      > with five
      > li</selection>nes.
      """.trimIndent()
    )
    executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
    checkResultByText("> This is a multiline block quote with five lines.")
  }

  fun `test join lines with remainder`() {
    configureFromFileText(
      "test.md",
      """
      > This <selection>is a
      > multiline
      > block quote
      > with five
      > li</selection>nes.
      > remains separate.
      """.trimIndent()
    )
    executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
    checkResultByText("> This is a multiline block quote with five lines.\n> remains separate.")
  }
}
