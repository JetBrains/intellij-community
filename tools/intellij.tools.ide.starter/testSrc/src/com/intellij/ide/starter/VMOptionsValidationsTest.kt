package com.intellij.ide.starter

import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.isInformationalLaunch
import com.intellij.ide.starter.runner.readDirWatermarks
import com.intellij.ide.starter.runner.validateVMOptionsWereSet
import com.intellij.openapi.util.io.NioFiles
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeText

/**
 * Covers the validation that asks whether the IDE received the `.vmoptions` of its run.
 *
 * No IDE runs here: the test creates the files an IDE creates at its start, and reads what the validation reports.
 */
class VMOptionsValidationsTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `an entry in each directory reports nothing`() {
    val paths = IDEDataPaths(testHome = tempDir, inMemoryRoot = null)
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.configDir, ".lock", watermarks.getValue(paths.configDir))
    createAfterTheLaunch(paths.systemDir, ".port", watermarks.getValue(paths.systemDir))

    failuresReportedWhile { validateVMOptionsWereSet(watermarks) }.shouldBeEmpty()
  }

  @Test
  fun `a directory with no new entry is reported`() {
    val paths = IDEDataPaths(testHome = tempDir, inMemoryRoot = null)
    (paths.configDir / "options" / "other.xml").write("seeded by the baseline")
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.systemDir, ".port", watermarks.getValue(paths.systemDir))

    val failures = failuresReportedWhile { validateVMOptionsWereSet(watermarks) }

    failures.size shouldBe 1
    (paths.configDir.toString() in failures.single().testName) shouldBe true
  }

  @Test
  fun `an entry in the shared system directory reports nothing`() {
    val sharedSystemDir = tempDir / "shared-system"
    val paths = IDEDataPaths(testHome = tempDir, inMemoryRoot = null, sharedSystemDir = sharedSystemDir)
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.configDir, ".lock", watermarks.getValue(paths.configDir))
    createAfterTheLaunch(sharedSystemDir, ".port", watermarks.getValue(paths.systemDir))

    failuresReportedWhile { validateVMOptionsWereSet(watermarks) }.shouldBeEmpty()
  }

  @Test
  fun `a system directory deleted before the launch is checked`() {
    val paths = IDEDataPaths(testHome = tempDir, inMemoryRoot = null)
    // the same delete as IDETestContext.wipeSystemDir, which a new context calls before its first launch
    NioFiles.deleteRecursively(paths.systemDir)
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.configDir, ".lock", watermarks.getValue(paths.configDir))
    (paths.systemDir / ".port").write("written by the IDE")

    (paths.systemDir in watermarks) shouldBe true
    failuresReportedWhile { validateVMOptionsWereSet(watermarks) }.shouldBeEmpty()
  }

  @Test
  fun `a system directory that the IDE did not create is reported`() {
    val paths = IDEDataPaths(testHome = tempDir, inMemoryRoot = null)
    NioFiles.deleteRecursively(paths.systemDir)
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.configDir, ".lock", watermarks.getValue(paths.configDir))

    val failures = failuresReportedWhile { validateVMOptionsWereSet(watermarks) }

    failures.size shouldBe 1
    (paths.systemDir.toString() in failures.single().testName) shouldBe true
  }

  @Test
  fun `a frontend is checked in its per-process directories`() {
    val paths = FrontendIDEDataPaths(testHome = tempDir / "frontend", inMemoryRoot = null)
    (paths.configDir / "options" / "other.xml").write("seeded by the baseline")
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.perProcessConfigDir, ".lock", watermarks.getValue(paths.perProcessConfigDir))
    createAfterTheLaunch(paths.perProcessSystemDir, ".port", watermarks.getValue(paths.perProcessSystemDir))

    failuresReportedWhile { validateVMOptionsWereSet(watermarks) }.shouldBeEmpty()
  }

  @Test
  fun `a frontend that did not create its per-process directories is reported`() {
    val paths = FrontendIDEDataPaths(testHome = tempDir / "frontend", inMemoryRoot = null)
    val watermarks = readDirWatermarks(paths)

    createAfterTheLaunch(paths.configDir, ".lock", watermarks.getValue(paths.perProcessConfigDir))

    failuresReportedWhile { validateVMOptionsWereSet(watermarks) }.size shouldBe 2
  }

  @Test
  fun `a launch that only prints an answer is informational`() {
    isInformationalLaunch(listOf("--help")) shouldBe true
    isInformationalLaunch(listOf("--edit", "--help")) shouldBe true
    isInformationalLaunch(listOf("--list-commands")) shouldBe true
    isInformationalLaunch(listOf("-Didea.is.internal=true", "nosplash", "--version")) shouldBe true
  }

  @Test
  fun `a launch that starts the IDE is not informational`() {
    isInformationalLaunch(emptyList()) shouldBe false
    isInformationalLaunch(listOf("installPlugins", "there.is.no.such.plugin")) shouldBe false
    isInformationalLaunch(listOf("/path/to/project", "--version")) shouldBe false
  }

  /**
   * Creates the file [name] in [dir], and gives [dir] a time one second past [watermark].
   *
   * The time is set rather than taken from the clock, because a file system with a one second granularity can stamp
   * a change right after the watermark with the time of the watermark itself.
   */
  private fun createAfterTheLaunch(dir: Path, name: String, watermark: FileTime) {
    (dir / name).write("written by the IDE")
    dir.setLastModifiedTime(FileTime.from(watermark.toInstant().plusSeconds(1)))
  }

  private fun Path.write(content: String) {
    parent.createDirectories()
    writeText(content)
  }
}
