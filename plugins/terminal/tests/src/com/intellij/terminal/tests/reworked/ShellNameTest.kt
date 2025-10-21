package com.intellij.terminal.tests.reworked

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.ShellName
import org.junit.Test

internal class ShellNameTest {
  @Test
  fun `check comparison of created shell names with defined constants`() {
    testShellNamesComparison(
      expectedName = ShellName.ZSH,
      nameVariants = listOf("zsh", "Zsh", "ZSH")
    )
    testShellNamesComparison(
      expectedName = ShellName.BASH,
      nameVariants = listOf("bash", "Bash", "BASH")
    )
    testShellNamesComparison(
      expectedName = ShellName.FISH,
      nameVariants = listOf("fish", "Fish", "FISH")
    )
    testShellNamesComparison(
      expectedName = ShellName.POWERSHELL,
      nameVariants = listOf("powershell", "PowerShell", "POWERSHELL")
    )
    testShellNamesComparison(
      expectedName = ShellName.PWSH,
      nameVariants = listOf("pwsh", "Pwsh", "PWSH")
    )
  }

  @Test
  fun `check isPowerShell detects powershell correctly`() {
    val shellNames = listOf("powershell", "PowerShell", "POWERSHELL", "pwsh", "Pwsh", "PWSH").map { ShellName.of(it) }
    val allNames = shellNames + listOf(ShellName.POWERSHELL, ShellName.PWSH)
    assertThat(allNames).allMatch { ShellName.isPowerShell(it) }
  }

  private fun testShellNamesComparison(expectedName: ShellName, nameVariants: List<String>) {
    val names = nameVariants.map { ShellName.of(it) }
    assertThat(names).allMatch { it == expectedName }
  }
}