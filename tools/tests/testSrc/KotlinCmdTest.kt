package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(10, unit = TimeUnit.MINUTES)
class KotlinCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("kotlin.cmd", variableName = "KOTLIN_VERSION")
    val result = CmdToolTestUtil.runTool("kotlin.cmd", "-version")
    assertThat(result.exitCode).isZero()
    assertThat(result.combinedOutput).contains(version)
  }

  @Test
  fun runScript(@TempDir tempDir: Path) {
    tempDir.resolve("hello.main.kts").writeText("""println("hello from kotlin")""")

    val result = CmdToolTestUtil.runTool("kotlin.cmd", tempDir.resolve("hello.main.kts").toAbsolutePath().toString())
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).contains("hello from kotlin")
  }
}
