// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.actions

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.IS_PROMPT_EDITOR_KEY
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TerminalDeletePreviousWordTest : LightPlatformCodeInsightTestCase() {
  @Test
  fun `delete word and stop at dash`() {
    doTest("cmd -s --longOpt<caret>", "cmd -s --<caret>")
  }

  @Test
  fun `delete word and stop at space`() {
    doTest("git commit<caret>", "git <caret>")
  }

  @Test
  fun `delete word and stop at slash`() {
    doTest("cd dir1/dir2<caret>", "cd dir1/<caret>")
  }

  @Test
  fun `delete word and stop on line break`() {
    val before = """cd dir/\
                   |otherDir<caret>""".trimMargin()
    val after = """cd dir/\
                  |<caret>""".trimMargin()
    doTest(before, after)
  }

  @Test
  fun `delete delimiters and word after them`() {
    doTest("ls -la //<caret>", "ls -<caret>")
  }

  @Test
  fun `delete part of the word`() {
    doTest("git comm<caret>it file", "git <caret>it file")
  }

  @Test
  fun `delete whole line`() {
    doTest("someLongCommandWithoutDelimiters<caret>", "<caret>")
  }

  @Test
  fun `delete nothing`() {
    doTest("<caret>cmd", "<caret>cmd")
  }

  private fun doTest(textBefore: String, textAfter: String) {
    configureFromFileText("prompt.sh", textBefore)
    editor.putUserData(IS_PROMPT_EDITOR_KEY, true)
    executeAction("Terminal.DeletePreviousWord")
    checkResultByText(textAfter)
  }
}