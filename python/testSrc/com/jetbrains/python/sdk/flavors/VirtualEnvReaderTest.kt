package com.jetbrains.python.sdk.flavors

import com.intellij.grazie.grammar.assertIsEmpty
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.utils.io.deleteRecursively
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class VirtualEnvReaderTest {
  inner class Bootstrap {
    val PYENV_ROOT = "PYENV_ROOT"

    val cwd = FileUtilRt.createTempDirectory("venvreader", "").toPath()
    val pyenv = cwd.resolve(".pyenv")

    val env = HashMap<String, String>();
    val virtualEnvReader = VirtualEnvReader { envVar ->
      env[envVar] ?: String()
    }

    fun setupPyenv(versions: List<String>, binary: String) {
      for (version in versions) {
        addVersion(pyenv, version, binary)
      }

      env[PYENV_ROOT] = pyenv.absolutePathString()
    }

    fun addVersion(root: Path, version: String, binary: String) {
      val vsdir = ensureVersions(root)

      val vdir = vsdir.resolve(version)
      Files.createDirectory(vdir)

      if (binary.isNotEmpty()) {
        Files.createFile(vdir.resolve(binary))
      }
    }

    fun removeVersion(root: Path, version: String) {
      val vsdir = ensureVersions(root)
      val vdir = vsdir.resolve(version)

      vdir.deleteRecursively()
    }

    fun ensureVersions(root: Path): Path {
      if (!root.exists()) {
        Files.createDirectory(root);
      }

      val versions = root.resolve("versions")
      if (!versions.exists()) {
        Files.createDirectory(versions)
      }

      return versions
    }
  }

  val EMPTY_PATTERN = Pattern.compile("")

  @Test
  fun testHandleEmptyDirs() {
    val bootstrap = Bootstrap()

    // non existent dir
    var interpreters = bootstrap.virtualEnvReader.findLocalInterpreters(bootstrap.pyenv, setOf("python"), EMPTY_PATTERN)
    assertIsEmpty(interpreters)

    // invalid data
    bootstrap.env[bootstrap.PYENV_ROOT] = "aa\u0000bb"
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), EMPTY_PATTERN)
    assertIsEmpty(interpreters)

    // empty dir
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), EMPTY_PATTERN)
    assertIsEmpty(interpreters)

    // empty dir
    bootstrap.setupPyenv(listOf(), "")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), EMPTY_PATTERN)
    assertIsEmpty(interpreters)

    // .pyenv but no versions
    Files.createDirectory(bootstrap.pyenv)
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), EMPTY_PATTERN)
    assertIsEmpty(interpreters)
  }

  @Test
  fun testCollectPaths() {
    val bootstrap = Bootstrap()

    // just version
    bootstrap.setupPyenv(listOf("3.1.1"), "python")
    var interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), EMPTY_PATTERN)
    assertEquals(1, interpreters.size)
    assert(interpreters[0].absolutePathString().startsWith(bootstrap.pyenv.absolutePathString()))
    assert(interpreters[0].absolutePathString().endsWith("python"))

    // another version w/o match
    bootstrap.addVersion(bootstrap.pyenv, "3.2.1", "xxxx")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), EMPTY_PATTERN)
    assertEquals(1, interpreters.size)

    // both in names
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python", "xxxx"), EMPTY_PATTERN)
    assertEquals(2, interpreters.size)
    assert(interpreters[0] != interpreters[1])

    // name + pattern
    var pattern = Pattern.compile("xxxx")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), pattern)
    assertEquals(2, interpreters.size)
    assert(interpreters[0] != interpreters[1])

    pattern = Pattern.compile("python")
    bootstrap.removeVersion(bootstrap.pyenv, "3.2.1")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters(setOf("python"), pattern)
    assertEquals(1, interpreters.size)
    assert(interpreters[0].absolutePathString().endsWith("python"))
  }
}