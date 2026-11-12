// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator

import org.junit.jupiter.api.Test

/**
 * A resize while the shell waits at its prompt, followed by the redraw that the shell sends on `SIGWINCH`.
 *
 * The shell does not know that the terminal reflows the prompt. It moves the cursor up by the number of rows that the
 * prompt took at the old width, erases them, and prints the prompt again. So the redraw lands on the first prompt row
 * only when the resize keeps the cursor at the same row offset from that row.
 *
 * The semantic prompt marks (OSC 133) tell the engine which rows are the prompt. `redraw=1` tells it that the shell
 * redraws them, so the engine keeps each prompt row as one blank row. The marks are the ones that the IDE shell
 * integrations send. The redraw bytes are the ones that zsh 5.9 and bash 5.3 send. See IJPL-254160.
 */
class PromptRedrawOnResizeTest {

  /** The symptom of IJPL-254160: the reflowed prompt rows above the cursor survive the redraw. */
  @Test
  fun zshNarrowingBelowThePromptWidthKeepsOnePrompt() = session(40, 6) { session ->
    session.write("out1\r\nout2\r\n").write(zshPrompt())
    session.assertCursorPosition(33, 3)

    session.resize(12, 6)
    session.write(zshRedraw(oldPromptRows = 1))

    session.assertScreenLines("out1", "out2", "$ spring-pet", "clinic git:(", "main) x ")
    session.assertScrollbackLines()
    session.assertCursorPosition(9, 5)
  }

  /** The mirror image: the join moves the cursor up, so the redraw overwrites the output above the prompt. */
  @Test
  fun zshWideningToFitThePromptKeepsTheOutputAbove() = session(12, 6) { session ->
    session.write("out1\r\nout2\r\n").write(zshPrompt())
    session.assertScreenLines("out1", "out2", "$ spring-pet", "clinic git:(", "main) x ")
    session.assertCursorPosition(9, 5)

    session.resize(40, 6)
    session.write(zshRedraw(oldPromptRows = 3))

    session.assertScreenLines("out1", "out2", PROMPT)
    session.assertScrollbackLines()
    session.assertCursorPosition(33, 3)
  }

  /**
   * The scenario of IJPL-254160: both dimensions change, there and back. The engine changes the rows before the
   * columns on a shrink and after them on a growth, so the prompt handling must hold in both orders.
   */
  @Test
  fun zshShrinkingAndGrowingBackDiagonallyKeepsThePromptAndTheOutput() = session(40, 8) { session ->
    session.write("out1\r\nout2\r\n").write(zshPrompt())

    session.resize(12, 6)
    session.write(zshRedraw(oldPromptRows = 1))

    session.assertScreenLines("out1", "out2", "$ spring-pet", "clinic git:(", "main) x ")
    session.assertScrollbackLines()
    session.assertCursorPosition(9, 5)

    session.resize(40, 8)
    session.write(zshRedraw(oldPromptRows = 3))

    session.assertScreenLines("out1", "out2", PROMPT)
    session.assertScrollbackLines()
    session.assertCursorPosition(33, 3)
  }

  /**
   * bash moves up over all rows of the last prompt line, like zsh. So `redraw=last` does not fit: the engine keeps only
   * the cursor row for it, and the other rows of the line join and move the cursor up.
   */
  @Test
  fun bashWideningToFitThePromptKeepsTheOutputAbove() = session(12, 6) { session ->
    session.write("out1\r\nout2\r\n").write(BASH_PROMPT_START + bashLastPromptLine())
    session.assertScreenLines("out1", "out2", "$ spring-pet", "clinic git:(", "main) x ")
    session.assertCursorPosition(9, 5)

    session.resize(40, 6)
    session.write(bashRedraw(oldPromptRows = 3))

    session.assertScreenLines("out1", "out2", PROMPT)
    session.assertScrollbackLines()
    session.assertCursorPosition(33, 3)
  }

