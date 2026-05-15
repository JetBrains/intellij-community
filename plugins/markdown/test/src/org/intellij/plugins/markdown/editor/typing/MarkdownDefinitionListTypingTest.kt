// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownDefinitionListTypingTest : LightPlatformCodeInsightTestCase() {

  // Repro: in a fresh .md file, type 'a', Enter, ':', Space.
  // Reported symptom: typing throws afterwards and the file can no longer be opened.
  @Test
  fun `test typing colon and space after enter does not throw`() {
    configureFromFileText("some.md", "<caret>")
    type("a\n: ")
    type('x')
    checkResultByText("a\n: x<caret>")
  }

  // Repro: term line "Note:" followed by an empty definition ": " on the next line,
  // then type a character after the trailing space.
  // Reported symptom: "An IDE error occurred", highlighting dies, typed chars disappear.
  @Test
  fun `test typing after empty definition following term line does not throw`() {
    configureFromFileText("some.md", "Note:\n<caret>")
    type(": ")
    type('x')
    checkResultByText("Note:\n: x<caret>")
  }
}
