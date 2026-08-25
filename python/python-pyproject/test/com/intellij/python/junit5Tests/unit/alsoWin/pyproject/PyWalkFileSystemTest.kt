package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.io.createDirectories
import com.jetbrains.python.Result
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.DEFAULT_VIRTUALENV_DIRNAME
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentest4j.AssertionFailedError
import java.nio.file.Path

internal class PyWalkFileSystemTest {
  companion object {
    @TempDir
    lateinit var root: Path

    private lateinit var excludedDir: Path
    private lateinit var expectedTomlFiles: Array<Path>

    @BeforeAll
    @JvmStatic
    fun createStructure() {
      excludedDir = root.resolve("excluded").createDirectory()
      expectedTomlFiles = arrayOf(
        root.resolve(PY_PROJECT_TOML).createFile(),
        root.resolve("dir").createDirectory().resolve(PY_PROJECT_TOML).createFile(),
        excludedDir.resolve(PY_PROJECT_TOML).createFile(),
      )

      // None of the following may be reported: each of these directories is pruned before the walk descends into it.
      // Dot directories.
      root.resolve(".abc").createDirectories().resolve(PY_PROJECT_TOML).createFile()
      root.resolve(DEFAULT_VIRTUALENV_DIRNAME).createDirectory().resolve(PY_PROJECT_TOML).createFile()
      // A venv whose name has no dot: recognized by python in it
      root.resolve("venv").createDirectory().apply {
        resolve("Scripts").createDirectories().resolve("python.exe").createFile()
        resolve("bin").createDirectories().resolve("python").createFile()
        resolve(PY_PROJECT_TOML).createFile()
      }
      // Well-known heavy directories, pruned by name.
      root.resolve("node_modules").createDirectory().resolve(PY_PROJECT_TOML).createFile()
      root.resolve("lib").resolve("site-packages").createDirectories().resolve(PY_PROJECT_TOML).createFile()
    }
  }

  @Test
  fun testSunnyDay(): Unit = timeoutRunBlocking {
    val files = walkFileSystemNoTomlContent(setOf(root)).orThrow().rawTomlFiles
    assertThat("Wrong files", files, Matchers.containsInAnyOrder(*expectedTomlFiles))
  }

  @Test
  fun testExcludedPaths(): Unit = timeoutRunBlocking {
    val files = walkFileSystemNoTomlContent(setOf(root), excludedPaths = setOf(excludedDir)).orThrow().rawTomlFiles
    val expected = expectedTomlFiles.filterNot { it.startsWith(excludedDir) }.toTypedArray()
    assertThat("pyproject.toml inside an excluded folder must not be reported", files, Matchers.containsInAnyOrder(*expected))
  }

  @Test
  fun testRainyDay(): Unit = timeoutRunBlocking {
    when (val r = walkFileSystemNoTomlContent(setOf(root.resolve("foo")))) {
      is Result.Failure -> Unit
      is Result.Success -> throw AssertionFailedError("Fake dir has files: ${r.result}")
    }
  }
}
