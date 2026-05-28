package com.intellij.terminal.frontend.view.completion

import org.jetbrains.plugins.terminal.session.ShellName

internal fun needsShellEscaping(shellName: ShellName, value: String): Boolean {
  val charsToEscape = if (ShellName.isPowerShell(shellName)) POWERSHELL_CHARS_TO_ESCAPE else UNIX_SHELLS_CHARS_TO_ESCAPE
  return value.any { it in charsToEscape }
}

internal fun escapeShellArgument(argument: String, shellName: ShellName): String {
  if (!needsShellEscaping(shellName, argument)) return argument
  return if (ShellName.isPowerShell(shellName)) quotePowerShellArgument(argument) else escapeUnixShellArgument(argument)
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
      if (ch in UNIX_SHELLS_CHARS_TO_ESCAPE) {
        append("\\")
      }
      append(ch)
    }
  }
}

private const val POWERSHELL_CHARS_TO_ESCAPE = " \n\t\r`$'\"(){}[]<>|;&,@#"

private const val UNIX_SHELLS_CHARS_TO_ESCAPE = " \n\t\r`$'\"(){}[]<>|;&*?\\"
