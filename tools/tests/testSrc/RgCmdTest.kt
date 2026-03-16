package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(5, unit = TimeUnit.MINUTES)
class RgCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("rg.cmd")
    val result = CmdToolTestUtil.runTool("rg.cmd", "--version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).startsWith("ripgrep $version")
  }

  @Test
  fun searchForPattern(@TempDir tempDir: Path) {
    tempDir.resolve("sample.txt").writeText("the quick brown fox\njumps over the lazy dog\n")

    val result = CmdToolTestUtil.runTool("rg.cmd", "quick", tempDir.toAbsolutePath().toString())
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).contains("quick brown fox")
  }
}
