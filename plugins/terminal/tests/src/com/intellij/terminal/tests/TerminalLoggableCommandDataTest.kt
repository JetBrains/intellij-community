package com.intellij.terminal.tests

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics.CommandData
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics.KnownCommandsData
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics.getLoggableCommandData
import org.junit.Test

internal class TerminalLoggableCommandDataTest {
  @Test
  fun `empty string`() {
    val data = getLoggableCommandData("", EMPTY_DATA)
    assertCommandData(data, "<empty>", null)
  }

  @Test
  fun `whitespace only`() {
    val data = getLoggableCommandData("   ", EMPTY_DATA)
    assertCommandData(data, "<whitespaces>", null)
  }

  @Test
  fun `known command with known subcommand`() {
    val data = getLoggableCommandData("git commit", GIT_DATA)
    assertCommandData(data, "git", "commit")
  }

  @Test
  fun `known command with known subcommand and extra args`() {
    val data = getLoggableCommandData("git commit -m message", GIT_DATA)
    assertCommandData(data, "git", "commit")
  }

  @Test
  fun `known command with unknown subcommand`() {
    val data = getLoggableCommandData("git unknown-sub", GIT_DATA)
    assertCommandData(data, "git", "third.party")
  }

  @Test
  fun `known command without arguments`() {
    val data = getLoggableCommandData("git", GIT_DATA)
    assertCommandData(data, "git", null)
  }

  @Test
  fun `subcommand matches any known command`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("git", "docker"),
      subCommands = setOf("commit"),
      maxSubCommandTokens = 1,
    )
    val data = getLoggableCommandData("docker commit", knownCommandsData)
    assertCommandData(data, "docker", "commit")
  }

  @Test
  fun `multiword subcommand`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv pip install pkg", knownCommandsData)
    assertCommandData(data, "uv", "pip install")
  }

  @Test
  fun `longest multiword subcommand wins`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip", "pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv pip install pkg", knownCommandsData)
    assertCommandData(data, "uv", "pip install")
  }

  @Test
  fun `falls back to shorter subcommand`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip", "pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv pip unknown", knownCommandsData)
    assertCommandData(data, "uv", "pip")
  }

  @Test
  fun `multiword subcommand no match`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv foo bar", knownCommandsData)
    assertCommandData(data, "uv", "third.party")
  }

  @Test
  fun `relative path`() {
    val data = getLoggableCommandData("./script.sh", EMPTY_DATA)
    assertCommandData(data, "<relative path>", null)
  }

  @Test
  fun `home-related path`() {
    val data = getLoggableCommandData("~/Documents/tool", EMPTY_DATA)
    assertCommandData(data, "<absolute path>", null)
  }

  @Test
  fun `absolute path unix`() {
    val data = getLoggableCommandData("/usr/bin/tool", EMPTY_DATA)
    assertCommandData(data, "<absolute path>", null)
  }

  @Test
  fun `absolute path windows`() {
    val data = getLoggableCommandData("C:\\Users\\tool.exe", EMPTY_DATA)
    assertCommandData(data, "<absolute path>", null)
  }

  @Test
  fun `unknown command`() {
    val data = getLoggableCommandData("unknown-cmd", GIT_DATA)
    assertCommandData(data, "third.party", null)
  }

  companion object {
    private val EMPTY_DATA = KnownCommandsData(
      commands = emptySet(),
      subCommands = emptySet(),
      maxSubCommandTokens = 1,
    )

    private val GIT_DATA = KnownCommandsData(
      commands = setOf("git"),
      subCommands = setOf("commit", "push", "pull"),
      maxSubCommandTokens = 1,
    )

    private fun assertCommandData(data: CommandData, expectedCommand: String, expectedSubCommand: String?) {
      assertThat(data.command).isEqualTo(expectedCommand)
      assertThat(data.subCommand).isEqualTo(expectedSubCommand)
    }
  }
}
