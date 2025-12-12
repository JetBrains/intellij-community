package com.intellij.terminal.tests.reworked.util

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelResult.Error
import com.intellij.platform.eel.EelResult.Ok
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.path.EelPath
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.DynamicTest
import kotlin.time.Duration

internal fun <T> withShellPathAndShellIntegration(
  eelApi: EelApi,
  timeout: Duration = DEFAULT_TEST_TIMEOUT,
  action: suspend CoroutineScope.(shellPath: EelPath, shellIntegration: Boolean) -> T,
): List<DynamicTest> {
  val shellPaths = timeoutRunBlocking(timeout) {
    listAvailableShellPaths(eelApi)
  }
  return shellPaths.flatMap { listOf(it to false, it to true) }.map { (shellPath, shellIntegration) ->
    DynamicTest.dynamicTest("$shellPath, shell_integration=$shellIntegration") {
      timeoutRunBlocking(timeout) {
        action(shellPath, shellIntegration)
      }
    }
  }
}

internal suspend fun listAvailableShellPaths(eelApi: EelApi): List<EelPath> {
  return (findAbsoluteShellPaths(eelApi) + findShellPathsFromPATH(eelApi)).distinct()
}

private suspend fun findAbsoluteShellPaths(eelApi: EelApi): List<EelPath> {
  val absoluteShellPaths = when (eelApi.descriptor.osFamily) {
    EelOsFamily.Posix -> listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
      "/bin/bash",
      "/usr/bin/bash",
      "/opt/homebrew/bin/bash",
      "/usr/bin/fish",
      "/usr/local/bin/fish",
      "/opt/homebrew/bin/fish",
    )
    EelOsFamily.Windows -> emptyList()
  }
  return absoluteShellPaths
    .map { EelPath.parse(it, eelApi.descriptor) }
    .filter { it.isFile(eelApi) }
}

private suspend fun findShellPathsFromPATH(eelApi: EelApi): List<EelPath> {
  val shellExeNames = when (eelApi.descriptor.osFamily) {
    EelOsFamily.Posix -> listOf(
      "bash",
      "zsh",
      "fish",
    )
    EelOsFamily.Windows -> listOf(
      "powershell.exe",
      "pwsh.exe",
    )
  }
  return shellExeNames.mapNotNull {
    eelApi.exec.findExeFilesInPath(it).firstOrNull()
  }
}

private suspend fun EelPath.isFile(eelApi: EelApi): Boolean {
  return when (val result = eelApi.fs.stat(this).resolveAndFollow().eelIt()) {
    is Ok -> result.value.type is EelFileInfo.Type.Regular
    is Error -> false
  }
}
