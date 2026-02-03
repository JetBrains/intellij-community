package com.intellij.community.wintools

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.Path
import kotlin.io.path.pathString


class WinToolsTest {
  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun testLock() {
    val systemRoot = System.getenv("SystemRoot") ?: "c:\\windows"
    val path = Path(systemRoot, "system32/cmd.exe")
    val cmdProcess = ProcessBuilder(path.pathString).start()

    val info = WinProcessInfo.get(cmdProcess.pid()).getOrThrow()
    assertEquals(path.pathString, info.commandLine, "Wrong command line")

    val processPids = getProcessLockedPath(path).getOrThrow().map { it.pid() }
    assertThat("No cmd.exe found among processes that lock file", processPids, CoreMatchers.hasItem(info.pid))
  }
}