// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import org.jetbrains.terminal.completion.ShellCommand

interface CommandSpecManager {
  /**
   * Returns a short version of the command specification: only names, description
   * and the loadSpec reference for loading full specification.
   * Intended that this method should return fast in most of the cases, because it should not load the whole command specification.
   * See [getCommandSpec] for more info.
   */
  fun getShortCommandSpec(commandName: String): ShellCommand?

  /**
   * [commandName] can be the main command name or the path of subcommand.
   * In the latter case, the [commandName] should be represented by the main command name and subcommand name divided by '/'.
   * For example, 'main/sub'.
   * The subcommand should be located in the directory named as the main command.
   * So the expected file structure should look like this:
   * - main.json
   * - main
   *     - sub.json
   *     - sub2.json
   */
  suspend fun getCommandSpec(commandName: String): ShellCommand?
}