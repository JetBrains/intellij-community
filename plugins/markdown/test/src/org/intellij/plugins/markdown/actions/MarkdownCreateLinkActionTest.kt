// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.junit.jupiter.api.Test
import java.awt.datatransfer.StringSelection

@TestApplication
class MarkdownCreateLinkActionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun testCreateLinkFromSelectedTextPlacesCaretIntoDestination() {
    doCreateLinkTest(
      before = "Some <selection>read more</selection> text",
      after = "Some [read more](<caret>) text"
    )
  }

  @Test
  fun testCreateLinkFromSelectedUrlPlacesCaretIntoText() {
    doCreateLinkTest(
      before = "Some <selection>https://example.com</selection> text",
      after = "Some [<caret>](https://example.com) text"
    )
  }

  @Test
  fun testPasteUrlOverSelectedTextCreatesLink(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "Some <selection>read more</selection> text")
    withContext(Dispatchers.EDT) {
      CopyPasteManager.getInstance().setContents(StringSelection("https://example.com"))

      myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE)

      myFixture.checkResult("Some [read more](https://example.com)<caret> text")
    }
  }

  private fun doCreateLinkTest(before: String, after: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", before)
    withContext(Dispatchers.EDT) {
      CopyPasteManager.getInstance().setContents(StringSelection(""))

      myFixture.executeAction("Markdown.Styling.CreateLink")

      myFixture.checkResult(after)
    }
  }
}
