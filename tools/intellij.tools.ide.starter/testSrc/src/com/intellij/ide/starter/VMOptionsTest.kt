package com.intellij.ide.starter

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.ReportingPathUtils.PATH_LENGTH_LIMIT
import com.intellij.ide.starter.utils.ReportingPathUtils.WIDEST_CRASH_LOG_NAME
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.syncRestoredProjectFiles
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines

class VMOptionsTest {
  @TempDir
  lateinit var tempDir: Path

  /** A process id is 32 bits wide at most, so reserving a `Long`'s worth of digits rejects directories every real process fits into. */
  @Test
  fun `a crash log name is reserved for the widest process id there can be`() {
    WIDEST_CRASH_LOG_NAME shouldBe "java_error_in_idea_4294967295.log"
  }

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

  @Test
  fun `plugin paths are appended in order and a known path is not added again`() {
    val options = vmOptions()
    val first = tempDir.resolve("pool/devkit")
    val second = tempDir.resolve("a=b/android")

    options.addPluginPath(first)
    options.addPluginPath(second)
    options.addPluginPath(first)

    options.pluginPaths() shouldBe listOf(first.toAbsolutePath(), second.toAbsolutePath())
    options.data().filter { it.startsWith("-Dplugin.path=") } shouldBe
      listOf("-Dplugin.path=${first.toAbsolutePath()}${File.pathSeparator}${second.toAbsolutePath()}")
  }

  @Test
  fun `no plugin path gives an empty list`() {
    vmOptions().pluginPaths().shouldBeEmpty()
  }

  @Test
  fun `the setup script holds the setup commands, and its option points to it`() {
    val paths = IDEDataPaths(tempDir, null)
    val vmOptions = vmOptions()

    vmOptions.installSetupScript("some/test", paths, CommandChain().syncRestoredProjectFiles())

    val scriptFile = paths.systemDir.resolve("some_test.setup.text")
    scriptFile.readLines() shouldBe listOf("%syncRestoredProjectFiles")
    vmOptions.data() shouldContain "-D${VMOptions.SETUP_SCRIPT_FILE_OPTION}=${scriptFile.toAbsolutePath()}"
  }

  private fun vmOptions(): VMOptions = VMOptions(ide = mock(InstalledIde::class.java), data = emptyList(), env = emptyMap())

  private fun directoryWithAbsoluteLength(targetLength: Int): Path {
    val root = tempDir.toAbsolutePath().normalize()
    val paddingLength = targetLength - root.toString().length - 1
    require(paddingLength > 0) { "The temporary directory is already longer than the $targetLength characters asked for" }
    return root.resolve("x".repeat(paddingLength))
  }
}
