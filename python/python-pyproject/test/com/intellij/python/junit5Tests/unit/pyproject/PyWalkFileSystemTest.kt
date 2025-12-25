package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.pyProjectToml.walkFileSystemNoTomlContent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.jetbrains.python.Result
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
      expectedExcludedDir = root.resolve(".foo").createDirectory()
    }
  }

  @Test
  fun testSunnyDay(): Unit = timeoutRunBlocking {
    val (files, exclude) = walkFileSystemNoTomlContent(root).orThrow()
    assertThat("Wrong dirs excluded", exclude, Matchers.containsInAnyOrder(expectedExcludedDir))
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
