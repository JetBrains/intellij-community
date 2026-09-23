package com.intellij.ide.starter.testContextCache

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

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
}
