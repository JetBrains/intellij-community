// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalCursorMove
import com.intellij.terminal.frontend.view.impl.TerminalCursorMove.Direction.LEFT
import com.intellij.terminal.frontend.view.impl.TerminalCursorMove.Direction.RIGHT
import com.intellij.terminal.frontend.view.impl.calculateCursorMoveToClick
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.updateCursor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Checks the text rules of [calculateCursorMoveToClick].
 * The clicks in a real terminal view are in [TerminalMouseEventsHandlingTest.PromptClicks].
 */
@RunWith(JUnit4::class)
internal class TerminalPromptClickHandlerTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `click before the cursor moves the cursor left`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "ls -la")
    fixture.model.updateCursor(0, 14)

    assertEquals(TerminalCursorMove(LEFT, 4), fixture.calculateMove(clickOffset = 10))
  }

  @Test
  fun `click after the cursor moves the cursor right`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "ls -la")
    fixture.model.updateCursor(0, 8)

    assertEquals(TerminalCursorMove(RIGHT, 3), fixture.calculateMove(clickOffset = 11))
  }

  @Test
  fun `click at the cursor does not move the cursor`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "ls -la")
    fixture.model.updateCursor(0, 10)

    assertEquals(null, fixture.calculateMove(clickOffset = 10))
  }

  @Test
  fun `click in the prompt moves the cursor to the command start`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "ls -la")
    fixture.model.updateCursor(0, 14)

    assertEquals(TerminalCursorMove(LEFT, 6), fixture.calculateMove(clickOffset = 3))
  }

  @Test
  fun `click in the spaces that the user typed at the end moves the cursor there`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val fixture = createFixture()
      fixture.typeCommand(prompt = "prompt> ", command = "ls   ")
      fixture.model.updateCursor(0, 8)

      assertEquals(TerminalCursorMove(RIGHT, 4), fixture.calculateMove(clickOffset = 12))
    }

  @Test
  fun `one key press moves the cursor over a surrogate pair`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "echo ${GRINNING_FACE}x")
    fixture.model.updateCursor(0, 16)

    assertEquals(TerminalCursorMove(LEFT, 2), fixture.calculateMove(clickOffset = 13))
  }

  @Test
  fun `one key press moves the cursor over a double-width character`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "echo ${HANGUL_HAN}x")
    fixture.model.updateCursor(0, 15)

    assertEquals(TerminalCursorMove(LEFT, 2), fixture.calculateMove(clickOffset = 13))
  }

  @Test
  fun `combining mark does not need its own key press`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "cafe${COMBINING_ACUTE_ACCENT}x")
    fixture.model.updateCursor(0, 14)

    assertEquals(TerminalCursorMove(LEFT, 2), fixture.calculateMove(clickOffset = 11))
  }

  @Test
  fun `zero width joiner does not need its own key press`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "$MAN$ZERO_WIDTH_JOINER${WOMAN}x")
    fixture.model.updateCursor(0, 14)

    assertEquals(TerminalCursorMove(LEFT, 3), fixture.calculateMove(clickOffset = 8))
  }

  @Test
  fun `click on the prompt line above the command does not move the cursor`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "~/dir\n> ", command = "ls -la")
    fixture.model.updateCursor(1, 8)

    assertEquals(null, fixture.calculateMove(clickOffset = 2))
  }

  @Test
  fun `click on the line below the command does not move the cursor`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "> ", command = "ls", textBelow = "\nfile1 file2")
    fixture.model.updateCursor(0, 4)

    assertEquals(null, fixture.calculateMove(clickOffset = 7))
  }

  @Test
  fun `cursor on the continuation line of the command does not move`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "> ", command = "echo \\\n> abc")
    fixture.model.updateCursor(1, 5)

    assertEquals(null, fixture.calculateMove(clickOffset = 11))
  }

  @Test
  fun `running command does not move the cursor`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.typeCommand(prompt = "prompt> ", command = "ls -la")
    fixture.model.updateCursor(0, 14)
    fixture.shellIntegration.onCommandStarted(TerminalOffset.of(14), "ls -la")

    assertEquals(null, fixture.calculateMove(clickOffset = 10))
  }

  @Test
  fun `prompt without the finish mark does not move the cursor`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixture = createFixture()
    fixture.shellIntegration.onPromptStarted(TerminalOffset.ZERO)
    fixture.model.update(0, "prompt> ls -la")
    fixture.model.updateCursor(0, 14)

    assertEquals(null, fixture.calculateMove(clickOffset = 10))
  }

  private fun createFixture(): Fixture {
    val scope = terminalProjectScope(project).childScope("TerminalPromptClickHandlerTest")
    Disposer.register(testRootDisposable) { scope.cancel() }
    val model = TerminalTestUtil.createOutputModel()
    val shellIntegration = TerminalShellIntegrationImpl(model, TerminalSessionModelImpl(), scope, LocalEelDescriptor, ShellName.of("zsh"))
    return Fixture(model, shellIntegration)
  }

  private class Fixture(
    val model: MutableTerminalOutputModel,
    val shellIntegration: TerminalShellIntegrationImpl,
  ) {
    /** Prints the prompt between the prompt marks, then the typed command, like the shell does. */
    suspend fun typeCommand(prompt: String, command: String, textBelow: String = "") {
      shellIntegration.onPromptStarted(model.startOffset)
      model.update(0, prompt)
      shellIntegration.onPromptFinished(model.endOffset)
      model.update(0, prompt + command + textBelow)
    }

    fun calculateMove(clickOffset: Long): TerminalCursorMove? {
      val block = shellIntegration.blocksModel.activeBlock as TerminalCommandBlock
      return calculateCursorMoveToClick(model, block, TerminalOffset.of(clickOffset))
    }
  }

  companion object {
    /** An emoji outside the BMP, so it takes two UTF-16 chars. */
    private val GRINNING_FACE = Character.toString(0x1F600)

    /** The Hangul syllable "han", a double-width character. */
    private val HANGUL_HAN = Char(0xD55C)

    private val COMBINING_ACUTE_ACCENT = Char(0x0301)

    private val MAN = Character.toString(0x1F468)
    private val WOMAN = Character.toString(0x1F469)
    private val ZERO_WIDTH_JOINER = Char(0x200D)
  }
}
