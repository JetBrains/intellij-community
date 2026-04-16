// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner.LOGIN_CLI_OPTION
import org.jetbrains.plugins.terminal.TerminalStartupEelContext
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.INTERACTIVE_CLI_OPTION
import java.nio.file.Path

/**
 * Converts a "wsl.exe" Windows shell command to a Linux shell command to launch it via IJEnt.
 *
 * Conversion is performed only if the Windows WSL shell command can be fully recognized, e.g.:
 *
 * - `"wsl.exe"`
 * - `"wsl.exe -d Ubuntu"`
 * - `"wsl.exe --distribution Debian"`
 *
 * No conversion is performed in the case of unrecognized options, e.g.:
 * - `"wsl.exe --user my_user"`
 * - `"wsl.exe --exec /bin/bash"`
 * - `"wsl.exe --cd /home/user"`
 *
 * If IJEnt is unavailable, the distribution cannot be determined, or conversion fails for any reason,
 * the Windows WSL shell command is launched via wsl.exe (without IJEnt).
 */
internal class WslShellExecCommand(
  private val distributionNameFromCommandLine: String?,
) {

  suspend fun toIJEntStartupEelContext(workingDirectory: Path): TerminalStartupEelContext? {
    val distributionName = findDistributionName(workingDirectory)
    if (distributionName != null) {
      val wslRootPath = WSLDistribution(distributionName).getUNCRootPath()
      val eelDescriptor = wslRootPath.getEelDescriptor()
      val eelApi = eelDescriptor.toWslEelApiIfAvailable()
      if (eelApi != null) {
        return TerminalStartupEelContext(
          findRemoteWorkingDirectory(eelApi, workingDirectory),
          createDefaultWslShellCommand(eelApi)
        )
      }
    }
    return null
  }

  private fun findDistributionName(workingDirectory: Path): String? {
    distributionNameFromCommandLine?.let {
      return it
    }
    WslPath.parseWindowsUncPath(workingDirectory.toString())?.let {
      return it.distributionId
    }
    val installedDistributions = WslDistributionManager.getInstance().installedDistributions
    if (installedDistributions.size == 1) {
      return installedDistributions[0].msId
    }
    if (installedDistributions.isEmpty()) {
      logFallbackWarn("Found no installed WSL distributions.")
    }
    else {
      logFallbackWarn("Found multiple (${installedDistributions.size}) installed WSL distributions.")
    }
    return null
  }

  private fun findRemoteWorkingDirectory(eelApi: EelApi, workingDirectory: Path): EelPath {
    val translator = TerminalLocalPathTranslator(eelApi.descriptor)
    val remotePath = translator.translateAbsoluteLocalPathToRemote(workingDirectory)
    return remotePath ?: eelApi.userInfo.home
  }

  private suspend fun createDefaultWslShellCommand(eelApi: EelPosixApi): ShellExecCommand {
    val shell = TerminalEnvironmentVariablesProvider.instance.fetchMinimalEnvironmentVariableValue(eelApi, "SHELL") ?: "/bin/sh"
    return ShellExecCommandImpl(listOf(shell, LOGIN_CLI_OPTION, INTERACTIVE_CLI_OPTION))
  }

  companion object {
    internal fun parse(shellCommand: List<String>): WslShellExecCommand? {
      if (isWslCommand(shellCommand)) {
        if (shellCommand.size == 1) {
          return WslShellExecCommand(null)
        }
        val distributionOptionName = shellCommand.getOrNull(1)
        if (distributionOptionName == "-d" || distributionOptionName == "--distribution") {
          if (shellCommand.size == 3) {
            return WslShellExecCommand(shellCommand[2])
          }
        }
        logFallbackWarn("Unable to parse WSL command $shellCommand to launch via IJEnt.")
      }
      return null
    }

    internal fun isWslCommand(command: List<String>): Boolean {
      if (SystemInfo.isWindows) {
        val exePath = command.getOrNull(0) ?: return false
        val exeFileName = PathUtil.getFileName(exePath)
        return exeFileName.equals("wsl.exe", true) || exeFileName.equals("wsl", true)
      }
      return false
    }

    private suspend fun EelDescriptor.toWslEelApiIfAvailable(): EelPosixApi? {
      val eelApi = try {
        toEelApi()
      }
      catch (e: EelUnavailableException) {
        logFallbackWarn("Unavailable EelApi for ${this.name}", e)
        return null
      }
      return eelApi.asSafely<EelPosixApi>() ?: run {
        logFallbackWarn("EelPosixApi is expected, got ${eelApi.javaClass.name}")
        null
      }
    }

    private fun logFallbackWarn(reason: String, t: Throwable? = null) {
      fileLogger().warn("$reason. Fallback to local launch via wsl.exe.", t)
    }
  }
}
