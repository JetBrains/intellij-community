package com.intellij.ide.starter.path

import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.createInMemoryDirectory
import com.intellij.tools.ide.util.common.logOutput
import java.lang.ref.Cleaner
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.name

open class IDEDataPaths(
  open val testHome: Path,
  inMemoryRoot: Path?,
) : AutoCloseable {

  companion object {
    private val CLEANER = Cleaner.create()

    inline fun <reified T> createPaths(testName: String, testHome: Path, useInMemoryFs: Boolean): T where T : Any {
      val isTestHomeCleanupSuccessful = testHome.listDirectoryEntriesQuietly()
        ?.filterNot { it.name == "system" && it.isDirectory() }
        ?.all { it.deleteRecursivelyQuietly() }
      if (isTestHomeCleanupSuccessful == false) {
        logOutput("Failed to delete some entries in $testHome\nLeft directories: ${testHome.listDirectoryEntriesQuietly()?.joinToString()}")
      }
      testHome.createDirectories()
      val inMemoryRoot = if (useInMemoryFs) {
        createInMemoryDirectory("ide-integration-test-$testName")
      }
      else {
        null
      }
      return T::class.java.getConstructor(Path::class.java, Path::class.java)
        .newInstance(testHome, inMemoryRoot)
    }
  }

  val tempDir = (testHome / "temp").createDirectories()

  val configDir = ((inMemoryRoot ?: testHome) / "config").createDirectories()
  val systemDir = ((inMemoryRoot ?: testHome) / "system").createDirectories()
  val pluginsDir = (testHome / "plugins").createDirectories()
  val jbrDiagnostic = (testHome / "jbrDiagnostic").createDirectories()
  open val eventLogMetadataDir = (configDir / "event-log-metadata").createDirectories()
  open val eventLogDataDir = (systemDir / "event-log-data").createDirectories()

  override fun close() {
    cleanable.clean()
  }

  private val cleanable = CLEANER.register(this, Runnable {
    if (inMemoryRoot != null) {
      try {
        inMemoryRoot.deleteRecursivelyQuietly()
      }
      catch (e: Exception) {
        logOutput("! Failed to unmount in-memory FS at $inMemoryRoot")
        e.stackTraceToString().lines().forEach { logOutput("    $it") }
      }
    }
  })

  override fun toString(): String = "IDE Test Paths at $testHome"
}