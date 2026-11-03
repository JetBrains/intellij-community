// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiDependencyTargetTest {
  private val projectFixture = projectFixture()

  @Test
  fun `a mixed dependency group preserves the included declaration owner`() {
    val target = resolve("""
      [project]
      name = 'sample'
      [dependency-groups]
      tools = ['testpath>=0.5', {include-group = 'shared.group'}]
      'shared.group' = ['six>=1']
      [tool.pixi.workspace]
      [tool.pixi.environments]
      analysis = ['tools']
    """)
    assertThat(target.getOrThrow()).isEqualTo(PixiDependencyTarget("six", PixiDependencyScope.Feature("shared.group"),
                                                               PixiDependencyStorage.PYTHON_ARRAY))
  }

  @Test
  fun `a deleted environment cannot be recreated by a dependency change`() {
    val target = resolvePixiDependencyTarget(parse("[environments]\nanalysis = []"), "deleted", "six", true) { null }
    assertThat(target.isSuccess).isFalse()
  }

  @Test
  fun `the explicit default feature selects the root declaration`() {
    val target = resolvePixiDependencyTarget(parse("[pypi-dependencies]\nsix = '*'"),
                                           "default", "six", true, "default") { null }
    assertThat(target.getOrThrow()).isEqualTo(PixiDependencyTarget("six", PixiDependencyScope.Default))
  }

  @Test
  fun `native names with underscores and hyphens are different declarations`() {
    val target = resolve("""
      [dependencies]
      typing_extensions = '*'
      [feature.tools.dependencies]
      typing-extensions = '*'
      [environments]
      analysis = ['tools']
    """, "typing-extensions", false)
    assertThat(target.getOrThrow()).isEqualTo(PixiDependencyTarget("typing-extensions", PixiDependencyScope.Feature("tools")))
  }

  @Test
  fun `the default environment without the default feature owns new dependencies`() {
    val target = resolvePixiDependencyTarget(parse("""
      [environments.default]
      features = ['tools']
      no-default-feature = true
    """.trimIndent()), "default", "six", true) { null }
    assertThat(target.isSuccess).isTrue()
    assertThat(target.getOrThrow()).isEqualTo(PixiDependencyTarget("six", PixiDependencyScope.Environment("default"),
                                                               PixiDependencyStorage.UNDECLARED))
  }

  @Test
  fun `a native declaration preserves its original name`() {
    val target = resolve("""
      [dependencies]
      typing_extensions = '*'
      [environments]
      analysis = []
    """, "typing_extensions", pypi = false)
    assertThat(target.getOrThrow()).isEqualTo(PixiDependencyTarget("typing_extensions", PixiDependencyScope.Default))
  }

  @Test
  fun `an explicit feature preserves the native name and excludes other declarations`() {
    val target = resolvePixiDependencyTarget(parse("""
      [dependencies]
      typing_extensions = '*'
      [feature.tools.dependencies]
      typing_extensions = '*'
      [environments]
      analysis = []
    """.trimIndent()), "analysis", "typing_extensions", false, "tools") { null }
    assertThat(target.getOrThrow()).isEqualTo(PixiDependencyTarget("typing_extensions", PixiDependencyScope.Feature("tools")))
  }

  @Test
  fun `a new dependency belongs to the selected environment`() {
    val target = resolve("[environments]\nanalysis = []")
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Environment("analysis"))
    assertThat(target.getOrThrow().storage).isEqualTo(PixiDependencyStorage.UNDECLARED)
  }

  @Test
  fun `disabled features do not affect the target`() {
    val target = resolve("""
      [pypi-dependencies]
      six = '*'
      [feature.unused.pypi-dependencies]
      six = '*'
      [environments]
      analysis = []
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Default)
  }

  @Test
  fun `an environment without the default feature ignores its declarations`() {
    val target = resolve("""
      [pypi-dependencies]
      six = '*'
      [feature.tools.pypi-dependencies]
      six = '*'
      [environments.analysis]
      features = ['tools']
      no-default-feature = true
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Feature("tools"))
  }

  @Test
  fun `an inline declaration belongs to the environment`() {
    val target = resolve("""
      [environments.analysis.pypi-dependencies]
      six = '*'
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Environment("analysis"))
  }

  @Test
  fun `a quoted feature name is one key`() {
    val target = resolve("""
      [feature.'test.tools'.pypi-dependencies]
      six = '*'
      [environments]
      analysis = ['test.tools']
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Feature("test.tools"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["dependency-groups", "project.optional-dependencies"])
  fun `native Python dependency groups belong to their feature`(table: String) {
    val target = resolve("""
      [$table]
      tools = ['six[extra]>=1.16; python_version >= "3.12"']
      [tool.pixi.environments]
      analysis = ['tools']
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Feature("tools"))
    assertThat(target.getOrThrow().storage).isEqualTo(PixiDependencyStorage.PYTHON_ARRAY)
  }

  @Test
  fun `included dependency groups retain their owner and do not loop`() {
    val target = resolve("""
      [dependency-groups]
      tools = [{include-group = 'shared'}, {include-group = 'cycle'}]
      shared = ['six==1.16']
      cycle = [{include-group = 'tools'}]
      [tool.pixi.environments]
      analysis = ['tools']
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Feature("shared"))
  }

  @Test
  fun `project dependencies belong to the default feature`() {
    val target = resolve("""
      [project]
      dependencies = ['six>=1.16']
      [tool.pixi.environments]
      analysis = []
    """)
    assertThat(target.getOrThrow().scope).isEqualTo(PixiDependencyScope.Default)
    assertThat(target.getOrThrow().storage).isEqualTo(PixiDependencyStorage.PYTHON_ARRAY)
  }

  @Test
  fun `multiple active declarations require an explicit choice`() {
    val target = resolve("""
      [pypi-dependencies]
      six = '*'
      [feature.tools.pypi-dependencies]
      six = '*'
      [environments]
      analysis = ['tools']
    """)
    assertThat(target.isSuccess).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = ["target.linux-64.dependencies", "host-dependencies", "build-dependencies"])
  fun `specialized native declarations require an explicit choice`(table: String) {
    val target = resolve("[$table]\nsix = '*'", pypi = false)
    assertThat(target.isSuccess).isFalse()
  }

  private fun resolve(text: String, name: String = "six", pypi: Boolean = true): PyResult<PixiDependencyTarget> {
    val source = text.trimIndent()
    val environment = if ("environments" in source) "analysis" else "default"
    val manifest = parse(source)
    return resolvePixiDependencyTarget(manifest, environment, name, pypi) {
      PyRequirementParser.fromLine(it, projectFixture.get())?.name
    }
  }

  private fun parse(text: String) = checkNotNull(parsePixiToml(text))
}
