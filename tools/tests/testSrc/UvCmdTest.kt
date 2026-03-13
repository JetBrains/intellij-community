package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(5, unit = TimeUnit.MINUTES)
class UvCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("uv.cmd")
    val result = CmdToolTestUtil.runTool("uv.cmd", "--version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).startsWith("uv $version")
  }

  @Test
  fun help() {
    val result = CmdToolTestUtil.runTool("uv.cmd", "--help")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).contains("An extremely fast Python package manager")
  }
}
