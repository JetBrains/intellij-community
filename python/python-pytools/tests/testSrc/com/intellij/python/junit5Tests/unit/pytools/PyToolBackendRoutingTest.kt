// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.intellij.python.pytools.backend.GenericPyToolManager
import com.intellij.python.pytools.backend.InstalledInfo
import com.intellij.python.pytools.backend.PyTool
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Which backend answers for a tool.
 *
 * A machine's backends do not divide by machine: uv manages the tools uv installed, while uv itself and a tool that
 * pip placed belong to the pip backend. Letting the highest-priority backend answer for everything is what made an
 * upgrade of uv install a second uv in uv's own bin directory instead of updating the one on `PATH`, and what left
 * Pipenv with no upgrade at all.
 */
internal class PyToolBackendRoutingTest {
  private class FakeTool(name: String) : PyTool {
    override val packageName: PyPackageName = PyPackageName.from(name)
  }

  /** Manages exactly the tools it was given, and records the tool list it was asked about. */
  private class FakeBackend(private val managed: Set<PyTool>) : GenericPyToolManager {
    val asked: MutableList<Collection<PyTool>> = mutableListOf()

    override suspend fun install(tool: PyTool): PyResult<Path> = error("not called")
    override suspend fun upgrade(tool: PyTool): PyResult<Path> = error("not called")

    override suspend fun list(tools: Collection<PyTool>): Map<PyTool, InstalledInfo> {
      asked.add(tools.toList())
      return tools.filter { it in managed }.associateWith {
        InstalledInfo(path = Path.of("/fake", it.packageName.name), installedVersion = "1.0", latestVersion = "2.0")
      }
    }
  }

  private val hatch = FakeTool("hatch")
  private val poetry = FakeTool("poetry")
  private val uv = FakeTool("uv")

  /** Narrowing across backends: each answers for what it manages, and the result is the union. */
  @Test
  fun `each backend answers for the tools it manages`() = timeoutRunBlocking {
    val uvBackend = FakeBackend(setOf(hatch, poetry))
    val pipBackend = FakeBackend(setOf(uv))
    val listed = narrow(listOf(uvBackend, pipBackend), listOf(hatch, poetry, uv))
    assertEquals(setOf(hatch, poetry, uv), listed.keys)
  }

  /**
   * The point of narrowing: the pip backend reaches PyPI per tool, so it must be asked only about what uv disowned —
   * uv itself and a tool pip placed — never about uv's own tools.
   */
  @Test
  fun `a later backend is asked only about what the earlier one left`() = timeoutRunBlocking {
    val uvBackend = FakeBackend(setOf(hatch, poetry))
    val pipBackend = FakeBackend(setOf(uv))
    narrow(listOf(uvBackend, pipBackend), listOf(hatch, poetry, uv))
    assertEquals(listOf(listOf(uv)), pipBackend.asked)
  }

  /** Nothing left to ask about: a later backend is not called at all, not called with an empty list. */
  @Test
  fun `a later backend is not asked when everything is covered`() = timeoutRunBlocking {
    val uvBackend = FakeBackend(setOf(hatch, uv))
    val pipBackend = FakeBackend(setOf(uv))
    narrow(listOf(uvBackend, pipBackend), listOf(hatch, uv))
    assertEquals(emptyList<Collection<PyTool>>(), pipBackend.asked)
  }

  /** A tool no backend manages is absent, so a caller can tell it from one the first backend owns. */
  @Test
  fun `a tool no backend manages is absent`() = timeoutRunBlocking {
    val listed = narrow(listOf(FakeBackend(setOf(hatch)), FakeBackend(emptySet())), listOf(hatch, uv))
    assertEquals(setOf(hatch), listed.keys)
  }

  /** The narrowing fold of `GenericPyToolManagerProvider.listAll`, without an environment to resolve backends from. */
  private suspend fun narrow(
    backends: List<GenericPyToolManager>,
    tools: Collection<PyTool>,
  ): Map<PyTool, InstalledInfo> =
    backends.fold(emptyMap()) { covered, backend ->
      val remaining = tools - covered.keys
      if (remaining.isEmpty()) covered else covered + backend.list(remaining)
    }
}
