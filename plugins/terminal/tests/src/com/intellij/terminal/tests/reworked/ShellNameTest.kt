package com.intellij.terminal.tests.reworked

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
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

  private fun testShellNamesComparison(expectedName: ShellName, nameVariants: List<String>) {
    val names = nameVariants.map { ShellName.of(it) }
    assertThat(names).allMatch { it == expectedName }
  }

  @Test
  fun `check isPowerShell detects powershell correctly`() {
    val shellNames = listOf("powershell", "PowerShell", "POWERSHELL", "pwsh", "Pwsh", "PWSH").map { ShellName.of(it) }
    val allNames = shellNames + listOf(ShellName.POWERSHELL, ShellName.PWSH)
    assertThat(allNames).allMatch { ShellName.isPowerShell(it) }
  }

  @Test
  fun `check guess shell name for Unix paths`() {
    testGuessShellName("/bin/bash", ShellName.BASH)
    testGuessShellName("/usr/local/bin/zsh", ShellName.ZSH)
    testGuessShellName("fish", ShellName.FISH)
    testGuessShellName("BAsh.pupu", ShellName.BASH)
    testGuessShellName("/home/user/bin/my-shell", ShellName.of("my-shell"))
    testGuessShellName("/home/my name/bin/zsh", ShellName.ZSH)
    testGuessShellName("/bin/zsh.sh", ShellName.ZSH)
    testGuessShellName("/bin/Zsh.sh", ShellName.ZSH)
  }

  @Test
  fun `check guess shell name for Windows paths`() {
    testGuessShellName("""C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe""", ShellName.POWERSHELL)
    testGuessShellName("""C:\Windows\System32\WindowsPowerShell\v1.0\PowerShell.exe""", ShellName.POWERSHELL)
    testGuessShellName("""C:\Program Files\PowerShell\7\pwsh.exe""", ShellName.PWSH)
    testGuessShellName("""C:\Windows\System32\cmd.exe""", ShellName.of("cmd"))
    testGuessShellName("pwsh.exe", ShellName.PWSH)
    testGuessShellName("powershell.exe", ShellName.POWERSHELL)
    testGuessShellName("pwsh", ShellName.PWSH)
    testGuessShellName("powershell", ShellName.POWERSHELL)
    testGuessShellName("POWERshell.pupu", ShellName.POWERSHELL)
  }

  private fun testGuessShellName(executablePath: String, expectedName: ShellName) {
    val options = TerminalStartupOptionsImpl(
      shellCommand = listOf(executablePath),
      workingDirectory = "",
      envVariables = emptyMap(),
      pid = null,
    )
    assertThat(options.guessShellName()).isEqualTo(expectedName)
  }
}