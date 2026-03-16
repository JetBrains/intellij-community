package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(5, unit = TimeUnit.MINUTES)
class FdCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("fd.cmd").removePrefix("v")
    val result = CmdToolTestUtil.runTool("fd.cmd", "--version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).isEqualTo("fd $version")
  }

  @Test
  fun findFilesByExtension(@TempDir tempDir: Path) {
    tempDir.resolve("hello.txt").writeText("hello")
    tempDir.resolve("world.txt").writeText("world")
    tempDir.resolve("readme.md").writeText("readme")

    val result = CmdToolTestUtil.runTool("fd.cmd", "-e", "txt", workingDir = tempDir)
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).contains("hello.txt")
    assertThat(result.stdout).contains("world.txt")
    assertThat(result.stdout).doesNotContain("readme.md")
  }
}
