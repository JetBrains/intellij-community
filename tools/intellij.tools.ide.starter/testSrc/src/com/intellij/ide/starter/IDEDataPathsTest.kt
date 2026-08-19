package com.intellij.ide.starter

import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.path.IDEDataPaths
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class IDEDataPathsTest {
  @TempDir
  lateinit var testDirectory: Path

  @Test
  fun `an IDE reports under its own test home`() {
    val testHome = testDirectory.resolve("maven-smoke-tests")

    IDEDataPaths(testHome = testHome, inMemoryRoot = null).use { paths ->
      paths.reportingRoot shouldBe testHome
    }
  }

  /** A frontend keeps its data apart from the backend's, but reports beside it, so that the artifacts of one launch stay together. */
  @Test
  fun `a frontend reports under the test home it shares with the backend`() {
    val testHome = testDirectory.resolve("maven-smoke-tests")

    FrontendIDEDataPaths(testHome = testHome.resolve(FrontendIDEDataPaths.FRONTEND_DIR_NAME), inMemoryRoot = null).use { paths ->
      paths.reportingRoot shouldBe testHome
    }
  }
}
