// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownJoinQuoteLinesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test join lines without selection`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
      > This is a<caret>
      > block quote.
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
      myFixture.checkResult("> This is a block quote.")
    }
  }

  @Test
  fun `test join lines nested`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
      > This <selection>is a
      > multiline
      > > block quote
      > with five
      > li</selection>nes.
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
      myFixture.checkResult("> This is a multiline\n> > block quote\n> with five lines.")
    }
  }

  @Test
  fun `test join lines`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
      "test.md",
      """
      > This <selection>is a
      > multiline
      > block quote
      > with five
      > li</selection>nes.
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
      myFixture.checkResult("> This is a multiline block quote with five lines.")
    }
  }

  @Test
  fun `test join lines with remainder`(): Unit = timeoutRunBlocking {
    myFixture.configureByText(
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
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
      myFixture.checkResult("> This is a multiline block quote with five lines.\n> remains separate.")
    }
  }
}
