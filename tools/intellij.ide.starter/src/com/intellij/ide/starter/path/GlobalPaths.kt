package com.intellij.ide.starter.path

import com.intellij.ide.starter.teamcity.TeamCityCIServer
import com.intellij.ide.starter.utils.FileSystem.getDirectoryTreePresentableSizes
import com.intellij.ide.starter.utils.getDiskInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.div

abstract class GlobalPaths(val checkoutDir: Path) {
  val intelliJOutDirectory: Path = checkoutDir.toAbsolutePath() / "out"
  val artifactsDirectory: Path = intelliJOutDirectory / "artifacts"

  /**
   * Local => out
   * CI => out/tests
   */
  val compiledRootDirectory: Path = when (TeamCityCIServer.isBuildRunningOnCI) {
    true -> intelliJOutDirectory / "tests"
    false -> intelliJOutDirectory // Local run
  }

  // TODO: get rid of dependency on TeamCity
  val testHomePath = if (TeamCityCIServer.isBuildRunningOnCI) {
    val tempDirPath = Paths.get(System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir")))
    Files.createTempDirectory(tempDirPath, "startupPerformanceTests")
  }
  else {
    intelliJOutDirectory
  }.resolve("perf-startup").createDirectories()

  val installersDirectory = (testHomePath / "installers").createDirectories()

  val testsDirectory = (testHomePath / "tests").createDirectories()

  // TODO: get rid of dependency on TeamCity
  private val cacheDirectory: Path = if (TeamCityCIServer.isBuildRunningOnCI &&
    !System.getProperty("agent.persistent.cache").isNullOrEmpty()
  ) {
    (Paths.get(System.getProperty("agent.persistent.cache"), "perf-tests-cache")).createDirectories()
  }
  else {
    (testHomePath / "cache").createDirectories()
  }

  fun getCacheDirectoryFor(entity: String): Path = (cacheDirectory / entity).createDirectories()

  fun getDiskUsageDiagnostics(): String {
    return buildString {
      appendLine("Disk usage by integration tests (home $testHomePath)")
      appendLine(Files.getFileStore(testHomePath).getDiskInfo())
      appendLine()
      appendLine(testHomePath.getDirectoryTreePresentableSizes(3))
      if (cacheDirectory != testHomePath / "cache") {
        appendLine("Agent persistent cache directory disk usage $cacheDirectory")
        appendLine(cacheDirectory.getDirectoryTreePresentableSizes(2))
      }
    }
  }
}