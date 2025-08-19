// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.impl.wsl.WslConstants.WSLENV
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.plugins.terminal.isWslCommand

object TerminalEnvironment {
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
}

/**
 * Propagates environment variables to WSL via `WSLENV`:
 * https://devblogs.microsoft.com/commandline/share-environment-vars-between-wsl-and-windows/
 *
 * Takes effect only when shell is running in WSL via wsl.exe without IJEnt (`terminal.use.EelApi` is disabled).
 */
internal class WslEnvInterop(eelDescriptor: EelDescriptor, shellCommand: MutableList<String>) {
  private val enabled: Boolean = eelDescriptor.osFamily.isWindows &&
                                 eelDescriptor == LocalEelDescriptor &&
                                 isWslCommand(shellCommand)
  private val envNamesToPass: MutableSet<String> = CollectionFactory.createCaseInsensitiveStringSet()

  fun passEnvsToWsl(envNames: Collection<String>) {
    if (enabled) {
      envNamesToPass.addAll(envNames)
    }
  }

  fun applyTo(envs: MutableMap<String, String>) {
    if (!enabled) return
    val newItems = envNamesToPass.filter { it != WSLENV }.map { "$it/u" }
    if (newItems.isNotEmpty()) {
      val colon = ":"
      val allItems = listOfNotNull(envs[WSLENV]?.removeSuffix(colon)) + newItems
      envs[WSLENV] = allItems.joinToString(colon)
    }
  }
}