  /**
   * bash redraws only the last line of a multi-line prompt. The bash integration marks the start of each prompt line,
   * so the engine keeps and clears only the rows of the last line, and the line above stays.
   */
  @Test
  fun bashKeepsTheUpperLineOfATwoLinePrompt() = session(40, 6) { session ->
    session.write("out1\r\n").write(BASH_PROMPT_START + osc("133;P;k=i") + "~/src\r\n" + bashLastPromptLine())
    session.assertScreenLines("out1", "~/src", PROMPT)

    session.resize(12, 6)
    session.write(bashRedraw(oldPromptRows = 1))

    session.assertScreenLines("out1", "~/src", "$ spring-pet", "clinic git:(", "main) x ")
    session.assertScrollbackLines()
    session.assertCursorPosition(9, 5)

    session.resize(40, 6)
    session.write(bashRedraw(oldPromptRows = 3))

    session.assertScreenLines("out1", "~/src", PROMPT)
    session.assertScrollbackLines()
    session.assertCursorPosition(33, 3)
  }

  /**
   * PSReadLine does not redraw the prompt after a resize, so the PowerShell integration sends `redraw=0`. The engine
   * keeps the last `redraw` value for the whole session. Here zsh starts PowerShell, so without `redraw=0` the engine
   * would clear the PowerShell prompt, and nothing would draw it again.
   */
  @Test
  fun promptWithoutRedrawKeepsItsReflowedText() = session(40, 6) { session ->
    session.write(osc("133;A;redraw=1") + "% " + osc("133;B") + "pwsh\r\n" + osc("133;C"))
    session.write(osc("133;A;redraw=0") + "PS /Users/me/spring-petclinic> " + osc("133;B"))
    session.assertCursorPosition(32, 2)

    session.resize(12, 6)

    session.assertScreenLines("% pwsh", "PS /Users/me", "/spring-petc", "linic> ")
    session.assertScrollbackLines()
    session.assertCursorPosition(8, 4)
  }

  /**
   * Without the marks, the engine does not know where the prompt is, so it reflows the prompt and the redraw corrupts
   * it. This is the known limit for a shell without the IDE integration, for example zsh with Powerlevel10k.
   */
  @Test
  fun withoutPromptMarksTheRedrawDuplicatesThePrompt() = session(40, 6) { session ->
    session.write("out1\r\nout2\r\n").write(PROMPT)

    session.resize(12, 6)
    session.write(zshRedraw(oldPromptRows = 1, prompt = PROMPT))

    session.assertScreenLines("out2", "$ spring-pet", "clinic git:(", "$ spring-pet", "clinic git:(", "main) x ")
    session.assertScrollbackLines("out1")
    session.assertCursorPosition(9, 6)
  }
}

/** The oh-my-zsh `robbyrussell` prompt of the issue, in ASCII. */
private const val PROMPT: String = "$ spring-petclinic git:(main) x "

/** [PROMPT] between the marks that the zsh integration puts into `PS1`, so a redraw prints them again. */
private fun zshPrompt(): String = osc("133;A;redraw=1") + PROMPT + osc("133;B")

/**
 * What zsh 5.9 sends on `SIGWINCH` when the prompt took [oldPromptRows] rows at the old width: it moves up to the
 * first prompt row, erases to the end of the screen, and prints [prompt] again.
 */
private fun zshRedraw(oldPromptRows: Int, prompt: String = zshPrompt()): String =
  "\r\r" + csi("A").repeat(oldPromptRows - 1) + csi("0m") + csi("27m") + csi("24m") + csi("J") + prompt

/** The mark that the bash integration prints from `PROMPT_COMMAND`, before bash prints `PS1`. */
private val BASH_PROMPT_START: String = osc("133;A;redraw=1")

/** The last line of `PS1` with the marks of the bash integration. A redraw prints only this line. */
private fun bashLastPromptLine(): String = osc("133;P;k=i") + PROMPT + osc("133;B")

/**
 * What bash 5.3 sends on `SIGWINCH` when the last prompt line took [oldPromptRows] rows at the old width: it erases
 * these rows from the bottom up and prints the last prompt line again.
 */
private fun bashRedraw(oldPromptRows: Int): String =
  "\r" + csi("K") + ("\r" + csi("A") + csi("K")).repeat(oldPromptRows - 1) + "\r" + bashLastPromptLine()
