// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.icons.AllIcons
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.toInProjectAndOtherSections
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path
import javax.swing.Icon

/**
 * Guards which environments the widget files under "In-project".
 *
 * The heading means where the environment is — directly inside the project folder — and it used to mean what the
 * environment is called. A project holding `.venv` and `.venv1` therefore showed one of them under "In-project" and the
 * other under a heading naming the project folder, which is the very same place (PY-91389).
 */
@TestApplication
class PyEvoInProjectSectionsTest {
  private val baseDir: Path = Path.of("/home/me/PycharmProjects/FastAPIProject23")

  /**
   * A provider that is nothing but the three members [toInProjectAndOtherSections] reads off one: its id, which says
   * whether another tool made an environment, and its name and icon, which every row it owns wears.
   */
  private val owner = object : PyEvoEnvironmentProvider {
    override val toolId: ToolId = ToolId("uv")
    override val label: String = "uv"
    override val icon: Icon = AllIcons.Language.Python

    override suspend fun loadSections(
      pyProject: EvoPyProject,
      fileSystem: FileSystem<PathHolder.Eel>,
      discovered: List<DiscoveredVenv>,
    ): EvoLoadResultDto = EvoLoadResultDto.Ok(emptyList())
  }

  private fun venv(root: String): DiscoveredVenv =
    DiscoveredVenv(pythonBinary = Path.of(root).resolve("bin/python"), config = emptyMap(), version = "3.14.0")

  private fun sections(vararg roots: String): List<EvoSectionDto> =
    roots.map { venv(it) }.toInProjectAndOtherSections(owner, baseDir, AllIcons.Language.Python, "In-project")

  /** Row titles of each section, with its heading. */
  private fun shape(sections: List<EvoSectionDto>): List<Pair<String?, List<String>>> =
    sections.map { section -> section.label to section.leaves.map { it.title } }

  @Test
  fun `every environment in the project folder is in-project`() {
    // The reported bug: `.venv1` sits beside `.venv` and was filed under a heading naming the project folder.
    assertEquals(
      listOf<Pair<String?, List<String>>>("In-project" to listOf(".venv", ".venv1")),
      shape(sections("$baseDir/.venv", "$baseDir/.venv1")),
    )
  }

  @Test
  fun `the default leads and the rest follow by name`() {
    assertEquals(
      listOf<Pair<String?, List<String>>>("In-project" to listOf(".venv", ".venv1", ".venv2", "env")),
      shape(sections("$baseDir/.venv2", "$baseDir/env", "$baseDir/.venv", "$baseDir/.venv1")),
    )
  }

  @Test
  fun `a missing default keeps its place as the row that creates it`() {
    val sections = sections("$baseDir/.venv1")
    assertEquals(listOf<Pair<String?, List<String>>>("In-project" to listOf(".venv", ".venv1")), shape(sections))
    // The leading row creates the environment; only the second one points at an interpreter that exists.
    val refs = sections.single().leaves.map { it.ref }
    assertEquals(true, refs[0] is PyInterpreterRef.CreateEnv, "expected a create row for the absent .venv, got ${refs[0]}")
    assertEquals(true, refs[1] is PyInterpreterRef.DetectedPath, "expected .venv1 to be selectable, got ${refs[1]}")
  }

  @Test
  fun `an environment outside the project keeps its own folder heading`() {
    val shape = shape(sections("$baseDir/.venv", "/home/me/envs/other"))
    assertEquals("In-project" to listOf(".venv"), shape.first())
    assertEquals(2, shape.size)
    assertEquals(listOf("other"), shape[1].second)
  }

  @Test
  fun `a nested environment is not in-project`() {
    // One folder deeper is a different place, and its heading says which.
    val shape = shape(sections("$baseDir/.venv", "$baseDir/sub/.venv"))
    assertEquals("In-project" to listOf(".venv"), shape.first())
    assertEquals(2, shape.size)
  }
}
