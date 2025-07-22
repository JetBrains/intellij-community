// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PathUtil
import com.intellij.util.system.OS
import com.jediterm.terminal.Terminal
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.util.ShellNameUtil

@ApiStatus.Internal
object TerminalPSReadLineUpdateUtil {
  private const val UPDATE_REJECTED_COMMAND = "psreadline_update_rejected"
  private const val UPDATE_REJECTED_PROPERTY = "Terminal.PSReadlineUpdateRejected"

  private const val ASK_UPDATE_ENV = "__JETBRAINS_INTELLIJ_ASK_PSREADLINE_UPDATE"

  private const val TEXT_LINE_1_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_1"
  private const val TEXT_LINE_2_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_2"
  private const val TEXT_LINE_3_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_3"
  private const val TEXT_LINE_4_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_4"
  private const val TEXT_REJECTED_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_REJECTED"
  private const val TEXT_SKIPPED_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_SKIPPED"
  private const val TEXT_COMPLETED_ENV = "__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_COMPLETED"
  private const val IDE_NAME_ENV = "__JETBRAINS_INTELLIJ_IDE_NAME"

  private val LOG = logger<TerminalPSReadLineUpdateUtil>()

  @JvmStatic
  fun trackUpdateRejection(terminal: Terminal) {
    terminal.addCustomCommandListener { args ->
      try {
        handleCustomCommand(args)
      }
      catch (ex: Exception) {
        LOG.error("Failed to handle custom command", ex)
      }
    }
  }

  private fun handleCustomCommand(args: List<String>) {
    val message = args.getOrNull(0)
    if (message == UPDATE_REJECTED_COMMAND) {
      PropertiesComponent.getInstance().setValue(UPDATE_REJECTED_PROPERTY, true)
    }
  }

  @JvmStatic
  fun configureOptions(options: ShellStartupOptions): ShellStartupOptions {
    if (!isPowerShell(options)) {
      return options
    }

    val os = OS.CURRENT
    val isWin10 = os == OS.Windows && os.isAtLeast(10, 0) && !os.isAtLeast(11, 0)
    val updateRejected = PropertiesComponent.getInstance().getBoolean(UPDATE_REJECTED_PROPERTY)
    val alreadyEnabled = options.envVariables[ASK_UPDATE_ENV].equals("true", ignoreCase = true)
    val alreadyDisabled = options.envVariables[ASK_UPDATE_ENV].equals("false", ignoreCase = true)
    // Perform configuration again if the value is already configured to add localization-related envs.
    return if (isWin10 && !updateRejected && !alreadyDisabled || alreadyEnabled) {
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
    map[TEXT_LINE_4_ENV] = TerminalBundle.message("psreadline.update.line.4")
    map[TEXT_REJECTED_ENV] = TerminalBundle.message("psreadline.update.rejected")
    map[TEXT_SKIPPED_ENV] = TerminalBundle.message("psreadline.update.skipped")
    map[TEXT_COMPLETED_ENV] = TerminalBundle.message("psreadline.update.completed")
    map[IDE_NAME_ENV] = ApplicationNamesInfo.getInstance().fullProductName
  }

  private fun isPowerShell(options: ShellStartupOptions): Boolean {
    val shellCommand = options.shellCommand ?: return false
    val commandName = shellCommand.firstOrNull() ?: return false
    val shellName = PathUtil.getFileName(commandName)
    return ShellNameUtil.isPowerShell(shellName)
  }
}