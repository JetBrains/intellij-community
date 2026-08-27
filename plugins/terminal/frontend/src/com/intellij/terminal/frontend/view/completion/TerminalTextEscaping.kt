package com.intellij.terminal.frontend.view.completion

import com.intellij.execution.CommandLineUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.ShellName

internal fun needsShellEscaping(shellName: ShellName, value: String): Boolean {
  val charsToEscape = when {
    ShellName.isPowerShell(shellName) -> POWERSHELL_CHARS_TO_ESCAPE
    ShellName.isCommandPrompt(shellName) -> COMMAND_PROMPT_CHARS_TO_ESCAPE
    else -> UNIX_SHELLS_CHARS_TO_ESCAPE
  }
  return value.any { it in charsToEscape || it.isControlCharacter }
}

@ApiStatus.Internal
fun escapeShellArgument(argument: String, shellName: ShellName): String {
  if (!needsShellEscaping(shellName, argument)) return argument

  return when {
    // A control character has no literal form inside single quotes, so remove it.
    ShellName.isPowerShell(shellName) -> quotePowerShellArgument(argument.removeControlCharacters())
    ShellName.isCommandPrompt(shellName) -> CommandLineUtil.escapeParameterOnWindows(argument.removeControlCharacters(), true)
    else -> escapeUnixShellArgument(argument)
  }
}

private fun quotePowerShellArgument(argument: String): String {
  return "'${escapePowerShellSingleQuoted(argument)}'"
}

internal fun escapePowerShellSingleQuoted(value: String): String {
  return value.replace("'", "''")
}

internal fun escapeUnixShellArgument(argument: String): String {
  return buildString {
    for (ch in argument) {
      when {
        ch.isControlCharacter -> append(ch.toAnsiCQuote())
        ch in UNIX_SHELLS_CHARS_TO_ESCAPE -> append('\\').append(ch)
        else -> append(ch)
      }
    }
  }
}

/**
 * Replaces every control character with the ANSI-C quote that bash and zsh understand.
 * Use it also to show the value to the user, so the popup and the command line match.
 */
internal fun String.escapeControlCharacters(): String {
  if (none { it.isControlCharacter }) return this
  return buildString {
    for (ch in this@escapeControlCharacters) {
      if (ch.isControlCharacter) append(ch.toAnsiCQuote()) else append(ch)
    }
  }
}

internal fun String.removeControlCharacters(): String = filterNot { it.isControlCharacter }

/**
 * A control character must never reach the shell.
 * The shell line editor handles it as a key press, not as text.
 * For example, `0x15` clears the command line, and `0x0F` runs it.
 *
 * The C1 range `0x80`..`0x9F` is not included on purpose.
 * We write the text as UTF-8, so those characters never become a control byte.
 */
private val Char.isControlCharacter: Boolean
  get() = code < 0x20 || code == 0x7F

/** Returns `$'\025'` for `0x15`. The shell writes the octal code as one byte, so only an ASCII code is correct here. */
private fun Char.toAnsiCQuote(): String {
  val octalCode = code.toString(8).padStart(3, '0')
  return $$"$'\\$$octalCode'"
}

private const val POWERSHELL_CHARS_TO_ESCAPE = " \n\t\r`$'\"(){}[]<>|;&,@#"

private const val COMMAND_PROMPT_CHARS_TO_ESCAPE = " \n\t\r\"()<>|^&%"

/** A control character is escaped by [escapeUnixShellArgument] itself, so `\n`, `\t` and `\r` are not listed here. */
private const val UNIX_SHELLS_CHARS_TO_ESCAPE = " `$'\"(){}[]<>|;&!*?\\"
