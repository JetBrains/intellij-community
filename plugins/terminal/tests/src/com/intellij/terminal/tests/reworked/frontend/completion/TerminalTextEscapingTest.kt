package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.terminal.frontend.view.completion.escapeShellArgument
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.terminal.session.ShellName
import org.junit.Test

/** Ctrl+U. The shell line editor clears the command line. */
internal val KILL_LINE = Char(0x15)

/** Ctrl+O. The shell line editor runs the command line. */
internal val ACCEPT_LINE = Char(0x0F)

private val DELETE = Char(0x7F)

internal class TerminalTextEscapingTest {

  @Test
  fun `zsh escapes control characters with ANSI-C quotes`() {
    val escapedText = escapeShellArgument("zzbeta${KILL_LINE}id$ACCEPT_LINE", ShellName.ZSH)
    assertEquals("""zzbeta$'\025'id$'\017'""", escapedText)
  }

  @Test
  fun `bash escapes control characters with ANSI-C quotes`() {
    val escapedText = escapeShellArgument("zzbeta${KILL_LINE}id$ACCEPT_LINE", ShellName.BASH)
    assertEquals("""zzbeta$'\025'id$'\017'""", escapedText)
  }

  @Test
  fun `unix shells escape a tab and a new line with ANSI-C quotes`() {
    val escapedText = escapeShellArgument("a\tb\nc", ShellName.ZSH)
    assertEquals("""a$'\011'b$'\012'c""", escapedText)
  }

  @Test
  fun `unix shells escape a control character and a space in one value`() {
    val escapedText = escapeShellArgument("a$KILL_LINE b", ShellName.ZSH)
    assertEquals("""a$'\025'\ b""", escapedText)
  }

  @Test
  fun `unix shells escape the delete character`() {
    val escapedText = escapeShellArgument("a${DELETE}b", ShellName.ZSH)
    assertEquals("""a$'\177'b""", escapedText)
  }

  @Test
  fun `power shell removes control characters`() {
    val escapedText = escapeShellArgument("a${KILL_LINE}b", ShellName.POWERSHELL)
    assertEquals("'ab'", escapedText)
  }

  @Test
  fun `command prompt removes control characters`() {
    val escapedText = escapeShellArgument("a${KILL_LINE}b", ShellName.CMD)
    assertEquals("ab", escapedText)
  }

  @Test
  fun `a value without a special character is not changed`() {
    val escapedText = escapeShellArgument("plain.txt", ShellName.ZSH)
    assertEquals("plain.txt", escapedText)
  }
}
