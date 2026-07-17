// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.python.pyproject.dependencies.PyProjectDependencyGroupLocator
import com.intellij.python.pyproject.dependencies.spi.PyDependencyGroupLocator
import com.intellij.python.pyproject.dependencies.spi.resolveGroupName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.toml.lang.psi.TomlFileType
import org.toml.lang.psi.TomlKeySegment

// Mirrors the Poetry-specific header-path logic that PoetryPyProjectManager overrides. Kept
// inline in the test module because the production impl lives in the poetry backend module
// (`community/python/src`), which the pyproject-tests module doesn't depend on. If the Poetry
// shape rules change in production, update this alongside them.
private object PoetryLocatorForTest : PyDependencyGroupLocator {
  override fun resolveHeaderPath(path: List<String>): String? = when {
    path == listOf("tool", "poetry", "dependencies") -> "main"
    path.size == 5 && path[0] == "tool" && path[1] == "poetry" && path[2] == "group" && path[4] == "dependencies" -> path[3]
    else -> null
  }
}

@TestApplication
internal class PyDependencyGroupLocatorTest {
  private val projectFixture = projectFixture()

  private val project get() = projectFixture.get()

  // PEP 621 — `[project]` table with inline `dependencies = [...]` key.
  @Test
  fun pep621InlineDependencies() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [project]
      name = "pkg"
      dependencies = ["requests"]
      """.trimIndent(),
    )
    assertEquals("main", byName["dependencies"])
    assertNull(byName["name"], "`name` must not be classified as a group")
  }

  // PEP 621 — `[project.dependencies]` separate header form.
  @Test
  fun pep621DependenciesHeader() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [project.dependencies]
      requests = "*"
      """.trimIndent(),
    )
    assertEquals("main", byName["dependencies"])
  }

  // PEP 621 — `[project.optional-dependencies.<name>]` trailing-segment header.
  @Test
  fun pep621OptionalDependenciesTrailingHeader() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [project.optional-dependencies.gui]
      requests = "*"
      """.trimIndent(),
    )
    assertEquals("gui", byName["gui"])
  }

  // PEP 621 — `[project.optional-dependencies]` inline form (the uv-style layout).
  @Test
  fun pep621OptionalDependenciesInline() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [project.optional-dependencies]
      gui = ["pyqt5"]
      cli = ["click"]
      """.trimIndent(),
    )
    assertEquals("gui", byName["gui"])
    assertEquals("cli", byName["cli"])
  }

  // PEP 735 — `[dependency-groups]` top-level keys are groups; `include-group` inside an inline
  // table value must NOT be picked up.
  @Test
  fun pep735DependencyGroupsAndIncludeGroup() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [dependency-groups]
      test = ["pytest", { include-group = "lint" }]
      lint = ["ruff"]
      """.trimIndent(),
    )
    assertEquals("test", byName["test"])
    assertEquals("lint", byName["lint"])
    assertNull(byName["include-group"], "`include-group` is a nested inline-table key, not a dependency group")
  }

  // Bare `[project]` header must not match.
  @Test
  fun pep621BareProjectHeaderIsNotAGroup() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [project]
      name = "pkg"
      """.trimIndent(),
    )
    assertNull(byName["project"])
  }

  // Poetry — `[tool.poetry.dependencies]` → `main`.
  @Test
  fun poetryDependencies() = timeoutRunBlocking {
    val byName = resolveAll(
      PoetryLocatorForTest,
      """
      [tool.poetry.dependencies]
      python = "^3.11"
      """.trimIndent(),
    )
    assertEquals("main", byName["dependencies"])
  }

  // Poetry — `[tool.poetry.group.<name>.dependencies]` → `<name>`. The locator only fires on the
  // trailing `dependencies` segment, so the resolved name is keyed by that segment in [resolveAll].
  @Test
  fun poetryGroupDependencies() = timeoutRunBlocking {
    val byName = resolveAll(
      PoetryLocatorForTest,
      """
      [tool.poetry.group.dev.dependencies]
      pytest = "*"
      """.trimIndent(),
    )
    assertEquals("dev", byName["dependencies"])
  }

  // Returning null for unrelated tables.
  @Test
  fun noMatchOutsideKnownTables() = timeoutRunBlocking {
    val byName = resolveAll(
      PyProjectDependencyGroupLocator(),
      """
      [build-system]
      requires = ["setuptools"]
      """.trimIndent(),
    )
    assertNull(byName["requires"])
    assertNull(byName["build-system"])
  }

  /**
   * Builds an in-memory `pyproject.toml` PSI file from [toml], runs [locator] on every
   * [TomlKeySegment], and returns `segmentText → resolvedGroupName` for those that produced a
   * non-null answer (and `null` entries for the rest).
   */
  private suspend fun resolveAll(locator: PyDependencyGroupLocator, toml: String): Map<String, String?> = readAction {
    val file: PsiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("pyproject.toml", TomlFileType, toml)
    val out = mutableMapOf<String, String?>()
    PsiTreeUtil.collectElementsOfType(file, TomlKeySegment::class.java).forEach { segment ->
      val key = segment.name ?: return@forEach
      // Last-write-wins is fine — every test uses unique segment names per shape.
      out[key] = locator.resolveGroupName(segment)
    }
    out
  }
}
