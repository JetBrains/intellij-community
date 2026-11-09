// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionTestFixture

class MarkdownFragmentEditorTest : BasePlatformTestCase() {
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
    val fragmentEditor = InjectionTestFixture(myFixture).openInFragmentEditor()
    fragmentEditor.type("\nthree")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    myFixture.checkResult(after)
  }
}
