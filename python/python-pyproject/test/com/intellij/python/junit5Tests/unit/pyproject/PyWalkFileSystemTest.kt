package com.intellij.python.junit5Tests.unit.pyproject

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

class PyWalkFileSystemTest {
  companion object {
    @TempDir
    lateinit var root: Path

    private lateinit var expectedExcludedDir: Path
    private lateinit var expectedTomlFiles: Array<Path>

    @BeforeAll
    @JvmStatic
    fun createStructure() {
      expectedTomlFiles = arrayOf(
        root.resolve(PY_PROJECT_TOML).createFile(),
        root.resolve("dir").createDirectory().resolve(PY_PROJECT_TOML).createFile()
      )
      root.resolve(".abc").createDirectories().resolve(PY_PROJECT_TOML).createFile()
      expectedExcludedDir = root.resolve(DEFAULT_VIRTUALENV_DIRNAME).createDirectory()
    }
  }

  @Test
  fun testSunnyDay(): Unit = timeoutRunBlocking {
    val files = walkFileSystemNoTomlContent(root).orThrow().rawTomlFiles
    assertThat("Wrong files", files, Matchers.containsInAnyOrder(*expectedTomlFiles))
  }

  @Test
  fun testRainyDay(): Unit = timeoutRunBlocking {
    when (val r = walkFileSystemNoTomlContent(root.resolve("foo"))) {
      is Result.Failure -> Unit
      is Result.Success -> throw AssertionFailedError("Fake dir has files: ${r.result}")
    }
  }
}
