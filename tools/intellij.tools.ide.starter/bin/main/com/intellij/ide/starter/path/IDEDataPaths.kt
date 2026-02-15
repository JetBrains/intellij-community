package com.intellij.ide.starter.path

import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.createInMemoryDirectory
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

@Suppress("unused")
open class IDEDataPaths(
  open val testHome: Path,
  private val inMemoryRoot: Path?,
) {

  companion object {
    inline fun <reified T> createPaths(testName: String, testHome: Path, useInMemoryFs: Boolean): T where T : Any {
      testHome.toFile().walkBottomUp().fold(true) { res, it ->
        (it.absolutePath.startsWith((testHome / "system").toFile().absolutePath) || it.delete() || !it.exists()) && res
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

  protected fun finalize() {
    if (inMemoryRoot != null) {
      try {
        inMemoryRoot.deleteRecursivelyQuietly()
      }
      catch (e: Exception) {
        logOutput("! Failed to unmount in-memory FS at $inMemoryRoot")
        e.stackTraceToString().lines().forEach { logOutput("    $it") }
      }
    }
  }

  override fun toString(): String = "IDE Test Paths at $testHome"
}