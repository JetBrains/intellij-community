package com.intellij.ide.starter

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.project.NoProject
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.TestMetadata
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import com.sun.nio.file.ExtendedOpenOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class IDETestContextCleanupTest {
  @TempDir
  lateinit var testDirectory: Path

  @Test
  fun `cleanup removes caches and preserves other directories`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      paths.systemDir.resolve("projects/project/cache").createDirectories().resolve("workspace.dat").writeText("cache")
      paths.systemDir.resolve("caches").createDirectories().resolve("vfs.dat").writeText("cache")
      val sdk = paths.configDir.resolve("options").createDirectories().resolve("jdk.table.xml")
      sdk.writeText("sdk")
      val plugin = paths.pluginsDir.resolve("plugin.jar")
      plugin.writeText("plugin")
      val project = testDirectory.resolve("project").createDirectories().resolve("project.iml")
      project.writeText("project")

      assertSame(context, context.wipeSystemDir())

      assertFalse(paths.systemDir.exists())
      assertEquals("sdk", sdk.readText())
      assertEquals("plugin", plugin.readText())
      assertEquals("project", project.readText())
    }
  }

  @Test
  fun `cleanup on a missing directory does nothing and reports nothing`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      NioFiles.deleteRecursively(paths.systemDir)
      val ciServer = RecordingCIServer()

      withCIServer(ciServer) {
        repeat(2) {
          assertSame(context, context.wipeSystemDir())
          assertFalse(paths.systemDir.exists())
        }
      }

      assertTrue(ciServer.failures.isEmpty())
    }
  }

  @Test
  fun `cleanup preserves the system directory when requested`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths).apply { preserveSystemDir = true }
      val cache = paths.systemDir.resolve("cache.dat")
      cache.writeText("cache")

      assertSame(context, context.wipeSystemDir())

      assertEquals("cache", cache.readText())
    }
  }

  @Test
  fun `cleanup propagates deletion failures with the directory and cause`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      val failure = IOException("Cannot delete the cache")
      mockStatic(NioFiles::class.java).use { deletion ->
        deletion.`when`<Unit> { NioFiles.deleteRecursively(paths.systemDir) }.thenThrow(failure)

        val error = assertThrows<IOException> { context.wipeSystemDir(failOnWipeError = true) }

        assertSame(failure, error.cause)
        assertTrue(error.message.orEmpty().contains(paths.systemDir.toString()))
        assertTrue(error.message.orEmpty().contains(context.testName))
      }
    }
  }

  @Test
  fun `cleanup does not follow a symbolic link outside the system directory`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      val externalDirectory = testDirectory.resolve("external").createDirectories()
      val externalFile = externalDirectory.resolve("data.txt")
      externalFile.writeText("external")
      val link = paths.systemDir.resolve("external-link")
      try {
        Files.createSymbolicLink(link, externalDirectory)
      }
      catch (e: UnsupportedOperationException) {
        assumeTrue(false, "Symbolic links are unavailable: ${e.message}")
      }
      catch (e: IOException) {
        assumeTrue(false, "Cannot create a symbolic link: ${e.message}")
      }

      context.wipeSystemDir(failOnWipeError = true)

      assertFalse(link.exists())
      assertFalse(paths.systemDir.exists())
      assertEquals("external", externalFile.readText())
    }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `cleanup fails while a cache file denies deletion`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      val cache = paths.systemDir.resolve("locked.dat")
      cache.writeText("cache")
      FileChannel.open(cache, READ, ExtendedOpenOption.NOSHARE_DELETE).use {
        assertThrows<IOException> { context.wipeSystemDir(failOnWipeError = true) }
        assertTrue(cache.exists())
      }

      context.wipeSystemDir(failOnWipeError = true)

      assertFalse(paths.systemDir.exists())
    }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `cleanup reports a synthetic failure by default while a cache file denies deletion`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      val cache = paths.systemDir.resolve("locked.dat")
      cache.writeText("cache")
      val ciServer = RecordingCIServer()
      withCIServer(ciServer) {
        FileChannel.open(cache, READ, ExtendedOpenOption.NOSHARE_DELETE).use {
          assertSame(context, context.wipeSystemDir())
        }
      }

      assertTrue(cache.exists())
      val failure = ciServer.failures.single()
      assertEquals(SyntheticTestKind.TEST_INFRA_EXCEPTION, failure.kind)
      assertTrue(failure.testName.contains(paths.systemDir.toString()))
      assertTrue(failure.message.contains(context.testName))
      assertTrue(failure.details.contains(cache.toString()), "Expected the locked file, got: ${failure.details}")
      assertTrue(failure.details.contains("\tat "), "Expected a stack trace, got: ${failure.details}")
    }
  }

  @Test
  fun `cleanup reports nothing when it succeeds`() {
    IDEDataPaths(testDirectory, null).use { paths ->
      val context = createContext(paths)
      paths.systemDir.resolve("cache.dat").writeText("cache")
      val ciServer = RecordingCIServer()
      withCIServer(ciServer) {
        assertSame(context, context.wipeSystemDir())
      }

      assertFalse(paths.systemDir.exists())
      assertTrue(ciServer.failures.isEmpty())
    }
  }

  private fun withCIServer(ciServer: CIServer, action: () -> Unit) {
    val previousDi = di
    di = DI {
      extend(previousDi)
      bindSingleton<CIServer>(overrides = true) { ciServer }
    }
    try {
      action()
    }
    finally {
      di = previousDi
    }
  }

  private class RecordingCIServer : CIServer by NoCIServer {
    data class Failure(val testName: String, val message: String, val details: String, val kind: SyntheticTestKind)

    val failures: MutableList<Failure> = mutableListOf()

    override fun reportTestFailure(
      testName: String, message: String, details: String, linkToLogs: String?,
      kind: SyntheticTestKind, generifyTestName: Boolean, additionalMetadata: List<TestMetadata>,
    ) {
      failures.add(Failure(testName, message, details, kind))
    }
  }

  private fun createContext(paths: IDEDataPaths): IDETestContext = IDETestContext(
    paths = paths,
    ide = mock(InstalledIde::class.java),
    testCase = TestCase(IdeInfo.IdeaUltimate, NoProject),
    testName = "system-directory-cleanup",
    _resolvedProjectHome = null,
    publishers = emptyList(),
  )
}
