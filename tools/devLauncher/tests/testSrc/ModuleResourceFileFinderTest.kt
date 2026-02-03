package com.intellij.tools.devLauncher

import com.intellij.openapi.application.ex.PathManagerEx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

class ModuleResourceFileFinderTest {
  private lateinit var projectDir: Path
  private lateinit var finder: ModuleResourceFileFinder

  @BeforeEach
  fun setUp() {
    projectDir = Path(PathManagerEx.getCommunityHomePath()).resolve("tools/devLauncher/tests/testData/moduleResourceFinderProject")
    finder = ModuleResourceFileFinder(projectDir)
  }

  @Test
  fun `simple roots`() {
    assertPath("simple/resources/a.txt", finder.findResourceFile("simple", "a.txt"))
    assertPath("simple/src/b/b.txt", finder.findResourceFile("simple", "b/b.txt"))
    assertPath(null, finder.findResourceFile("simple", "c.txt"))
  }
  
  @Test
  fun `roots with prefixes`() {
    assertPath("withPrefix/resources/a.txt", finder.findResourceFile("withPrefix", "prefix1/a.txt"))
    assertPath("withPrefix/src/b/b.txt", finder.findResourceFile("withPrefix", "prefix2/b/b.txt"))
    assertPath("additional-resources/c.txt", finder.findResourceFile("withPrefix", "prefix3/c.txt"))
  }

  private fun assertPath(expectedPath: String?, file: Path?) {
    assertEquals(expectedPath, file?.relativeTo(projectDir)?.invariantSeparatorsPathString)
  }
}