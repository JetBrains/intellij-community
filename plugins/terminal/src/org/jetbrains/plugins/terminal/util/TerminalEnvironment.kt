// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.impl.wsl.WslConstants.WSLENV
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.isWslCommand

@ApiStatus.Internal
object TerminalEnvironment {

  const val TERMINAL_EMULATOR: String = "TERMINAL_EMULATOR"
  const val TERM_SESSION_ID: String = "TERM_SESSION_ID"
  private const val COLON: String = ":"

  fun setCharacterEncoding(env: MutableMap<String, String>) {
    if (SystemInfo.isMac) {
      val charsetName = AdvancedSettings.getString("terminal.character.encoding")
      val charset = try {
        charset(charsetName)
      }
      catch (e: Exception) {
        logger<TerminalEnvironment>().warn("Cannot find $charsetName encoding", e)
        Charsets.UTF_8
      }
      env[LC_CTYPE] = charset.name()
    }
  }

  private const val LC_CTYPE = "LC_CTYPE"

  /**
   * Propagates environment variables to WSL via `WSLENV`:
   * https://devblogs.microsoft.com/commandline/share-environment-vars-between-wsl-and-windows/
   *
   * Takes effect only when shell is running in WSL via wsl.exe without IJEnt (`terminal.use.EelApi` is disabled).
   */
  @JvmStatic
  fun setWslEnv(
    eelDescriptor: EelDescriptor,
    shellCommand: List<String>,
    userDefinedEnvData: EnvironmentVariablesData?,
    envs: MutableMap<String, String>,
  ) {
    if (eelDescriptor.osFamily.isWindows && eelDescriptor == LocalEelDescriptor && isWslCommand(shellCommand)) {
      doSetWslEnv(userDefinedEnvData, envs)
    }
  }

  @VisibleForTesting
  fun doSetWslEnv(
    userDefinedEnvData: EnvironmentVariablesData?,
    envs: MutableMap<String, String>,
  ) {
    val envNamesToPass = buildList {
      addAll(userDefinedEnvData?.envs?.keys.orEmpty())
      add(TERMINAL_EMULATOR)
      add(TERM_SESSION_ID)
    }.distinctBy { it.lowercase() }

    val newItems = envNamesToPass.filter { it != WSLENV }.map { "$it/u" }
    val allItems = listOfNotNull(envs[WSLENV]?.removeSuffix(COLON)) + newItems
    envs[WSLENV] = allItems.joinToString(COLON)
  }
}
