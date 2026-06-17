package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.terminal.frontend.view.completion.escapeShellArgument
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.terminal.session.ShellName
import org.junit.Test

internal class TerminalCmdArgumentsEscapingTest {

  @Test
  fun `command prompt quotes exclamation mark`() {
    val escapedText = escapeShellArgument("""C:\Users\me\file!.txt""", ShellName.CMD)
    assertEquals("""C:\Users\me\file"!.txt"""", escapedText)
  }

  @Test
  fun `command prompt quotes paths with spaces`() {
    val escapedText = escapeShellArgument("""C:\Users\me\My File!.txt""", ShellName.CMD)
    assertEquals(""""C:\Users\me\My File!.txt"""", escapedText)
  }

  @Test
  fun `command prompt quotes shell metacharacters`() {
    val escapedText = escapeShellArgument("""C:\Users\me\one&two.txt""", ShellName.CMD)
    assertEquals("""C:\Users\me\one"&two.txt"""", escapedText)
  }

  @Test
  fun `command prompt escapes percent expansion`() {
    val escapedText = escapeShellArgument("""C:\Users\me\%profile%.txt""", ShellName.CMD)
    assertEquals(""""C:\Users\me\%profile%.txt"""", escapedText)
  }

  @Test
  fun `command prompt escapes double quotes`() {
    val escapedText = escapeShellArgument("a\"b", ShellName.CMD)
    assertEquals("\"a\\\"b\"", escapedText)
  }

  @Test
  fun `command prompt quotes wildcard and shell special characters`() {
    val escapedText = escapeShellArgument("a*b?[c]~d'e", ShellName.CMD)
    assertEquals(""""a*b?[c]~d'e"""", escapedText)
  }
}