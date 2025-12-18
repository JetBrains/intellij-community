package com.intellij.terminal.tests

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics
import org.junit.Test

internal class TerminalResourcesTest {
  @Test
  fun `known-commands list is alphabetically sorted`() {
    assertThat(getKnownCommandsList()).isSorted
  }

  @Test
  fun `known-commands list contains no duplicates`() {
    assertThat(getKnownCommandsList()).doesNotHaveDuplicates()
  }

  @Test
  fun `known-commands list contains no blank lines`() {
    assertThat(getKnownCommandsList()).allMatch { it.isNotBlank() }
  }

  @Test
  fun `known-commands list items doesn't start or end with spaces`() {
    assertThat(getKnownCommandsList()).allMatch { it.trim() == it }
  }

  private fun getKnownCommandsList(): List<String> {
    return TerminalCommandUsageStatistics.javaClass.getResourceAsStream("known-commands.txt")!!
      .readAllBytes()
      .toString(Charsets.UTF_8)
      .split("\n")
  }
}