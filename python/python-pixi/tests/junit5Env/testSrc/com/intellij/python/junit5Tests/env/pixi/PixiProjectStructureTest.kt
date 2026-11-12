// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.python.pixi.backend.project.PixiPyProjectManager
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PixiProjectStructureTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `mixed groups preserve local edges beside Pixi table edges`(): Unit = timeoutRunBlocking {
    val first = project(directory.resolve("packages/first package"), "first")
    val second = project(directory.resolve("packages/second"), "second")
    val inactive = project(directory.resolve("packages/inactive"), "inactive")
    val table = project(directory.resolve("packages/table"), "table")
    val manifest = directory.resolve("pyproject.toml")
    val content = """
      [project]
      name = 'root'
      version = '0.1.0'
      [dependency-groups]
      'tools.group' = ['alias @ ${first.second.root.toUri()}', {include-group = 'shared'}]
      shared = ['second @ ${second.second.root.toUri()}', {include-group = 'tools.group'}]
      inactive = ['inactive @ ${inactive.second.root.toUri()}']
      [tool.pixi.workspace]
      channels = []
      [tool.pixi.pypi-dependencies]
      table = { path = 'packages/table', editable = true }
    """.trimIndent()
    manifest.writeText(content)
    val root = project(directory, "root")

    val structure = structure(root, first, second, inactive, table)

    assertThat(structure.dependencies.map[name("root")])
      .containsExactlyInAnyOrder(name("first"), name("second"), name("inactive"), name("table"))
    assertThat(manifest.readText()).isEqualTo(content)
  }

  @Test
  fun `member groups resolve paths from the member root`(): Unit = timeoutRunBlocking {
    directory.resolve("pixi.toml").writeText("[workspace]\nchannels = []\n")
    val root = project(directory, "root")
    val library = project(directory.resolve("packages/library"), "library")
    val memberRoot = directory.resolve("apps/member").createDirectories()
    memberRoot.resolve("pyproject.toml").writeText("""
      [project]
      name = 'member'
      version = '0.1.0'
      [dependency-groups]
      tools = ['alias @ {root:parent:parent:uri}/packages/library', 'self @ {root:uri}', {include-group = 'tools'}]
    """.trimIndent())
    val member = project(memberRoot, "member")
    val renamedLibrary = name("library@1") to library.second

    val structure = structure(root, member, renamedLibrary)

    assertThat(structure.dependencies.map[name("member")]).containsExactly(name("library@1"))
    assertThat(structure.dependencies.map[name("root")]).isEmpty()
  }

  @Test
  fun `group references ignore registry packages and invalid local paths`(): Unit = timeoutRunBlocking {
    directory.resolve("pixi.toml").writeText("[workspace]\nchannels = []\n")
    val root = project(directory, "root")
    val library = project(directory.resolve("library"), "library")
    val memberRoot = directory.resolve("member").createDirectories()
    memberRoot.resolve("pyproject.toml").writeText("""
      [project]
      name = 'member'
      version = '0.1.0'
      [dependency-groups]
      tools = ['library>=1', 'remote @ https://example.org/library', 'invalid @ file://host/library',
               'broken @ file:///bad path', 'missing @ ${directory.resolve("missing").toUri()}', {include-group = 'tools'}]
    """.trimIndent())
    val member = project(memberRoot, "member")
    val healthyRoot = directory.resolve("healthy").createDirectories()
    healthyRoot.resolve("pyproject.toml").writeText("""
      [project]
      name = 'healthy'
      version = '0.1.0'
      [dependency-groups]
      tools = ['library @ ${library.second.root.toUri()}', {include-group = 'tools'}]
    """.trimIndent())
    val healthy = project(healthyRoot, "healthy")

    val structure = structure(root, library, member, healthy)

    assertThat(structure.dependencies.map[name("member")]).isNullOrEmpty()
    assertThat(structure.dependencies.map[name("healthy")]).containsExactly(name("library"))
  }

  @Test
  fun `a malformed member does not remove other group edges`(): Unit = timeoutRunBlocking {
    directory.resolve("pixi.toml").writeText("[workspace]\nchannels = []\n")
    val root = project(directory, "root")
    val broken = project(directory.resolve("broken"), "broken")
    val library = project(directory.resolve("library"), "library")
    val healthyRoot = directory.resolve("healthy").createDirectories()
    healthyRoot.resolve("pyproject.toml").writeText("""
      [project]
      name = 'healthy'
      version = '0.1.0'
      [dependency-groups]
      tools = ['library @ ${library.second.root.toUri()}', {include-group = 'tools'}]
    """.trimIndent())
    val healthy = project(healthyRoot, "healthy")
    broken.second.root.resolve("pyproject.toml").writeText("[dependency-groups\n")

    val structure = structure(root, broken, library, healthy)

    assertThat(structure.dependencies.map[name("broken")]).isNullOrEmpty()
    assertThat(structure.dependencies.map[name("healthy")]).containsExactly(name("library"))
  }

  @ParameterizedTest
  @CsvSource(
    "pixi.toml, target.linux-64", "pyproject.toml, target.linux-64",
    "pixi.toml, feature.tools.target.win-64", "pyproject.toml, feature.tools.target.win-64",
    "pixi.toml, environments.analysis", "pyproject.toml, environments.analysis",
  )
  fun `scoped local dependencies create workspace graph edges`(manifestName: String, scope: String): Unit = timeoutRunBlocking {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val manifest = directory.resolve(manifestName)
    val content = """
      [${prefix}workspace]
      name = 'workspace'
      channels = []
      [${prefix}$scope.pypi-dependencies]
      child = { path = 'packages/child', editable = true }
    """.trimIndent()
    manifest.writeText(content)
    val root = project(directory, "root")
    val child = project(directory.resolve("packages/child"), "child")

    val structure = structure(root, child)

    assertThat(structure.dependencies.map).isEqualTo(mapOf(name("root") to setOf(name("child"))))
    assertThat(structure.membersToWorkspace[name("child")]).isEqualTo(name("root"))
    assertThat(manifest.readText()).isEqualTo(content)
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `local dependencies and members use the existing model`(manifestName: String): Unit = timeoutRunBlocking {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    directory.resolve(manifestName).writeText("""
      [${prefix}workspace]
      name = 'workspace'
      channels = ['conda-forge']
      [${prefix}pypi-dependencies]
      'first.package' = { path = 'packages/first package', editable = true }
      duplicate = { path = './packages/../packages/first package', editable = true }
      self = { path = '.', editable = true }
      missing = { path = 'missing', editable = true }
      version = '*'
      invalid = { path = 42 }
      invalid-path = { path = "\u0000" }
      [${prefix}feature.'test.feature'.pypi-dependencies]
      second = { path = 'packages/second', editable = true }
      [${prefix}feature.broken]
      pypi-dependencies = 42
    """.trimIndent())
    val root = project(directory, "root")
    val first = project(directory.resolve("packages/first package"), "first")
    val second = project(directory.resolve("packages/second"), "second")

    val structure = structure(root, first, second)

    assertThat(structure.membersToWorkspace).isEqualTo(mapOf(name("root") to name("root"),
                                                             name("first") to name("root"),
                                                             name("second") to name("root")))
    assertThat(structure.dependencies.map).isEqualTo(mapOf(name("root") to setOf(name("first"), name("second"))))
  }

  @ParameterizedTest
  @ValueSource(strings = ["workspace", "project"])
  fun `an explicit nested workspace owns its descendants`(table: String): Unit = timeoutRunBlocking {
    directory.resolve("pyproject.toml").writeText("[tool.pixi.workspace]\nname = 'outer'\nchannels = []\n")
    val outer = project(directory, "outer")
    val nestedRoot = directory.resolve("nested").createDirectories()
    nestedRoot.resolve("pyproject.toml").writeText("[tool.pixi.$table]\nname = 'nested'\nchannels = []\n")
    val nested = project(nestedRoot, "nested")
    val child = project(nestedRoot.resolve("child"), "child")
    val sibling = project(directory.resolve("sibling"), "sibling")

    val structure = structure(outer, nested, child, sibling)

    assertThat(structure.membersToWorkspace).isEqualTo(mapOf(name("outer") to name("outer"),
                                                             name("nested") to name("nested"),
                                                             name("child") to name("nested"),
                                                             name("sibling") to name("outer")))
    assertThat(structure.dependencies.map.values).allSatisfy { assertThat(it).isEmpty() }
  }

  @Test
  fun `a native workspace without a root model entry creates no membership`(): Unit = timeoutRunBlocking {
    val manifest = directory.resolve("pixi.toml")
    val content = "[workspace]\nchannels = []\n"
    manifest.writeText(content)
    val child = project(directory.resolve("child"), "child")

    val structure = structure(child)

    assertThat(structure.membersToWorkspace).isEmpty()
    assertThat(structure.dependencies.map).isEmpty()
    assertThat(directory.resolve("pyproject.toml")).doesNotExist()
    assertThat(manifest.readText()).isEqualTo(content)
  }

  @ParameterizedTest
  @ValueSource(strings = ["[project]\nname = 'ordinary'\nversion = '0.1.0'\n", "[tool.pixi.workspace\n"])
  fun `ordinary and malformed roots create no membership`(manifest: String): Unit = timeoutRunBlocking {
    directory.resolve("pyproject.toml").writeText(manifest)
    val child = project(directory.resolve("child"), "child")

    val structure = structure(child)

    assertThat(structure.membersToWorkspace).isEmpty()
    assertThat(structure.dependencies.map).isEmpty()
  }

  @Test
  fun `an empty model stays empty`(): Unit = timeoutRunBlocking {
    val structure = structure()

    assertThat(structure.membersToWorkspace).isEmpty()
    assertThat(structure.dependencies.map).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["workspace", "project"])
  fun `a virtual workspace keeps its declared name`(table: String): Unit = timeoutRunBlocking {
    val parsed = PyProjectToml.parse("[tool.pixi.$table]\nname = 'declared'\nchannels = []\n", "fallback")

    assertThat(parsed).isNotNull()
    assertThat(parsed?.project?.name).isEqualTo("declared")
  }

  @ParameterizedTest
  @ValueSource(strings = ["workspace", "project"])
  fun `a virtual workspace uses the fallback name`(table: String): Unit = timeoutRunBlocking {
    val parsed = PyProjectToml.parse("[tool.pixi.$table]\nchannels = []\n", "fallback")

    assertThat(parsed).isNotNull()
    assertThat(parsed?.project?.name).isEqualTo("fallback")
  }

  private fun project(root: Path, projectName: String): Pair<ProjectName, PyProjectTomlProject> {
    root.createDirectories()
    val manifest = root.resolve("pyproject.toml")
    if (!manifest.exists()) {
      manifest.writeText("[project]\nname = '$projectName'\nversion = '0.1.0'\n")
    }
    val parsed = checkNotNull(PyProjectToml.parse(manifest.readText(), projectName))
    return name(projectName) to object : PyProjectTomlProject {
      override val root: Path = root
      override val pyProjectToml: PyProjectToml = parsed
    }
  }

  private suspend fun structure(vararg projects: Pair<ProjectName, PyProjectTomlProject>): ProjectStructureInfo =
    PixiPyProjectManager().getProjectStructure(projects.toMap(), projects.associate { (name, entry) -> entry.root to name })

  private fun name(value: String): ProjectName = ProjectName(value)
}
