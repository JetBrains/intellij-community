// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
object TerminalShellsDetector {
  private val UNIX_BINARIES_DIRECTORIES = listOf(
    "/bin",
    "/usr/bin",
    "/usr/local/bin",
    "/opt/homebrew/bin"
  )

  private val UNIX_SHELL_NAMES = listOf("bash", "zsh", "fish", "pwsh")

  /**
   * Finds available shells in the local file system using some heuristics.
   */
  @JvmStatic
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun detectShells(): List<DetectedShellInfo> {
    return when {
      SystemInfo.isUnix -> detectUnixShells()
      SystemInfo.isWindows -> detectWindowsShells() + detectWsl()
      else -> emptyList()
    }
  }

  private fun detectUnixShells(): List<DetectedShellInfo> {
    val shells = mutableListOf<DetectedShellInfo>()

    // Iterate over all combinations of path+shell to find executables.
    for (shellName in UNIX_SHELL_NAMES) {
      for (parentPath in UNIX_BINARIES_DIRECTORIES) {
        val shellPath = Path.of(parentPath, shellName)
        if (shellPath.exists()) {
          shells.add(createShellInfo(shellName, shellPath.toAbsolutePath().toString()))
        }
      }
    }

    return shells
  }

  private fun detectWindowsShells(): List<DetectedShellInfo> {
    val shells = mutableListOf<DetectedShellInfo>()

    val systemRoot = EnvironmentUtil.getValue("SystemRoot")      // C:\\Windows
    val programFiles = EnvironmentUtil.getValue("ProgramFiles")  // C:\\Program Files
    val localAppData = EnvironmentUtil.getValue("LocalAppData")  // C:\\Users\\<Username>\\AppData\\Local

    val powershell = PathEnvironmentVariableUtil.findInPath("powershell.exe")
    if (powershell != null && powershell.absolutePath.startsWith("$systemRoot\\System32\\WindowsPowerShell\\", ignoreCase = true)) {
      shells.add(createShellInfo("Windows PowerShell", powershell.absolutePath))
    }
    val cmd = PathEnvironmentVariableUtil.findInPath("cmd.exe")
    if (cmd != null && cmd.absolutePath.startsWith("$systemRoot\\System32\\", ignoreCase = true)) {
      shells.add(createShellInfo("Command Prompt", cmd.absolutePath))
    }
    val pwsh = PathEnvironmentVariableUtil.findInPath("pwsh.exe")
    if (pwsh != null && pwsh.absolutePath.startsWith("$programFiles\\PowerShell\\", ignoreCase = true)) {
      shells.add(createShellInfo("PowerShell", pwsh.absolutePath))
    }

    val gitBashGlobal = File("$programFiles\\Git\\bin\\bash.exe")
    val gitBashLocal = File("$localAppData\\Programs\\Git\\bin\\bash.exe")
    val gitBash = when {
      gitBashLocal.isFile() -> gitBashLocal
      gitBashGlobal.isFile() -> gitBashGlobal
      else -> null
    }
    if (gitBash != null) {
      shells.add(createShellInfo("Git Bash", gitBash.absolutePath))
    }

    val cmderRoot = EnvironmentUtil.getValue("CMDER_ROOT")
    if (cmderRoot != null && cmd != null && cmd.absolutePath.startsWith("$systemRoot\\System32\\", ignoreCase = true)) {
      val cmderInitBat = File(cmderRoot, "vendor\\init.bat")
      if (cmderInitBat.isFile()) {
        shells.add(createShellInfo("Cmder", cmd.absolutePath, listOf("/k", cmderInitBat.absolutePath)))
      }
    }

    return shells
  }

  private fun detectWsl(): List<DetectedShellInfo> {
    return if (WSLDistribution.findWslExe() != null) {
      return WslDistributionManager.getInstance()
        .getInstalledDistributions()
        .map { createShellInfo(it.msId, "wsl.exe", listOf("-d", it.msId)) }
    }
    else emptyList()
  }

  private fun createShellInfo(shellName: String, shellPath: String, options: List<String> = emptyList()): DetectedShellInfo {
    val shellCommand: List<String> = LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath)
    // The shell command consists of shellPath + some options, like `--login` or `i`.
    // Add them to the resulting options list.
    val addedOptions = shellCommand.drop(1)
    val adjustedOptions = (addedOptions + options).distinct()
    return DetectedShellInfo(shellName, shellPath, adjustedOptions)
  }
}

@ApiStatus.Internal
@Serializable
data class DetectedShellInfo(
  /** Name of the shell, for example, zsh or Windows PowerShell */
  val name: @NlsSafe String,
  /** Absolute path of the shell executable */
  val path: String,
  /** Additional command line options that should be used to start this shell */
  val options: List<String> = emptyList(),
)