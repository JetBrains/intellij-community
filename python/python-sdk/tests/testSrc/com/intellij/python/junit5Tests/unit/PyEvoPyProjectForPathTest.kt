// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.sdk.common.evolution.EvoPyProjectDto
import com.intellij.python.sdk.frontend.evolution.pyProjectForPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Guards `pyProjectForPath`, the rule the interpreter widget resolves "open file -> `PyProject`" by where there is no
 * module model to ask. Until PY-94235 the rule lived inline in the widget and no test could reach it.
 */
class PyEvoPyProjectForPathTest {
  private fun dto(key: String, vararg contentRoots: String, isMain: Boolean = false): EvoPyProjectDto =
    EvoPyProjectDto(key = key, name = key.substringAfterLast('/'), isMain = isMain, contentRoots = contentRoots.toList())

  /** The monorepo of the report: a root that is no Python project, a uv member, and a member of that member. */
  private val root = dto("/home/me/mono", "/home/me/mono", isMain = true)
  private val uv = dto("/home/me/mono/uv", "/home/me/mono/uv")
  private val subuv = dto("/home/me/mono/uv/subuv1", "/home/me/mono/uv/subuv1")
  private val all = listOf(root, uv, subuv)

  @Test
  @DisplayName("a file in a member resolves to that member, not to the project root")
  fun fileInMember() {
    assertEquals(uv, pyProjectForPath(all, "/home/me/mono/uv/main.py"))
  }

  @Test
  @DisplayName("a nested project wins over the one it sits in: the longest root matches")
  fun nestedWins() {
    assertEquals(subuv, pyProjectForPath(all, "/home/me/mono/uv/subuv1/src/app.py"))
  }

  @Test
  @DisplayName("a file at a content root resolves to its project")
  fun fileAtRoot() {
    assertEquals(uv, pyProjectForPath(all, "/home/me/mono/uv"))
  }

  @Test
  @DisplayName("a path order independent of the order the backend sent the projects in")
  fun orderIndependent() {
    assertEquals(subuv, pyProjectForPath(all.reversed(), "/home/me/mono/uv/subuv1/src/app.py"))
  }

  @Test
  @DisplayName("matching is on whole segments: a sibling whose name starts with a root's name is not held by it")
  fun segmentBoundary() {
    assertEquals(root, pyProjectForPath(all, "/home/me/mono/uvtools/main.py"))
  }

  @Test
  @DisplayName("a second content root of a module answers for that module too")
  fun secondContentRoot() {
    val withTests = dto("/home/me/mono/uv", "/home/me/mono/uv", "/home/me/tests")
    assertEquals(withTests, pyProjectForPath(listOf(root, withTests), "/home/me/tests/test_app.py"))
  }

  @Test
  @DisplayName("a backend that states no content roots still answers by its key")
  fun keyAloneMatches() {
    val keyOnly = dto("/home/me/mono/uv")
    assertEquals(keyOnly, pyProjectForPath(listOf(keyOnly), "/home/me/mono/uv/main.py"))
  }

  @Test
  @DisplayName("a file no project holds is nobody's — the caller is the one that falls back to the main project")
  fun outsideEveryRoot() {
    assertNull(pyProjectForPath(all, "/home/me/elsewhere/scratch.py"))
  }
}
