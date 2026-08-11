package com.intellij.terminal.tests

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics.CommandData
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics.KnownCommandsData
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics.getLoggableCommandData
import org.junit.Test

internal class TerminalLoggableCommandDataTest {
  @Test
  fun `empty string`() {
    val data = getLoggableCommandData("", EMPTY_DATA, false)
    assertCommandData(data, "<empty>", null)
  }

  @Test
  fun `whitespace only`() {
    val data = getLoggableCommandData("   ", EMPTY_DATA, false)
    assertCommandData(data, "<whitespaces>", null)
  }

  @Test
  fun `known command with known subcommand`() {
    val data = getLoggableCommandData("git commit", GIT_DATA, false)
    assertCommandData(data, "git", "commit")
  }

  @Test
  fun `known command with known subcommand and extra args`() {
    val data = getLoggableCommandData("git commit -m message", GIT_DATA, false)
    assertCommandData(data, "git", "commit")
  }

  @Test
  fun `known command with unknown subcommand`() {
    val data = getLoggableCommandData("git unknown-sub", GIT_DATA, false)
    assertCommandData(data, "git", "third.party")
  }

  @Test
  fun `known command without arguments`() {
    val data = getLoggableCommandData("git", GIT_DATA, false)
    assertCommandData(data, "git", null)
  }

  @Test
  fun `subcommand matches any known command`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("git", "docker"),
      subCommands = setOf("commit"),
      maxSubCommandTokens = 1,
    )
    val data = getLoggableCommandData("docker commit", knownCommandsData, false)
    assertCommandData(data, "docker", "commit")
  }

  @Test
  fun `multiword subcommand`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv pip install pkg", knownCommandsData, false)
    assertCommandData(data, "uv", "pip install")
  }

  @Test
  fun `longest multiword subcommand wins`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip", "pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv pip install pkg", knownCommandsData, false)
    assertCommandData(data, "uv", "pip install")
  }

  @Test
  fun `falls back to shorter subcommand`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip", "pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv pip unknown", knownCommandsData, false)
    assertCommandData(data, "uv", "pip")
  }

  @Test
  fun `multiword subcommand no match`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("uv"),
      subCommands = setOf("pip install"),
      maxSubCommandTokens = 2,
    )
    val data = getLoggableCommandData("uv foo bar", knownCommandsData, false)
    assertCommandData(data, "uv", "third.party")
  }

  @Test
  fun `relative path`() {
    val data = getLoggableCommandData("./script.sh", EMPTY_DATA, false)
    assertCommandData(data, "<relative path>", null)
  }

  @Test
  fun `home-related path`() {
    val data = getLoggableCommandData("~/Documents/tool", EMPTY_DATA, false)
    assertCommandData(data, "<absolute path>", null)
  }

  @Test
  fun `absolute path unix`() {
    val data = getLoggableCommandData("/usr/bin/tool", EMPTY_DATA, false)
    assertCommandData(data, "<absolute path>", null)
  }

  @Test
  fun `absolute path windows`() {
    val data = getLoggableCommandData("C:\\Users\\tool.exe", EMPTY_DATA, false)
    assertCommandData(data, "<absolute path>", null)
  }

  @Test
  fun `command matching is case insensitive`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("get-childitem"),
      subCommands = emptySet(),
      maxSubCommandTokens = 1,
    )
    val data = getLoggableCommandData("Get-ChildItem", knownCommandsData, false)
    assertCommandData(data, "get-childitem", null)
  }

  @Test
  fun `subcommand matching is case sensitive`() {
    val data = getLoggableCommandData("git Commit", GIT_DATA, false)
    assertCommandData(data, "git", "third.party")
  }

  @Test
  fun `unknown command`() {
    val data = getLoggableCommandData("unknown-cmd", GIT_DATA, false)
    assertCommandData(data, "third.party", null)
  }

  @Test
  fun `absolute path expanded to known command`() {
    val data = getLoggableCommandData("/usr/bin/git", GIT_DATA, true)
    assertCommandData(data, "git", null)
  }

  @Test
  fun `absolute path expanded to known command with subcommand`() {
    val data = getLoggableCommandData("/usr/bin/git commit", GIT_DATA, true)
    assertCommandData(data, "git", "commit")
  }

  @Test
  fun `absolute path expanded to unknown command is third party`() {
    val data = getLoggableCommandData("/usr/bin/unknown-tool", GIT_DATA, true)
    assertCommandData(data, "third.party", null)
  }

  @Test
  fun `absolute windows path with known extension is trimmed when expanded`() {
    val data = getLoggableCommandData("C:\\Users\\git.exe push", GIT_DATA, true)
    assertCommandData(data, "git", "push")
  }

  @Test
  fun `home-related path expanded to known command`() {
    val data = getLoggableCommandData("~/bin/git pull", GIT_DATA, true)
    assertCommandData(data, "git", "pull")
  }

  @Test
  fun `relative path expanded to known command`() {
    val data = getLoggableCommandData("./git commit", GIT_DATA, true)
    assertCommandData(data, "git", "commit")
  }

  @Test
  fun `relative path expanded to unknown command is third party`() {
    val data = getLoggableCommandData("./script.sh", GIT_DATA, true)
    assertCommandData(data, "third.party", null)
  }

  @Test
  fun `relative path expanded to known command with unknown subcommand`() {
    val data = getLoggableCommandData("./git unknown-sub", GIT_DATA, true)
    assertCommandData(data, "git", "third.party")
  }

  @Test
  fun `gradlew takes precedence over path expansion`() {
    val knownCommandsData = KnownCommandsData(
      commands = setOf("gradle"),
      subCommands = setOf("build"),
      maxSubCommandTokens = 1,
    )
    val data = getLoggableCommandData("./gradlew build", knownCommandsData, true)
    assertCommandData(data, "gradle", "build")
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
