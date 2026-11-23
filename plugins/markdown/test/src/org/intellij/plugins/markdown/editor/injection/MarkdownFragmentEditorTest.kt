// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownFragmentEditorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test new last line`() {
    doTest(
      """
        ```text
        one
        two<caret>
        ```
      """.trimIndent(),
      """
        ```text
        one
        two
        three
        ```
      """.trimIndent()
    )
  }

  @Test
  fun `test new last line in list item`() {
    doTest(
      """
        - item

          ```text
          one
          two<caret>
          ```
      """.trimIndent(),
      """
        - item

          ```text
          one
          two
          three
          ```
      """.trimIndent()
    )
  }

  private fun doTest(before: String, after: String) {
    myFixture.configureByText("test.md", before)
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val fragmentEditor = InjectionTestFixture(myFixture).openInFragmentEditor()
        fragmentEditor.type("\nthree")
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
        myFixture.checkResult(after)
      }
    }
  }
}
