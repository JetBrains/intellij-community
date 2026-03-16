package com.intellij.tools.cmd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

@Timeout(5, unit = TimeUnit.MINUTES)
class GoCmdTest {
  @Test
  fun version() {
    val version = CmdToolTestUtil.parseToolVersion("go.cmd")
    val result = CmdToolTestUtil.runTool("go.cmd", "version")
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).startsWith("go version go$version")
  }

  @Test
  fun runHelloWorld(@TempDir tempDir: Path) {
    tempDir.resolve("hello.go").writeText("""
      package main
      import "fmt"
      func main() { fmt.Println("hello from go") }
    """.trimIndent())

    val result = CmdToolTestUtil.runTool("go.cmd", "run", tempDir.resolve("hello.go").toAbsolutePath().toString())
    assertThat(result.exitCode).isZero()
    assertThat(result.stdout).isEqualTo("hello from go")
  }
}
