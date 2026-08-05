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
  private val inMemoryRoot: Path?,
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
  open val eventLogMetadataDir = (configDir / "event-log-metadata").createDirectories()
  open val eventLogDataDir = (systemDir / "event-log-data").createDirectories()

  /**
   * Returns a [FrontendIDEDataPaths] over the same directories and hands the ownership of [inMemoryRoot] over to it:
   * the returned instance becomes responsible for deleting the in-memory root, and this one must not be used anymore.
   *
   * Unlike [createPaths] this neither wipes nor re-creates [testHome], so it is safe to call for paths already in use.
   */
  internal fun asFrontendDataPaths(): FrontendIDEDataPaths {
    val frontendPaths = FrontendIDEDataPaths(testHome, inMemoryRoot)
    inMemoryRootOwner.released = true
    return frontendPaths
  }

  override fun close() {
    cleanable.clean()
  }

  private val inMemoryRootOwner = InMemoryRootOwner(inMemoryRoot)
  private val cleanable = CLEANER.register(this, inMemoryRootOwner)

  /** Must not hold a reference to the owning [IDEDataPaths], otherwise the [CLEANER] would never collect it. */
  private class InMemoryRootOwner(private val inMemoryRoot: Path?) : Runnable {
    @Volatile
    var released: Boolean = false

    override fun run() {
      if (released || inMemoryRoot == null) return
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
