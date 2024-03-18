// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.util

import com.intellij.terminal.completion.CommandSpecManager
import org.jetbrains.terminal.completion.ShellCommand

class FakeCommandSpecManager(private val commands: Map<String, ShellCommand> = emptyMap()) : CommandSpecManager {
  override fun getShortCommandSpec(commandName: String): ShellCommand? {
    return commands[commandName]
  }

  override suspend fun getCommandSpec(commandName: String): ShellCommand? {
    return commands[commandName]
  }
}