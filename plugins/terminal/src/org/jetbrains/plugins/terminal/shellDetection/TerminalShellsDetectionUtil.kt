// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsSafe
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
import kotlinx.coroutines.Deferred
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

    val systemRoot = envVariables["SystemRoot"]      // C:\\Windows
    val programFiles = envVariables["ProgramFiles"]  // C:\\Program Files
    val localAppData = envVariables["LocalAppData"]  // C:\\Users\\<Username>\\AppData\\Local

    val detectedShells: List<DetectedShellInfo> = coroutineScope {
      // Launch existence checks in parallel because checking sequentially can be slow for remote environments with big latency.
      val tasks: List<Deferred<DetectedShellInfo?>> = buildList {
        add(async {
          val powershell = eelApi.exec.where("powershell.exe")
          if (powershell != null && powershell.startsWithIgnoreCase("$systemRoot\\System32\\WindowsPowerShell\\")) {
            createShellInfo(POWERSHELL_5_NAME, powershell.toString(), eelDescriptor = eelDescriptor)
          }
          else null
        })

        add(async {
          val cmd = eelApi.exec.where("cmd.exe")
          if (cmd != null && cmd.startsWithIgnoreCase("$systemRoot\\System32\\")) {
            createShellInfo(CMD_NAME, cmd.toString(), eelDescriptor = eelDescriptor)
          }
          else null
        })

        add(async {
          val pwsh = eelApi.exec.where("pwsh.exe")
          if (pwsh != null && pwsh.startsWithIgnoreCase("$programFiles\\PowerShell\\")) {
            createShellInfo(POWERSHELL_7_NAME, pwsh.toString(), eelDescriptor = eelDescriptor)
          }
          else null
        })

        add(async {
          val gitBashGlobal = EelPath.parse("$programFiles\\Git\\bin\\bash.exe", eelDescriptor)
          val gitBashLocal = EelPath.parse("$localAppData\\Programs\\Git\\bin\\bash.exe", eelDescriptor)
          val gitBash = when {
            eelApi.fs.isRegularFile(gitBashLocal) -> gitBashLocal
            eelApi.fs.isRegularFile(gitBashGlobal) -> gitBashGlobal
            else -> null
          }
          if (gitBash != null) {
            createShellInfo(GIT_BASH_NAME, gitBash.toString(), eelDescriptor = eelDescriptor)
          }
          else null
        })
      }

      tasks.awaitAll().filterNotNull()
    }

    val cmderRoot = envVariables["CMDER_ROOT"]
    val cmd = detectedShells.find { it.name == CMD_NAME }?.path
    val cmderShell = if (cmderRoot != null && cmd != null && cmd.startsWith("$systemRoot\\System32\\", ignoreCase = true)) {
      val cmderInitBat = EelPath.parse(cmderRoot, eelDescriptor).resolve("vendor\\init.bat")
      if (eelApi.fs.isRegularFile(cmderInitBat)) {
        createShellInfo(CMDER_NAME, cmd, listOf("/k", cmderInitBat.toString()), eelDescriptor)
      }
      else null
    }
    else null

    return if (cmderShell != null) detectedShells + cmderShell else detectedShells
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