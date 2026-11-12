// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.sdk.backend.evolution.ownedEnvDirIn
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

  private fun owned(dir: String?): Path? = ownedEnvDirIn(dir?.let { Path.of(it) }, baseDir)

  @Test
  fun `an environment inside the project is the project's own`() {
    val dir = "/home/me/project/.venv"
    assertEquals(Path.of(dir), owned(dir))
  }

  @Test
  fun `an environment outside the project is not`() {
    // A system interpreter, a pyenv install, a named conda env and a poetry cache env all land here. Another project
    // may be using any of them, so none is ours to delete.
    assertNull(owned("/usr"))
    assertNull(owned("/home/me/.pyenv/versions/3.13"))
    assertNull(owned("/home/me/.conda/envs/project"))
  }

  @Test
  fun `a sibling directory whose name starts with the project's is not inside it`() {
    // Guards the reading of "inside": as plain text, `/home/me/project2` starts with `/home/me/project`.
    assertNull(owned("/home/me/project2/.venv"))
  }

  @Test
  fun `a path escaping the project through its parent is not inside it`() {
    assertNull(owned("/home/me/project/../other/.venv"))
  }

  @Test
  fun `the project directory itself is not an environment`() {
    assertNull(owned(baseDir.toString()))
  }

  @Test
  fun `an environment whose directory is unknown has nothing to destroy`() {
    assertNull(owned(null))
  }
}
