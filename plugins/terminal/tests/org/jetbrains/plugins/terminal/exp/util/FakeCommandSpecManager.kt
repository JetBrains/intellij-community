// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.util

import org.jetbrains.plugins.terminal.exp.completion.CommandSpecManager
import org.jetbrains.terminal.completion.ShellCommand

class FakeCommandSpecManager : CommandSpecManager {
  override fun getCommandSpec(commandName: String): ShellCommand? {
    return null
  }
}