// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.actions.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.removeUserData
import com.intellij.terminal.tests.block.util.TestTerminalPromptModel
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.IS_PROMPT_EDITOR_KEY
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalDeletePreviousWordTest : LightPlatformCodeInsightTestCase() {
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

  @Test
  fun `delete word until the start of the prompt text`() {
    doTest("someCommand<caret>", "<caret>", promptText = "prompt")
  }

  private var promptText: String = ""

  private fun doTest(commandBefore: String, commandAfter: String, promptText: String = "") {
    // Remember the prompt text to later use it for the prompt initialization
    this.promptText = promptText

    // Extension of the file is the same as the default extension of TerminalPromptFileType
    configureFromFileText("promptFile.prompt", promptText + commandBefore)

    executeAction("Terminal.DeletePreviousWord")
    checkResultByText(promptText + commandAfter)
  }

  /**
   * This method is called inside [configureFromFileText].
   * We have to put some values into the editor after its creation but before PSI parsing.
   */
  override fun createSaveAndOpenFile(relativePath: String, fileText: String): Editor {
    val editor = super.createSaveAndOpenFile(relativePath, fileText)

    val promptModel = TestTerminalPromptModel(editor as EditorEx, promptText)
    Disposer.register(testRootDisposable, promptModel)
    // Used in terminal actions
    editor.putUserData(TerminalPromptModel.KEY, promptModel)
    editor.putUserData(IS_PROMPT_EDITOR_KEY, true)

    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)!!
    // Used in TerminalPromptFileViewProvider
    virtualFile.putUserData(TerminalPromptModel.KEY, promptModel)
    Disposer.register(testRootDisposable) {
      virtualFile.removeUserData(TerminalPromptModel.KEY)
    }
    virtualFile.putUserData(ShellType.KEY, ShellType.ZSH)  // The shell type doesn't matter for this test
    return editor
  }
}
