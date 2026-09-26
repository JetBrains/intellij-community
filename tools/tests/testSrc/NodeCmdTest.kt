package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(5, unit = TimeUnit.MINUTES)
class NodeCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("node.cmd")
    val result = CmdToolTestUtil.runTool("node.cmd", "--version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).isEqualTo("v$version")
  }

  @Test
  fun runHelloScript(@TempDir tempDir: Path) {
    tempDir.resolve("hello.js").writeText("""console.log("hello from node")""")

    val result = CmdToolTestUtil.runTool("node.cmd", tempDir.resolve("hello.js").toAbsolutePath().toString())
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).isEqualTo("hello from node")
  }
}
