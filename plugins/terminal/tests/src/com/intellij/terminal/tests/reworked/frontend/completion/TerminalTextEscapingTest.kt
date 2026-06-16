package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.terminal.frontend.view.completion.escapeShellArgument
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.terminal.session.ShellName
import org.junit.Test

internal class TerminalTextEscapingTest {
  val cmdShell = ShellName.of("cmd")

  @Test
  fun `command prompt leaves exclamation mark unescaped`() {
    val escapedText = escapeShellArgument("""C:\Users\me\file!.txt""", cmdShell)
    assertEquals("""C:\Users\me\file!.txt""", escapedText)
  }

  @Test
  fun `command prompt quotes paths with spaces`() {
    val escapedText = escapeShellArgument("""C:\Users\me\My File!.txt""", cmdShell)
    assertEquals(""""C:\Users\me\My File!.txt"""", escapedText)
  }

  @Test
  fun `command prompt quotes shell metacharacters`() {
    val escapedText = escapeShellArgument("""C:\Users\me\one&two.txt""", cmdShell)
    assertEquals(""""C:\Users\me\one&two.txt"""", escapedText)
  }

  @Test
  fun `command prompt escapes percent expansion`() {
    val escapedText = escapeShellArgument("""C:\Users\me\%profile%.txt""", cmdShell)
    assertEquals(""""C:\Users\me\^%profile^%.txt"""", escapedText)
  }

  @Test
  fun `command prompt escapes double quotes`() {
    val escapedText = escapeShellArgument("a\"b", cmdShell)
    assertEquals("\"a\"\"b\"", escapedText)
  }
}