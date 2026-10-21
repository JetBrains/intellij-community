package com.intellij.ide.starter.testContextCache

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

class TestContextCacheTest {
  @Test
  fun `same configuration instance and discriminator reuse cached context`(@TempDir tempDir: Path) {
    val configuration = cachedProjectConfiguration(tempDir.resolve("project"))
    val cachedContext = TestContextCache.getOrCreate(
      configuration = configuration,
      contextDiscriminator = "same",
    ) { contextName, _ ->
      createDummyTestContext(contextName, tempDir.resolve(contextName))
    }

    val reusedContext = TestContextCache.getOrCreate(
      configuration = configuration,
      contextDiscriminator = "same",
    ) { _, _ ->
      error("Cached context should be reused")
    }

    assertSame(cachedContext, reusedContext)
  }

  /** One configuration in the monolith and the split mode has two entries over one project home. */
  @Test
  fun `a current context belongs to the entry whose system directory it shares`(@TempDir tempDir: Path) {
    val projectHome = tempDir.resolve("project").createDirectories()
    val cachedContext = createDummyTestContext("cached", tempDir.resolve("cached"), projectHome)
    val otherCachedContext = createDummyTestContext("other-cached", tempDir.resolve("other-cached"), projectHome)
    val currentContext = createDummyTestContext(
      "current", tempDir.resolve("current"), projectHome, sharedSystemDir = cachedContext.paths.systemDir,
    )

    assertTrue(CachedTestContext(cachedContext).isSourceOf(currentContext))
    assertFalse(CachedTestContext(otherCachedContext).isSourceOf(currentContext))
  }
}
