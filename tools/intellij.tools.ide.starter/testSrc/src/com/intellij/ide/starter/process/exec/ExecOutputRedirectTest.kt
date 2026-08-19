package com.intellij.ide.starter.process.exec

import com.intellij.openapi.util.SystemInfoRt
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class ExecOutputRedirectTest {
  @Test
  fun `stdout tail retains only the configured number of lines`() {
    val redirect = ExecOutputRedirect.ToStdOutAndTail("[ide-test-err]", maxLines = 2)

    redirect.redirectLine("first")
    redirect.redirectLine("second")
    redirect.redirectLine("third")

    redirect.read().lines() shouldContainExactly listOf(
      "[ide-test-err] second",
      "[ide-test-err] third",
    )
  }

  @Test
  fun `failed process diagnostics include retained stderr`() {
    val invalidOption = "-XX:StarterDefinitelyInvalidOption"
    val javaExecutable = Path.of(
      System.getProperty("java.home"),
      "bin",
      if (SystemInfoRt.isWindows) "java.exe" else "java",
    )
    val error = shouldThrow<IllegalStateException> {
      ProcessExecutor(
        presentableName = "invalid-java-option",
        workDir = null,
        timeout = 10.seconds,
        args = listOf(javaExecutable.toString(), invalidOption),
        stderrRedirect = ExecOutputRedirect.ToStdOutAndTail("[java-err]"),
        silent = true,
      ).start()
    }

    error.message shouldContain invalidOption.removePrefix("-XX:")
    error.message shouldContain "standard error stream"
  }
}
