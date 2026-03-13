package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(5, unit = TimeUnit.MINUTES)
class BunCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("bun.cmd")
    val result = CmdToolTestUtil.runTool("bun.cmd", "--version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).isEqualTo(version)
  }

  @Test
  fun runHelloScript(@TempDir tempDir: Path) {
    tempDir.resolve("hello.js").writeText("""console.log("hello from bun")""")

    val result = CmdToolTestUtil.runTool("bun.cmd", "run", tempDir.resolve("hello.js").toAbsolutePath().toString())
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).isEqualTo("hello from bun")
  }
}
