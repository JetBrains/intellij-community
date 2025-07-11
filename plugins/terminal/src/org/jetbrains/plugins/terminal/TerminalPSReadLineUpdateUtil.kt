// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.ide.util.PropertiesComponent
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

    if (options.envVariables.containsKey(ASK_UPDATE_ENV)) {
      // Do nothing if the user already configured this option in the settings.
      return options
    }

    val os = OS.CURRENT
    val isWin10 = os == OS.Windows && os.isAtLeast(10, 0) && !os.isAtLeast(11, 0)
    val updateRejected = PropertiesComponent.getInstance().getBoolean(UPDATE_REJECTED_PROPERTY)
    return if (isWin10 && !updateRejected) {
      val newEnvs = createEnvVariablesMap(options.envVariables)
      newEnvs[ASK_UPDATE_ENV] = "true"
      options.builder().envVariables(newEnvs).build()
    }
    else options
  }

  private fun isPowerShell(options: ShellStartupOptions): Boolean {
    val shellCommand = options.shellCommand ?: return false
    val commandName = shellCommand.firstOrNull() ?: return false
    val shellName = PathUtil.getFileName(commandName)
    return ShellNameUtil.isPowerShell(shellName)
  }
}