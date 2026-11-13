// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.typeInEditor
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownDefinitionListTypingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  // Repro: in a fresh .md file, type 'a', Enter, ':', Space.
  // Reported symptom: typing throws afterwards and the file can no longer be opened.
  @Test
  fun `test typing colon and space after enter does not throw`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor("a\n: ")
      myFixture.typeInEditor('x')
      myFixture.checkResult("a\n: x<caret>")
    }
  }

  // Repro: term line "Note:" followed by an empty definition ": " on the next line,
  // then type a character after the trailing space.
  // Reported symptom: "An IDE error occurred", highlighting dies, typed chars disappear.
  @Test
  fun `test typing after empty definition following term line does not throw`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "Note:\n<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor(": ")
      myFixture.typeInEditor('x')
      myFixture.checkResult("Note:\n: x<caret>")
    }
  }
}
