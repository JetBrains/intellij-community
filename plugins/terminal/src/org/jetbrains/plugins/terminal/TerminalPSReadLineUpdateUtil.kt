// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.util.PathUtil
import com.intellij.util.system.OS
import org.jetbrains.plugins.terminal.util.ShellNameUtil

internal object TerminalPSReadLineUpdateUtil {
  private const val ASK_UPDATE_ENV = "__JETBRAINS_INTELLIJ_ASK_PSREADLINE_UPDATE"

  private const val TEXT_LINE_1_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_1"
  private const val TEXT_LINE_2_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_2"
  private const val TEXT_LINE_3_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_3"

  @JvmStatic
  fun configureOptions(options: ShellStartupOptions): ShellStartupOptions {
    if (!isPowerShell(options)) {
      return options
    }

    val os = OS.CURRENT
    val isWin10 = os == OS.Windows && os.isAtLeast(10, 0) && !os.isAtLeast(11, 0)
    val alreadyEnabled = options.envVariables[ASK_UPDATE_ENV].equals("true", ignoreCase = true)
    val alreadyDisabled = options.envVariables[ASK_UPDATE_ENV].equals("false", ignoreCase = true)
    // Perform configuration again if the value is already configured to add localization-related envs.
    return if (isWin10 && !alreadyDisabled || alreadyEnabled) {
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
  @Suppress("InvalidBundleOrProperty") // Parameter values will be specified in PowerShell code
  private fun addLocalizationEnvVars(map: MutableMap<String, String>) {
    map[TEXT_LINE_1_ENV] = TerminalBundle.message("psreadline.update.line.1")
    map[TEXT_LINE_2_ENV] = TerminalBundle.message("psreadline.update.line.2")
    map[TEXT_LINE_3_ENV] = TerminalBundle.message("psreadline.update.line.3")
  }

  private fun isPowerShell(options: ShellStartupOptions): Boolean {
    val shellCommand = options.shellCommand ?: return false
    val commandName = shellCommand.firstOrNull() ?: return false
    val shellName = PathUtil.getFileName(commandName)
    return ShellNameUtil.isPowerShell(shellName)
  }
}