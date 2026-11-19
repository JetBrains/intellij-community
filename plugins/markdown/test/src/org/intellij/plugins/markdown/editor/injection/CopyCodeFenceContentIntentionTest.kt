// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor

@TestApplication
class CopyCodeFenceContentIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `list indent is removed with the caret on the opening line`() {
    // language=Markdown
    val content = """
    - item

      ```<caret>
      first
        second
      ```
    """.trimIndent()
    doTest(content, "first\n  second")
  }

  @Test
  fun `block quote marks are removed with the caret on the closing line`() {
    // language=Markdown
    val content = """
    > ```
    > first
    >   second
    > ```<caret>
    """.trimIndent()
    doTest(content, "first\n  second")
  }

  @Test
  fun `intention is available in the injected content`() {
    // language=Markdown
    val content = """
    - item

      ```json
      {
        "a": <caret>1
      }
      ```
    """.trimIndent()
    doTest(content, "{\n  \"a\": 1\n}")
  }

  @Test
  fun `intention is not available outside of a code fence`() {
    myFixture.configureByText("some.md", "Some te<caret>xt")
    Assertions.assertTrue(myFixture.filterAvailableIntentions(intentionText).isEmpty())
  }

  private fun doTest(content: String, expected: String) {
    myFixture.configureByText("some.md", content)
    val intention = myFixture.findSingleIntention(intentionText)
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        myFixture.launchAction(intention)
      }
    }
    Assertions.assertEquals(expected, CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor))
  }

  private val intentionText
    get() = MarkdownBundle.message("markdown.copy.code.fence.content.intention.text")
}
