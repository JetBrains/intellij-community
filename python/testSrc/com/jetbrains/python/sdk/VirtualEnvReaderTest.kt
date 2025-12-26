// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.grazie.grammar.assertIsEmpty
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.deleteRecursively
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.venvReader.tryResolvePath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

@TestApplication
class VirtualEnvReaderTest {
  inner class Bootstrap {
    val PYENV_ROOT = "PYENV_ROOT"

    val cwd = FileUtilRt.createTempDirectory("venvreader", "").toPath()
    val pyenv = cwd.resolve(".pyenv")

    val env = HashMap<String, String>()
    val virtualEnvReader = VirtualEnvReader(env)

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
    val binary = if (SystemInfoRt.isWindows) "python.exe" else "python"
    bootstrap.setupPyenv(listOf("3.1.1"), binary)
    var interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    Assertions.assertEquals(1, interpreters.size)
    assert(interpreters[0].absolutePathString().startsWith(bootstrap.pyenv.absolutePathString()))
    assert(interpreters[0].absolutePathString().endsWith(binary))

    // another version w/o match
    bootstrap.setupPyenv(listOf("3.2.1"), "xxx")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    Assertions.assertEquals(1, interpreters.size)

    // both in names
    val pypyBinary = if (SystemInfoRt.isWindows) "pypy.exe" else "pypy"
    bootstrap.setupPyenv(listOf("3.2.2"), pypyBinary)
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    Assertions.assertEquals(2, interpreters.size)
    assert(interpreters[0] != interpreters[1])

    bootstrap.removeVersion(bootstrap.pyenv, "3.2.2")
    interpreters = bootstrap.virtualEnvReader.findPyenvInterpreters()
    Assertions.assertEquals(1, interpreters.size)
    assert(interpreters[0].absolutePathString().endsWith(binary))
  }

  @Test
  fun testIsPyenvSdk() {
    val bootstrap = Bootstrap()

    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(null as String?))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(""))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("aa\u0000bb"))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("a/b/c/d"))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd.resolve("smthg")))

    bootstrap.setupPyenv(listOf("3.2.1"), "xxxx")

    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(null as String?))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(""))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("aa\u0000bb"))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk("a/b/c/d"))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd))
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.cwd.resolve("smthg")))

    // particularly any path inside pyenv root will work
    Assertions.assertTrue(bootstrap.virtualEnvReader.isPyenvSdk(bootstrap.pyenv.resolve("xxx")))

    // should resolve symlinks
    val link = bootstrap.cwd.resolve("smthg")
    val target = bootstrap.pyenv.resolve("xxx")

    // hanging links, should not resolve it
    if (!SystemInfoRt.isWindows) {
      // links require UAC on Windows
      Files.createSymbolicLink(link, target)
    }
    Assertions.assertFalse(bootstrap.virtualEnvReader.isPyenvSdk(link))

    if (!SystemInfoRt.isWindows) {
      Files.createFile(target)
      Assertions.assertTrue(bootstrap.virtualEnvReader.isPyenvSdk(link))
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getVenvRootPathTestCases")
  fun getVenvRootPathTests(name: String, isWindows: Boolean, path: Path, expectedReturnValue: Path?) {
    val result = VirtualEnvReader(isWindows = isWindows).getVenvRootPath(path)

    Assertions.assertEquals(expectedReturnValue, result)
  }

  companion object {
    @JvmStatic
    fun getVenvRootPathTestCases(): List<Arguments> = listOf(
      GetVenvRootPathTestCase(
        "returns null when no parent is found",
        false,
        "python",
        null
      ),

      GetVenvRootPathTestCase(
        "returns null when bin dir is named Scripts on non-windows",
        false,
        "root/.venv/Scripts/python",
        null
      ),

      GetVenvRootPathTestCase(
        "returns null when bin dir is named bin on windows",
        true,
        "root/.venv/bin/python.exe",
        null
      ),

      GetVenvRootPathTestCase(
        "returns root when bin dir is named Scripts on windows",
        true,
        "root/.venv/Scripts/python.exe",
        "root"
      ),

      GetVenvRootPathTestCase(
        "returns root when bin dir is named bin on non-windows",
        false,
        "root/.venv/bin/python.exe",
        "root"
      ),

      GetVenvRootPathTestCase(
        "returns null when bin dir has no parent",
        false,
        "bin/python.exe",
        null
      ),

      GetVenvRootPathTestCase(
        "returns null when .venv dir has no parent",
        false,
        ".venv/bin/python.exe",
        null
      ),

      GetVenvRootPathTestCase(
        "returns root with a custom .venv dir name",
        false,
        "root/.venv_custom/bin/python.exe",
        "root"
      ),
    ).map {
      Arguments.of(it.name, it.isWindows, Path.of(it.path), tryResolvePath(it.expectedReturnValue))
    }

    data class GetVenvRootPathTestCase(
      val name: String,
      val isWindows: Boolean,
      val path: String,
      val expectedReturnValue: String?,
    )
  }
}