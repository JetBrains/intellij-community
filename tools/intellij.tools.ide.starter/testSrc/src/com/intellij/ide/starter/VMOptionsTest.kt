package com.intellij.ide.starter

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.ReportingPathUtils.PATH_LENGTH_LIMIT
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path

/** The widest process id `%p` can be expanded to, on the OS that allows the widest one. */
private const val WIDEST_CRASH_LOG_NAME = "java_error_in_idea_4294967295.log"

class VMOptionsTest {
  @TempDir
  lateinit var tempDir: Path

  /**
   * `-XX:ErrorFile` is checked against a placeholder id, because the JVM only expands `%p` once it crashes. Reserving room for a `Long`
   * rather than for a process id reported crash directories that every real process would have written into.
   */
  @Test
  fun `a crash log directory is accepted when the widest process id still fits`() {
    val crashLogDirectory = directoryWithAbsoluteLength(PATH_LENGTH_LIMIT - 1 - "/$WIDEST_CRASH_LOG_NAME".length)

    val reported = failuresReportedWhile {
      vmOptions().withJvmCrashLogDirectory(crashLogDirectory)
    }

    reported.shouldBeEmpty()
  }

  @Test
  fun `a crash log directory no process id fits into is reported`() {
    val crashLogDirectory = directoryWithAbsoluteLength(PATH_LENGTH_LIMIT - "/java_error_in_idea_1.log".length)

    val reported = failuresReportedWhile {
      vmOptions().withJvmCrashLogDirectory(crashLogDirectory)
    }

    reported.single().message shouldContain "$PATH_LENGTH_LIMIT-character limit"
  }

  private fun vmOptions(): VMOptions = VMOptions(ide = mock(InstalledIde::class.java), data = emptyList(), env = emptyMap())

  private fun directoryWithAbsoluteLength(targetLength: Int): Path {
    val root = tempDir.toAbsolutePath().normalize()
    val paddingLength = targetLength - root.toString().length - 1
    require(paddingLength > 0) { "The temporary directory is already longer than the $targetLength characters asked for" }
    return root.resolve("x".repeat(paddingLength))
  }
}
