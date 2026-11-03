// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.sdk.backend.PythonInterpreterFacts
import com.intellij.python.sdk.backend.factRootPaths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The root paths that [factRootPaths] gives for the facts of an interpreter. The split into class roots and the source
 * roots of a module is up to the caller, which `PySdkPathsTest` covers.
 */
class PythonInterpreterRootsDerivationTest {
  private val project = Path.of("/work/project")
  private val venv = project.resolve(".venv")
  private val sitePackages = venv.resolve("lib/python3.12/site-packages")
  private val stdlib = Path.of("/usr/lib/python3.12")

  @Test
  fun `the paths of sys path are roots in their order`() {
    assertThat(factRootPaths(facts(stdlib, sitePackages), venv)).containsExactly(stdlib, sitePackages)
  }

  @Test
  fun `an excluded path, the environment home and egg info are no roots`() {
    val eggInfo = sitePackages.resolve("pkg.egg-info")
    val facts = facts(stdlib, venv, eggInfo, sitePackages).copy(excludedPaths = setOf(stdlib))

    assertThat(factRootPaths(facts, venv)).containsExactly(sitePackages)
  }

  @Test
  fun `an added path follows sys path and a path is a root once`() {
    val added = Path.of("/opt/extra")
    val facts = facts(stdlib, stdlib).copy(addedPaths = listOf(added, stdlib))

    assertThat(factRootPaths(facts, venv)).containsExactly(stdlib, added)
  }

  @Test
  fun `without an environment home every path is a root`() {
    assertThat(factRootPaths(facts(stdlib, venv), environmentHome = null)).containsExactly(stdlib, venv)
  }

  private fun facts(vararg sysPath: Path) = PythonInterpreterFacts(version = "Python 3.12.4", sysPath = sysPath.toList())
}
