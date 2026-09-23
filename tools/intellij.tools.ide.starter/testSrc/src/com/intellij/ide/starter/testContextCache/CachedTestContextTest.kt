package com.intellij.ide.starter.testContextCache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class CachedTestContextTest {
  @Test
  fun `current test contexts reused from the same cached context share one system directory`() {
    withTestHomes("shared") { cachedTestHome, currentTestHomeA, currentTestHomeB ->
      val cachedContext = createDummyTestContext("CachedTestContextTest-cached-shared", cachedTestHome)
      val cachedTestContext = CachedTestContext(cachedContext)

      val currentA = cachedTestContext.createCurrentTestContext(currentTestHomeA.fileName.toString())
      val currentB = cachedTestContext.createCurrentTestContext(currentTestHomeB.fileName.toString())

      assertNotEquals(currentA.paths.configDir, currentB.paths.configDir)

      val markerFile = currentA.paths.systemDir.resolve("caches/marker.txt")
      markerFile.parent.createDirectories()
      markerFile.writeText("shared")

      assertEquals("shared", currentB.paths.systemDir.resolve("caches/marker.txt").readText())
      assertEquals(cachedContext.paths.systemDir, currentA.paths.systemDir)
      assertEquals(cachedContext.paths.systemDir, currentB.paths.systemDir)
      assertEquals(cachedContext.paths.systemDir, currentA.paths.sharedSystemDir)
    }
  }

  @Test
  fun `index directory built for the cached context is reused by every current test context`() {
    withTestHomes("index") { cachedTestHome, currentTestHomeA, currentTestHomeB ->
      val cachedContext = createDummyTestContext("CachedTestContextTest-cached-index", cachedTestHome)
      val cachedTestContext = CachedTestContext(cachedContext)

      // Simulates a stub/file-based index already built by a prior reusable IDE run against this cached project.
      val indexFile = cachedContext.paths.systemDir.resolve("index/stubs.dat")
      indexFile.parent.createDirectories()
      indexFile.writeText("built index")

      val currentA = cachedTestContext.createCurrentTestContext(currentTestHomeA.fileName.toString())
      val currentB = cachedTestContext.createCurrentTestContext(currentTestHomeB.fileName.toString())

      // No copy step is involved: both current test contexts see the index through the shared system directory.
      assertEquals("built index", currentA.paths.systemDir.resolve("index/stubs.dat").readText())
      assertEquals("built index", currentB.paths.systemDir.resolve("index/stubs.dat").readText())

      // Index files written later through one context are immediately visible through the other too.
      val laterIndexFile = currentA.paths.systemDir.resolve("index/names.dat")
      laterIndexFile.writeText("more index data")
      assertEquals("more index data", currentB.paths.systemDir.resolve("index/names.dat").readText())
    }
  }

  @Test
  fun `a system directory left in the current test home is not used`() {
    withTestHomes("leftover") { cachedTestHome, currentTestHomeA, _ ->
      // Simulates a current test context directory left over from a run that gave it a system directory of its own.
      val leftoverFile = (currentTestHomeA / "system" / "caches" / "stale.dat").apply {
        parent.createDirectories()
        writeText("stale")
      }

      val cachedContext = createDummyTestContext("CachedTestContextTest-cached-leftover", cachedTestHome)
      val cachedTestContext = CachedTestContext(cachedContext)

      val currentA = cachedTestContext.createCurrentTestContext(currentTestHomeA.fileName.toString())

      assertEquals(cachedContext.paths.systemDir, currentA.paths.systemDir)
      assertFalse(currentA.paths.systemDir.resolve("caches/stale.dat").exists(), "Found the leftover $leftoverFile")
    }
  }

  /** [com.intellij.ide.starter.ide.IDETestContext.wipeSystemDir] of a current context discards the shared state. */
  @Test
  fun `wipeSystemDir discards the shared contents`() {
    withTestHomes("wipe-system-dir") { cachedTestHome, currentTestHomeA, _ ->
      val cachedContext = createDummyTestContext("CachedTestContextTest-cached-wipe-system-dir", cachedTestHome)
      val cachedTestContext = CachedTestContext(cachedContext)
      val currentContext = cachedTestContext.createCurrentTestContext(currentTestHomeA.fileName.toString())
      val sharedIndexFile = cachedContext.paths.systemDir.resolve("index/stubs.dat")
      currentContext.paths.systemDir.resolve("index").createDirectories().resolve("stubs.dat").writeText("built index")

      currentContext.wipeSystemDir()

      assertFalse(sharedIndexFile.exists(), "wipeSystemDir must discard the shared state")
    }
  }
}

private fun withTestHomes(testCaseId: String, block: (cachedTestHome: Path, currentTestHomeA: Path, currentTestHomeB: Path) -> Unit) {
  withTestHomes(ownerName = "CachedTestContextTest", testCaseId = testCaseId, block = block)
}
