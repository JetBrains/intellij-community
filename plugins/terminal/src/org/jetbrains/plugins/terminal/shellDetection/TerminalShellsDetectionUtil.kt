// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.where
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder

@ApiStatus.Internal
object TerminalShellsDetectionUtil {
  const val LOCAL_ENVIRONMENT_NAME: String = "Host"
  const val WSL_ENVIRONMENT_NAME: String = "WSL"
  const val DEV_CONTAINER_ENVIRONMENT_NAME: String = "Dev Container"

  private val UNIX_BINARIES_DIRECTORIES = listOf(
    "/bin",
    "/usr/bin",
    "/usr/local/bin",
    "/opt/homebrew/bin"
  )

  private val UNIX_SHELL_NAMES = listOf("bash", "zsh", "fish", "pwsh")

  suspend fun detectUnixShells(eelDescriptor: EelDescriptor): List<DetectedShellInfo> {
    require(eelDescriptor.osFamily.isPosix) { "detectUnixShells should only be called with Unix EelDescriptor" }

    val eelApi = eelDescriptor.toEelApi()
    val shells = mutableListOf<DetectedShellInfo>()

    // Iterate over all combinations of path+shell to find executables.
    for (parentPath in UNIX_BINARIES_DIRECTORIES) {
      val parentEelPath = EelPath.parse(parentPath, eelDescriptor)
      for (shellName in UNIX_SHELL_NAMES) {
        val shellPath = parentEelPath.resolve(shellName)
        if (eelApi.fs.isRegularFile(shellPath)) {
          shells.add(createShellInfo(shellName, shellPath.toString(), eelDescriptor = eelDescriptor))
        }
      }
    }

    return shells
  }

  suspend fun detectWindowsShells(eelDescriptor: EelDescriptor): List<DetectedShellInfo> {
    require(eelDescriptor.osFamily.isWindows) { "detectWindowsShells should only be called with Windows EelDescriptor" }

    val eelApi = eelDescriptor.toEelApi()
    val shells = mutableListOf<DetectedShellInfo>()

    val envVariables = try {
      eelApi.exec.environmentVariables().onlyActual(true).eelIt().await()
    }
    catch (ex: EelExecApi.EnvironmentVariablesException) {
      thisLogger().error("Failed to fetch environment variables", ex)
      return emptyList()
    }

    val systemRoot = envVariables["SystemRoot"]      // C:\\Windows
    val programFiles = envVariables["ProgramFiles"]  // C:\\Program Files
    val localAppData = envVariables["LocalAppData"]  // C:\\Users\\<Username>\\AppData\\Local

    val powershell = eelApi.exec.where("powershell.exe")
    if (powershell != null && powershell.startsWithIgnoreCase("$systemRoot\\System32\\WindowsPowerShell\\")) {
      shells.add(createShellInfo("Windows PowerShell", powershell.toString(), eelDescriptor = eelDescriptor))
    }
    val cmd = eelApi.exec.where("cmd.exe")
    if (cmd != null && cmd.startsWithIgnoreCase("$systemRoot\\System32\\")) {
      shells.add(createShellInfo("Command Prompt", cmd.toString(), eelDescriptor = eelDescriptor))
    }
    val pwsh = eelApi.exec.where("pwsh.exe")
    if (pwsh != null && pwsh.startsWithIgnoreCase("$programFiles\\PowerShell\\")) {
      shells.add(createShellInfo("PowerShell", pwsh.toString(), eelDescriptor = eelDescriptor))
    }

    val gitBashGlobal = EelPath.parse("$programFiles\\Git\\bin\\bash.exe", eelDescriptor)
    val gitBashLocal = EelPath.parse("$localAppData\\Programs\\Git\\bin\\bash.exe", eelDescriptor)
    val gitBash = when {
      eelApi.fs.isRegularFile(gitBashLocal) -> gitBashLocal
      eelApi.fs.isRegularFile(gitBashGlobal) -> gitBashGlobal
      else -> null
    }
    if (gitBash != null) {
      shells.add(createShellInfo("Git Bash", gitBash.toString(), eelDescriptor = eelDescriptor))
    }

    val cmderRoot = envVariables["CMDER_ROOT"]
    if (cmderRoot != null && cmd != null && cmd.startsWithIgnoreCase("$systemRoot\\System32\\")) {
      val cmderInitBat = EelPath.parse(cmderRoot, eelDescriptor).resolve("vendor\\init.bat")
      if (eelApi.fs.isRegularFile(cmderInitBat)) {
        shells.add(createShellInfo("Cmder", cmd.toString(), listOf("/k", cmderInitBat.toString()), eelDescriptor))
      }
    }

    return shells
  }

  @RequiresBackgroundThread
  fun detectWslDistributions(): List<DetectedShellInfo> {
    return if (WSLDistribution.findWslExe() != null) {
      WslDistributionManager.getInstance()
        .getInstalledDistributions()
        .map { createShellInfo(it.msId, "wsl.exe", listOf("-d", it.msId), eelDescriptor = LocalEelDescriptor) }
    }
    else emptyList()
  }

  private fun createShellInfo(
    shellName: String,
    shellPath: String,
    options: List<String> = emptyList(),
    eelDescriptor: EelDescriptor,
  ): DetectedShellInfo {
    val shellCommand: List<String> = LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath, eelDescriptor)
    // The shell command consists of shellPath + some options, like `--login` or `i`.
    // Add them to the resulting options list.
    val addedOptions = shellCommand.drop(1)
    val adjustedOptions = (addedOptions + options).distinct()
    return DetectedShellInfo(shellName, shellPath, adjustedOptions, eelDescriptor)
  }

  private suspend fun EelFileSystemApi.isRegularFile(path: EelPath): Boolean {
    return stat(path).justResolve().eelIt().getOrNull()?.type is EelFileInfo.Type.Regular
  }

  private fun EelPath.startsWithIgnoreCase(path: String): Boolean {
    return toString().startsWith(path, ignoreCase = true)
  }
}