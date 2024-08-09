package com.jetbrains.python.sdk.flavors

import com.intellij.grazie.grammar.assertIsEmpty
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.utils.io.deleteRecursively
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class VirtualEnvReaderTest {
  inner class Bootstrap {
    val PYENV_ROOT = "PYENV_ROOT"

    val cwd = FileUtilRt.createTempDirectory("venvreader", "").toPath()
    val pyenv = cwd.resolve(".pyenv")

    val env = HashMap<String, String>()
    val virtualEnvReader = VirtualEnvReader(env, isWindows = false)

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

  @Test
  fun testHandleEmptyDirs() {
    val bootstrap = Bootstrap()

    // non existent dir
    var interpreters = bootstrap.virtualEnvReader.findLocalInterpreters(bootstrap.pyenv)
    assertIsEmpty(interpreters)

    // invalid data
    bootstrap.env[bootstrap.PYENV_ROOT] = "aa\u0000bb"
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertIsEmpty(interpreters)

    // empty dir
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertIsEmpty(interpreters)

    // empty dir
    bootstrap.setupPyenv(listOf(), "")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertIsEmpty(interpreters)

    // .pyenv but no versions
    Files.createDirectory(bootstrap.pyenv)
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertIsEmpty(interpreters)
  }

  @Test
  fun testCollectPaths() {
    val bootstrap = Bootstrap()

    // just version
    bootstrap.setupPyenv(listOf("3.1.1"), "python")
    var interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertEquals(1, interpreters.size)
    assert(interpreters[0].absolutePathString().startsWith(bootstrap.pyenv.absolutePathString()))
    assert(interpreters[0].absolutePathString().endsWith("python"))

    // another version w/o match
    bootstrap.setupPyenv(listOf("3.2.1"), "xxx")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertEquals(1, interpreters.size)

    // both in names
    bootstrap.setupPyenv(listOf("3.2.2"), "pypy")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertEquals(2, interpreters.size)
    assert(interpreters[0] != interpreters[1])

    bootstrap.removeVersion(bootstrap.pyenv, "3.2.2")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    assertEquals(1, interpreters.size)
    assert(interpreters[0].absolutePathString().endsWith("python"))
  }

  @Test
  fun testIsPyenvSdk() {
    val bootstrap = Bootstrap()

    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(null as String?))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(""))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("aa\u0000bb"))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("a/b/c/d"))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd.resolve("smthg")))

    bootstrap.setupPyenv(listOf("3.2.1"), "xxxx")

    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(null as String?))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(""))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("aa\u0000bb"))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("a/b/c/d"))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd))
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd.resolve("smthg")))

    // particularly any path inside pyenv root will work
    assertTrue(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.pyenv.resolve("xxx")))

    // should resolve symlinks
    val link = bootstrap.cwd.resolve("smthg")
    val target = bootstrap.pyenv.resolve("xxx")

    // hanging links, should not resolve it
    Files.createSymbolicLink(link, target)
    assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(link))

    Files.createFile(target)
    assertTrue(bootstrap.virtualEnvReader.isPyenvSdk(link))
  }
}