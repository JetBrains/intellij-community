// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.inspections.dependencies.PyDependencyDeclarations
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.writeText

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
internal class PixiDependencyDeclarationsTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)

  @Test
  fun `only active Python dependency groups contribute inspection declarations`(): Unit = timeoutRunBlocking {
    val manifest = rootFixture.get().resolve("pyproject.toml")
    manifest.writeText("""
      [tool.pixi.workspace]
      [tool.pixi.environments]
      default = ['tools']
      [dependency-groups]
      tools = ['group-probe>=2', {include-group = 'shared'}]
      shared = ['shared-probe', {include-group = 'tools'}]
      unused = ['inactive-probe']
      [project.optional-dependencies]
      tools = ['extra-probe']
      unused = ['inactive-extra']
    """.trimIndent())
    val project = projectFixture.get()
    val virtualFile = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest))
    val declarations = readAction {
      PyDependencyDeclarations.forFile(checkNotNull(PsiManager.getInstance(project).findFile(virtualFile)))
        .map { it.key.name to it.value.text }
    }
    assertThat(declarations.map { it.first }).containsExactlyInAnyOrder("group-probe", "shared-probe", "extra-probe")
    assertThat(declarations.map { it.second }).containsExactlyInAnyOrder("'group-probe>=2'", "'shared-probe'", "'extra-probe'")
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `dependency inspections collect native and PyPI declarations from active features`(manifestName: String): Unit = timeoutRunBlocking {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val manifest = rootFixture.get().resolve(manifestName)
    manifest.writeText("""
      [${prefix}workspace]
      channels = ['conda-forge']
      [${prefix}dependencies]
      native_probe = '>=1'
      [${prefix}pypi-dependencies]
      pypi-probe = { version = '>=2', index = 'https://pypi.org/simple/' }
      [${prefix}environments]
      default = ['tools']
      [${prefix}feature.tools.pypi-dependencies]
      active-probe = '*'
      [${prefix}feature.unused.dependencies]
      inactive-probe = '*'
    """.trimIndent())
    val project = projectFixture.get()
    val virtualFile = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest))

    val declarations = readAction {
      PyDependencyDeclarations.forFile(checkNotNull(PsiManager.getInstance(project).findFile(virtualFile)))
        .map { (requirement, element) -> requirement.name to element.text }
    }

    assertThat(declarations.map { it.first }).containsExactlyInAnyOrder("native-probe", "pypi-probe", "active-probe")
    assertThat(declarations.map { it.second }).containsExactlyInAnyOrder(
      "native_probe = '>=1'", "pypi-probe = { version = '>=2', index = 'https://pypi.org/simple/' }", "active-probe = '*'",
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `PyPI declarations preserve version constraints for dependency inspections`(manifestName: String): Unit = timeoutRunBlocking {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val manifest = rootFixture.get().resolve(manifestName)
    manifest.writeText("""
      [${prefix}workspace]
      [${prefix}pypi-dependencies]
      six = '>=1.16,<2'
      testpath = { version = '==0.6.0', index = 'https://pypi.org/simple/' }
    """.trimIndent())
    val project = projectFixture.get()
    val virtualFile = checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest))
    val requirements = readAction {
      PyDependencyDeclarations.forFile(checkNotNull(PsiManager.getInstance(project).findFile(virtualFile))).keys
        .associate { it.name to it.versionSpecs.map { version -> version.presentableText } }
    }
    assertThat(requirements["six"]).containsExactly(">=1.16", "<2")
    assertThat(requirements["testpath"]).containsExactly("==0.6.0")
  }
}
