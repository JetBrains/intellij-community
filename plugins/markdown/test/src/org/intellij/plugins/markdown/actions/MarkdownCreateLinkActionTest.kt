// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import java.awt.datatransfer.StringSelection

class MarkdownCreateLinkActionTest : LightPlatformCodeInsightTestCase() {
  fun testCreateLinkFromSelectedTextPlacesCaretIntoDestination() {
    doCreateLinkTest(
      before = "Some <selection>read more</selection> text",
      after = "Some [read more](<caret>) text"
    )
  }

  fun testCreateLinkFromSelectedUrlPlacesCaretIntoText() {
    doCreateLinkTest(
      before = "Some <selection>https://example.com</selection> text",
      after = "Some [<caret>](https://example.com) text"
    )
  }

  fun testPasteUrlOverSelectedTextCreatesLink() {
    configureFromFileText("some.md", "Some <selection>read more</selection> text")
    CopyPasteManager.getInstance().setContents(StringSelection("https://example.com"))

    executeAction(IdeActions.ACTION_EDITOR_PASTE)

    checkResultByText("Some [read more](https://example.com)<caret> text")
  }

  private fun doCreateLinkTest(before: String, after: String) {
    configureFromFileText("some.md", before)
    CopyPasteManager.getInstance().setContents(StringSelection(""))

    executeAction("Markdown.Styling.CreateLink")

    checkResultByText(after)
  }
}
