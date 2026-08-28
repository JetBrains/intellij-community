// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.sdk.backend.evolution.ownedEnvBinaryIn
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Guards the gate that decides which environments may be rebuilt.
 *
 * The rule is load-bearing rather than cosmetic: it is the only thing standing between the rebuild affordance and an
 * environment that is not the project's to destroy. Every case here is one a wrong answer would destroy something.
 */
class PyEvoRecreateGateTest {
  private val baseDir: Path = Path.of("/home/me/project")

  private fun detected(path: String): PyInterpreterRef = PyInterpreterRef.DetectedPath(path)

  @Test
  fun `an environment inside the project is the project's own`() {
    val binary = "/home/me/project/.venv/bin/python"
    assertEquals(Path.of(binary), detected(binary).ownedEnvBinaryIn(baseDir))
  }

  @Test
  fun `an environment outside the project is not`() {
    // A system interpreter, a pyenv install, a named conda env and a poetry cache env all land here. Another project
    // may be using any of them, so none is ours to delete.
    assertNull(detected("/usr/bin/python3").ownedEnvBinaryIn(baseDir))
    assertNull(detected("/home/me/.pyenv/versions/3.13/bin/python").ownedEnvBinaryIn(baseDir))
    assertNull(detected("/home/me/.conda/envs/project/bin/python").ownedEnvBinaryIn(baseDir))
  }

  @Test
  fun `a sibling directory whose name starts with the project's is not inside it`() {
    // Guards the reading of "inside": as plain text, `/home/me/project2` starts with `/home/me/project`.
    assertNull(detected("/home/me/project2/.venv/bin/python").ownedEnvBinaryIn(baseDir))
  }

  @Test
  fun `a path escaping the project through its parent is not inside it`() {
    assertNull(detected("/home/me/project/../other/.venv/bin/python").ownedEnvBinaryIn(baseDir))
  }

  @Test
  fun `the project directory itself is not an environment`() {
    assertNull(detected(baseDir.toString()).ownedEnvBinaryIn(baseDir))
  }

  @Test
  fun `only an environment that exists has anything to destroy`() {
    assertNull(PyInterpreterRef.CreateEnv("3.13").ownedEnvBinaryIn(baseDir))
    assertNull(PyInterpreterRef.ExistingSdk("Python 3.13").ownedEnvBinaryIn(baseDir))
    assertNull(PyInterpreterRef.Autoconfigure("Venv").ownedEnvBinaryIn(baseDir))
    assertNull(null.ownedEnvBinaryIn(baseDir))
  }
}
