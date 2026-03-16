package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(5, unit = TimeUnit.MINUTES)
class NpxCmdTest {
  @Test
  fun version() {
    val result = CmdToolTestUtil.runTool("npx.cmd", "--version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).matches("\\d+\\.\\d+\\.\\d+")
  }

  @Test
  fun help() {
    val result = CmdToolTestUtil.runTool("npx.cmd", "--help")
    assertThat(result.exitCode).isZero()
    assertThat(result.combinedOutput).contains("npm")
  }
}
