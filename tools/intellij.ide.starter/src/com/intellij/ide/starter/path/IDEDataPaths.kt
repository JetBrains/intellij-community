package com.intellij.ide.starter.path

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.utils.FileSystem.getFileOrDirectoryPresentableSize
import com.intellij.ide.starter.utils.createInMemoryDirectory
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.streams.toList

class IDEDataPaths(
  private val testHome: Path,
  private val inMemoryRoot: Path?
) : Closeable {

  companion object {
    fun createPaths(testName: String, testHome: Path, useInMemoryFs: Boolean): IDEDataPaths {
      testHome.toFile().deleteRecursively()
      testHome.createDirectories()
      val inMemoryRoot = if (useInMemoryFs) {
        createInMemoryDirectory("ide-integration-test-$testName")
      }
      else {
        null
      }
      return IDEDataPaths(testHome = testHome, inMemoryRoot = inMemoryRoot)
    }
  }

  val logsDir = (testHome / "log").createDirectories()
  val reportsDir = (testHome / "reports").createDirectories()
  val snapshotsDir = (testHome / "snapshots").createDirectories()
  val tempDir = (testHome / "temp").createDirectories()

  // Directory used to store TeamCity artifacts. To make sure the TeamCity publishes all artifacts
  // files added to this directory must not be removed until the end of the tests execution .
  val teamCityArtifacts = (testHome / "team-city-artifacts").createDirectories()

  val configDir = ((inMemoryRoot ?: testHome) / "config").createDirectories()
  val systemDir = ((inMemoryRoot ?: testHome) / "system").createDirectories()
  val pluginsDir = (testHome / "plugins").createDirectories()

  override fun close() {
    if (inMemoryRoot != null) {
      try {
        inMemoryRoot.toFile().deleteRecursively()
      }
      catch (e: Exception) {
        logOutput("! Failed to unmount in-memory FS at $inMemoryRoot")
        e.stackTraceToString().lines().forEach { logOutput("    $it") }
      }
    }

    if (di.direct.instance<CIServer>().isBuildRunningOnCI) {
      deleteDirectories()
    }
  }

  private fun deleteDirectories() {
    val toDelete = getDirectoriesToDeleteAfterTest().filter { it.exists() }

    if (toDelete.isNotEmpty()) {
      logOutput(buildString {
        appendLine("Removing directories of $testHome")
        toDelete.forEach { path ->
          appendLine("  $path: ${path.getFileOrDirectoryPresentableSize()}")
        }
      })
    }

    toDelete.forEach { runCatching { it.toFile().deleteRecursively() } }
  }

  private fun getDirectoriesToDeleteAfterTest() = if (testHome.exists()) {
    Files.list(testHome).use { it.toList() } - listOf(teamCityArtifacts)
  }
  else {
    emptyList()
  }

  override fun toString(): String = "IDE Test Paths at $testHome"
}