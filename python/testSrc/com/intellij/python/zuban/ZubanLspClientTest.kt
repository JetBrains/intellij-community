// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.zuban

import com.intellij.idea.TestFor
import com.intellij.python.zuban.common.ZubanTypeCheckingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestFor(issues = ["PY-85009"])
class ZubanLspClientTest {
  @Nested
  inner class InitializationOptions {
    @Test
    fun `the options name the interpreter`() {
      assertEquals(mapOf("pythonExecutable" to "/p/.venv/bin/python"),
                   zubanInitializationOptions("/p/.venv/bin/python", ZubanTypeCheckingMode.AUTO))
    }

    @Test
    fun `the options are empty without a local interpreter`() {
      assertEquals(emptyMap<String, Any>(), zubanInitializationOptions(null, ZubanTypeCheckingMode.AUTO))
    }

    @Test
    fun `a chosen mode goes out`() {
      assertEquals(mapOf("pythonExecutable" to "/p/.venv/bin/python", "typeCheckingMode" to "mypy"),
                   zubanInitializationOptions("/p/.venv/bin/python", ZubanTypeCheckingMode.MYPY))
      assertEquals(mapOf("typeCheckingMode" to "default"), zubanInitializationOptions(null, ZubanTypeCheckingMode.DEFAULT))
    }

    @Test
    fun `every mode round-trips through its value`() {
      for (mode in ZubanTypeCheckingMode.entries) assertEquals(mode, ZubanTypeCheckingMode.of(mode.value))
      assertNull(ZubanTypeCheckingMode.of("off"))
      assertNull(ZubanTypeCheckingMode.of(null))
    }
  }

  @Nested
  inner class PythonExecutable {
    @Test
    fun `a unix interpreter goes out unchanged`() {
      assertEquals(Path.of("/p/.venv/bin/python").toString(), zubanPythonExecutable(Path.of("/p/.venv/bin/python"), isWindows = false))
    }

    @Test
    fun `a windows virtual environment goes out unchanged`() {
      val interpreter = Path.of("p", ".venv", "Scripts", "python.exe")
      assertEquals(interpreter.toString(), zubanPythonExecutable(interpreter, isWindows = true))
    }

    @Test
    fun `a windows interpreter in its prefix goes out below Scripts`() {
      // A conda environment or a base interpreter keeps `python.exe` in the prefix, and zuban takes the
      // parent of the directory of the executable as the prefix.
      val interpreter = Path.of("conda", "envs", "py312", "python.exe")
      assertEquals(Path.of("conda", "envs", "py312", "Scripts", "python.exe").toString(), zubanPythonExecutable(interpreter, isWindows = true))
    }
  }

  @Nested
  inner class MypyPath {
    private val root = Path.of("/project")

    @Test
    fun `nothing goes out without source roots`() {
      assertNull(zubanMypyPath(root, emptyList(), inherited = null))
    }

    @Test
    fun `source roots go out relative to the first root`() {
      val sourceRoots = listOf(root.resolve("lib"), root.resolve("tools/scripts"))
      assertEquals("lib:tools/scripts", zubanMypyPath(root, sourceRoots, inherited = null))
    }

    @Test
    fun `a source root outside the first root goes out with parent steps`() {
      assertEquals("../shared/src", zubanMypyPath(root, listOf(Path.of("/shared/src")), inherited = null))
    }

    @Test
    fun `the first root itself is left out`() {
      assertNull(zubanMypyPath(root, listOf(root), inherited = null))
    }

    @Test
    fun `a root with a colon is left out`() {
      assertEquals("lib", zubanMypyPath(root, listOf(root.resolve("a:b"), root.resolve("lib")), inherited = null))
    }

    @Test
    fun `a duplicate root goes out once`() {
      assertEquals("lib", zubanMypyPath(root, listOf(root.resolve("lib"), root.resolve("lib/.")), inherited = null))
    }

    @Test
    fun `the inherited value comes after the source roots`() {
      assertEquals("lib:/opt/stubs", zubanMypyPath(root, listOf(root.resolve("lib")), inherited = "/opt/stubs"))
    }

    @Test
    fun `the inherited value goes out alone without source roots`() {
      assertEquals("/opt/stubs", zubanMypyPath(root, emptyList(), inherited = "/opt/stubs"))
    }

    @Test
    fun `a blank inherited value is left out`() {
      assertNull(zubanMypyPath(root, emptyList(), inherited = " "))
    }
  }
}
