// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shell_integration

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.util.PathUtil
import com.intellij.util.system.OS
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.createEnvVariablesMap
import org.jetbrains.plugins.terminal.util.ShellNameUtil

/**
 * Windows 10 only.
 *
 * Allows configuring the [ShellStartupOptions] to show the message on PowerShell startup
 * with a proposal to update the PSReadLine version to 2.0.3+.
 * To fix the problem with text background rendering:
 * https://learn.microsoft.com/windows/terminal/troubleshooting#black-lines-in-powershell-51-6x-70
 */
internal object TerminalPSReadLineUpdateUtil {
  private const val ASK_UPDATE_ENV = "__JETBRAINS_INTELLIJ_ASK_PSREADLINE_UPDATE"

  private const val TEXT_LINE_1_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_1"
  private const val TEXT_LINE_2_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_2"
  private const val TEXT_LINE_3_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_3"
  private const val IDE_NAME_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_IDE_NAME"

  @JvmStatic
  fun configureOptions(options: ShellStartupOptions): ShellStartupOptions {
    if (!isPowerShell(options)) {
      return options
    }

    val os = OS.CURRENT
    val isWin10 = os == OS.Windows && os.isAtLeast(10, 0) && !os.isAtLeast(11, 0)
    val alreadyDisabled = options.envVariables[ASK_UPDATE_ENV].equals("false", ignoreCase = true)
    // Show the message only if it is Win10 and the user didn't specify skipping it in the terminal settings (via env vars).
    return if (isWin10 && !alreadyDisabled) {
      configureAskingForUpdate(options)
    }
    else options
  }

  private fun configureAskingForUpdate(options: ShellStartupOptions): ShellStartupOptions {
    val newEnvs = createEnvVariablesMap(options.envVariables)
    newEnvs[ASK_UPDATE_ENV] = "true"
    addLocalizationEnvVars(newEnvs)
    return options.builder().envVariables(newEnvs).build()
  }

  // Pass the localized text of update proposal in env variables.
  private fun addLocalizationEnvVars(map: MutableMap<String, String>) {
    // The exact parameter values will be substituted in the PowerShell scripts.
    map[TEXT_LINE_1_ENV] = TerminalBundle.message("psreadline.update.line.1", "{0}", "{1}")
    map[TEXT_LINE_2_ENV] = TerminalBundle.message("psreadline.update.line.2", "{0}")
    map[TEXT_LINE_3_ENV] = TerminalBundle.message("psreadline.update.line.3")
    map[IDE_NAME_ENV] = ApplicationNamesInfo.getInstance().fullProductName
  }

  private fun isPowerShell(options: ShellStartupOptions): Boolean {
    val shellCommand = options.shellCommand ?: return false
    val commandName = shellCommand.firstOrNull() ?: return false
    val shellName = PathUtil.getFileName(commandName)
    return ShellNameUtil.isPowerShell(shellName)
  }
}