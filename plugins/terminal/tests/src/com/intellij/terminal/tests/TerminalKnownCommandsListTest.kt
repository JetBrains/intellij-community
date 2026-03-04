package com.intellij.terminal.tests

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jetbrains.plugins.terminal.fus.TerminalCommandUsageStatistics
import org.junit.Test

internal class TerminalKnownCommandsListTest {
  @Test
  fun `list is alphabetically sorted`() {
    val actual = getKnownCommandsList()
    val expected = actual.sorted()
    assertListEquals(expected, actual)
  }

  @Test
  fun `list contains no duplicates`() {
    val actual = getKnownCommandsList()
    val expected = actual.distinct()
    assertListEquals(expected, actual)
  }

  @Test
  fun `list contains no blank lines`() {
    val actual = getKnownCommandsList()
    val expected = actual.filter { it.isNotBlank() }
    assertListEquals(expected, actual)
  }

  @Test
  fun `list items doesn't start or end with spaces`() {
    val actual = getKnownCommandsList()
    val expected = actual.map { it.trim() }
    assertListEquals(expected, actual)
  }

  @Test
  fun `list items have words separated by a single space`() {
    val actual = getKnownCommandsList()
    val regex = Regex(" +")
    val expected = actual.map { it.replace(regex, " ") }
    assertListEquals(expected, actual)
  }

  @Test
  fun `multiword commands have their parent commands listed`() {
    val actual = getKnownCommandsList()
    val commands = actual.toSet()
    val missingPrefixes = mutableSetOf<String>()
    for (command in commands) {
      val words = command.split(" ")
      for (i in 1 until words.size) {
        val prefix = words.subList(0, i).joinToString(" ")
        if (prefix !in commands) {
          missingPrefixes.add(prefix)
        }
      }
    }
    val expected = (actual + missingPrefixes).sorted().distinct()
    assertListEquals(expected, actual)
  }

  private fun getKnownCommandsList(): List<String> {
    return TerminalCommandUsageStatistics.javaClass.getResourceAsStream("known-commands.txt")!!
      .readAllBytes()
      .toString(Charsets.UTF_8)
      .split("\n")
  }

  private fun assertListEquals(expected: List<String>, actual: List<String>) {
    if (actual != expected) {
      throw FileComparisonFailedError(
        message = "Actual list doesn't match the expected one",
        expected = expected.joinToString("\n"),
        actual = actual.joinToString("\n")
      )
    }
  }
}