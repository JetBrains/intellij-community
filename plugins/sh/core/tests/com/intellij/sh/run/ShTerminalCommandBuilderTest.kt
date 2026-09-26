// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run

import com.intellij.platform.eel.EelOsFamily
import com.intellij.sh.parser.ShShebangParserUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class ShTerminalCommandBuilderTest {
  @Test
  fun `posix script with interpreter options and script options`() {
    val command = ShTerminalCommandBuilder.scriptFileCommand(
      "/usr/bin/env", "bash", "/home/user/my scripts/build.sh", "--fast", emptyMap(), EelOsFamily.Posix,
    )
    assertEquals("/usr/bin/env bash \"/home/user/my scripts/build.sh\" --fast", command)
  }

  @Test
  fun `posix environment variables precede the interpreter`() {
    val command = ShTerminalCommandBuilder.scriptFileCommand(
      "/bin/bash", "", "/tmp/run.sh", "", linkedMapOf("A" to "1", "B" to "two words"), EelOsFamily.Posix,
    )
    assertEquals("A=1 B=two\\ words /bin/bash /tmp/run.sh", command)
  }

  @Test
  fun `empty interpreter runs the script by its shebang`() {
    val command = ShTerminalCommandBuilder.scriptFileCommand("", "", "/tmp/tool.py", "", emptyMap(), EelOsFamily.Posix)
    assertEquals("/tmp/tool.py", command)
  }

  @Test
  fun `windows paths are escaped for the shell`() {
    val command = ShTerminalCommandBuilder.scriptFileCommand(
      "C:\\Program Files\\Git\\bin\\bash.exe", "", "C:\\work\\run.sh", "", mapOf("X" to "a b"), EelOsFamily.Windows,
    )
    assertEquals("X=\"a b\" C:\\\\Program\\ Files\\\\Git\\\\bin\\\\bash.exe C:\\\\work\\\\run.sh", command)
  }

  @Test
  fun `script text exports the environment first`() {
    val command = ShTerminalCommandBuilder.scriptTextCommand("echo \$A", linkedMapOf("A" to "1", "B" to "2"), EelOsFamily.Posix)
    assertEquals("export A=1 export B=2; echo \$A", command)
  }

  @Test
  fun `shebang splits into interpreter and a trailing option`() {
    val result = ShShebangParserUtil.parseInterpreterAndOptions("/usr/bin/env bash")
    assertEquals("/usr/bin/env", result.first)
    assertEquals("bash", result.second)
  }

  @Test
  fun `shebang without options keeps the whole interpreter`() {
    val result = ShShebangParserUtil.parseInterpreterAndOptions("/bin/bash")
    assertEquals("/bin/bash", result.first)
    assertEquals("", result.second)
  }
}
