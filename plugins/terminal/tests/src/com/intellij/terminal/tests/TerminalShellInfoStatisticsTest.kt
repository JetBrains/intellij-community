package com.intellij.terminal.tests

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.fus.TerminalShellInfoStatistics
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TerminalShellInfoStatisticsTest {
  @Test
  fun testGetShellNameForStat() {
    val cases = listOf(
      null to "unspecified",
      "" to "unspecified",
      "my-custom-shell" to "other",
      "bash" to "bash",
      "zsh" to "zsh",
      "powershell" to "powershell",
      "pwsh" to "pwsh",
      "cmd" to "cmd",
      "cmd.exe" to "cmd",
      "BASH" to "bash",
      "/bin/bash" to "bash",
      "/bin/zsh --login" to "zsh",
      """C:\Windows\System32\cmd.exe""" to "cmd",
      """C:\Windows\System32\cmd.exe --abcd --123""" to "cmd",
      """"C:\Program Files\PowerShell\7\pwsh.exe"""" to "pwsh",
      """"C:\Program Files\PowerShell\7\PWSH.exe"""" to "pwsh",
      """"C:\Program Files\PowerShell\7\pwsh.exe" --abcd""" to "pwsh",
    )

    for ((input, expected) in cases) {
      val actual = TerminalShellInfoStatistics.getShellNameForStat(input)
      assertThat(actual)
        .overridingErrorMessage { "Expected: $expected, Actual: $actual, Input: $input" }
        .isEqualTo(expected)
    }
  }
}
