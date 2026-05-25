// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder

@ApiStatus.Internal
object TerminalShellsDetectionUtil {
  val LOCAL_ENVIRONMENT_NAME: @Nls String
    get() = TerminalBundle.message("local.environment.name")
  const val WSL_ENVIRONMENT_NAME: @NlsSafe String = "WSL"
  const val DEV_CONTAINER_ENVIRONMENT_NAME: @NlsSafe String = "Dev Container"

  private const val POWERSHELL_5_NAME: @NlsSafe String = "Windows PowerShell"
  private const val POWERSHELL_7_NAME: @NlsSafe String = "PowerShell"
  private const val CMD_NAME: @NlsSafe String = "Command Prompt"
  private const val GIT_BASH_NAME: @NlsSafe String = "Git Bash"
  private const val CMDER_NAME: @NlsSafe String = "Cmder"

  private const val ENV_SYSTEM_ROOT: String = "SystemRoot"        // C:\Windows
  private const val ENV_PROGRAM_FILES: String = "ProgramFiles"    // C:\Program Files
  private const val ENV_LOCAL_APP_DATA: String = "LocalAppData"   // C:\Users\<Username>\AppData\Local
  private const val ENV_CMDER_ROOT: String = "CMDER_ROOT"

  private val UNIX_BINARIES_DIRECTORIES = listOf(
    "/bin",
    "/usr/bin",
    "/usr/local/bin",
    "/opt/homebrew/bin"
  )

  private val UNIX_SHELL_NAMES = listOf("bash", "zsh", "fish", "pwsh")

  suspend fun detectUnixShells(eelDescriptor: EelDescriptor): List<DetectedShellInfo> {
    require(eelDescriptor.osFamily.isPosix) { "detectUnixShells should only be called with Unix EelDescriptor" }

    data class ShellCandidate(val name: String, val path: EelPath)

    // Iterate over all combinations of path+shell to find executables.
    val candidates: List<ShellCandidate> = buildList {
      for (parentPath in UNIX_BINARIES_DIRECTORIES) {
        val parentEelPath = EelPath.parse(parentPath, eelDescriptor)
        for (shellName in UNIX_SHELL_NAMES) {
          val shellPath = parentEelPath.resolve(shellName)
          add(ShellCandidate(shellName, shellPath))
        }
      }
    }

    val existingCandidates = coroutineScope {
      val eelApi = eelDescriptor.toEelApi()

      // Launch existence checks in parallel because checking sequentially can be slow for remote environments with big latency.
      candidates.map { shell ->
        async {
          if (eelApi.fs.isRegularFile(shell.path)) shell else null
        }
      }
        .awaitAll()
        .filterNotNull()
    }

    return existingCandidates.map {
      createShellInfo(it.name, it.path.toString(), eelDescriptor = eelDescriptor)
    }
  }

  suspend fun detectWindowsShells(eelDescriptor: EelDescriptor): List<DetectedShellInfo> {
    require(eelDescriptor.osFamily.isWindows) { "detectWindowsShells should only be called with Windows EelDescriptor" }

    val eelApi = eelDescriptor.toEelApi()

    val envVariables = try {
      eelApi.exec.environmentVariables().onlyActual(true).eelIt().await()
    }
    catch (ex: EelExecApi.EnvironmentVariablesException) {
      thisLogger().error("Failed to fetch environment variables", ex)
      return emptyList()
    }

    return coroutineScope {
      // Launch existence checks in parallel because checking sequentially can be slow for remote environments with big latency.
      buildList {
        add(async { detectWindowsPowerShell5(eelApi, envVariables) })
        add(async { detectWindowsCmd(eelApi, envVariables) })
        add(async { detectWindowsPowerShell7(eelApi) })
        add(async { detectWindowsGitBash(eelApi, envVariables) })
        add(async { detectWindowsCmder(eelApi, envVariables) })
      }
        .awaitAll()
        .filterNotNull()
    }
  }

  private suspend fun detectWindowsPowerShell5(eelApi: EelApi, envVariables: Map<String, String>): DetectedShellInfo? {
    val systemRoot = envVariables[ENV_SYSTEM_ROOT] ?: return null
    val powershell = eelApi.exec.where("powershell.exe") ?: return null
    if (!powershell.startsWithIgnoreCase("$systemRoot\\System32\\WindowsPowerShell\\")) return null
    return createShellInfo(POWERSHELL_5_NAME, powershell.toString(), eelDescriptor = eelApi.descriptor)
  }

  private suspend fun detectWindowsCmd(eelApi: EelApi, envVariables: Map<String, String>): DetectedShellInfo? {
    val systemRoot = envVariables[ENV_SYSTEM_ROOT] ?: return null
    val cmd = eelApi.exec.where("cmd.exe") ?: return null
    if (!cmd.startsWithIgnoreCase("$systemRoot\\System32\\")) return null
    return createShellInfo(CMD_NAME, cmd.toString(), eelDescriptor = eelApi.descriptor)
  }

  private suspend fun detectWindowsPowerShell7(eelApi: EelApi): DetectedShellInfo? {
    val pwsh = eelApi.exec.where("pwsh.exe") ?: return null
    return createShellInfo(POWERSHELL_7_NAME, pwsh.toString(), eelDescriptor = eelApi.descriptor)
  }

  private suspend fun detectWindowsGitBash(eelApi: EelApi, envVariables: Map<String, String>): DetectedShellInfo? {
    val programFiles = envVariables[ENV_PROGRAM_FILES] ?: return null
    val localAppData = envVariables[ENV_LOCAL_APP_DATA] ?: return null
    val gitBashGlobal = EelPath.parse("$programFiles\\Git\\bin\\bash.exe", eelApi.descriptor)
    val gitBashLocal = EelPath.parse("$localAppData\\Programs\\Git\\bin\\bash.exe", eelApi.descriptor)
    val gitBash = when {
      eelApi.fs.isRegularFile(gitBashLocal) -> gitBashLocal
      eelApi.fs.isRegularFile(gitBashGlobal) -> gitBashGlobal
      else -> return null
    }
    return createShellInfo(GIT_BASH_NAME, gitBash.toString(), eelDescriptor = eelApi.descriptor)
  }

  private suspend fun detectWindowsCmder(eelApi: EelApi, envVariables: Map<String, String>): DetectedShellInfo? {
    val cmderRoot = envVariables[ENV_CMDER_ROOT] ?: return null
    val systemRoot = envVariables[ENV_SYSTEM_ROOT] ?: return null
    val cmd = eelApi.exec.where("cmd.exe") ?: return null
    if (!cmd.startsWithIgnoreCase("$systemRoot\\System32\\")) return null
    val cmderInitBat = EelPath.parse(cmderRoot, eelApi.descriptor).resolve("vendor\\init.bat")
    if (!eelApi.fs.isRegularFile(cmderInitBat)) return null
    return createShellInfo(CMDER_NAME, cmd.toString(), listOf("/k", cmderInitBat.toString()), eelApi.descriptor)
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
    return stat(path).resolveAndFollow().eelIt().getOrNull()?.type is EelFileInfo.Type.Regular
  }

  private fun EelPath.startsWithIgnoreCase(path: String): Boolean {
    return toString().startsWith(path, ignoreCase = true)
  }
}