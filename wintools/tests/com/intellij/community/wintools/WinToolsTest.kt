package com.intellij.community.wintools

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString


internal class WinToolsTest {
  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testLock() {
    val path = systemCmd()
    val cmdProcess = ProcessBuilder(path.pathString).start()

    val info = WinProcessInfo.get(cmdProcess.pid()).getOrThrow()
    assertEquals(path.pathString, info.commandLine, "Wrong command line")

    val processPids = getProcessLockedPath(path).getOrThrow().map { it.pid() }
    assertThat("No cmd.exe found among processes that lock file", processPids, CoreMatchers.hasItem(info.pid))
  }

  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testArguments(@TempDir tempDir: Path) {
    // Windows quotes a path that holds a space, so a split on a space gives the wrong arguments.
    val executable = tempDir.resolve("dir with space").createDirectories().resolve("cmd.exe")
    systemCmd().copyTo(executable)

    val process = ProcessBuilder(executable.pathString, "/c", "pause").start()
    try {
      val info = WinProcessInfo.get(process.pid()).getOrThrow()
      assertEquals(listOf(executable.pathString, "/c", "pause"), info.arguments, "Wrong arguments")
    }
    finally {
      process.destroyForcibly()
      process.waitFor()
    }
  }
}

private fun systemCmd(): Path = Path(System.getenv("SystemRoot") ?: "c:\\windows", "system32/cmd.exe")
