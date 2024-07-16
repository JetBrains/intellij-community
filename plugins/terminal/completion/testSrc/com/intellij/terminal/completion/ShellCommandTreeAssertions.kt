package com.intellij.terminal.completion

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ShellCommandTreeAssertions {
  fun assertSubcommandOf(cmd: String, parentCmd: String)

  fun assertOptionOf(option: String, subcommand: String)

  fun assertArgumentOfOption(arg: String, option: String)

  fun assertArgumentOfSubcommand(arg: String, subcommand: String)

  fun assertUnknown(child: String, parent: String)
}