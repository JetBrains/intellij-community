// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.SystemInfo

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
