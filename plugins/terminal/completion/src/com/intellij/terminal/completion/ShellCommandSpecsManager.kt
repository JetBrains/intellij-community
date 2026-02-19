// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import com.intellij.terminal.completion.spec.ShellCommandSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ShellCommandSpecsManager {
  suspend fun getCommandSpec(commandName: String): ShellCommandSpec?

  /**
   * The spec of the command or subcommand can be not fully loaded sometimes.
   * For example, it can be a light spec with only names and the reference for the full spec.
   * This method guarantees that returned spec will contain all its content.
   */
  suspend fun getFullCommandSpec(spec: ShellCommandSpec): ShellCommandSpec
}