// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.evolution

import com.intellij.python.pixi.backend.sdk.PixiSdkFlavor
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.junit5Tests.framework.errorSinkFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.EvoWorkspace
import com.intellij.python.sdk.backend.evolution.ownedEnvDirOf
import com.intellij.python.sdk.common.EvoRowAction
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.isFailure
import com.jetbrains.python.getOrThrow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PixiEvoEnvironmentProviderTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)
  private val pyProjectFixture = projectFixture.pyProjectFixture(pathFixture)
  private val errorSink = errorSinkFixture()

  @Test
  fun `the widget reserves feature names and existing environment directories`(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    root.resolve("pixi.toml").writeText("[workspace]\n[feature.env]\n")
    root.resolve(".pixi/envs/env-2").createDirectories()

    val spec = PixiEvoEnvironmentProvider().addNewEnvSpec(context(), EvoSectionDto(label = "workspace", leaves = emptyList()))

    assertThat(spec?.name).isEqualTo("env-3")
    assertThat(spec?.takenNames).contains("default", "env", "env-2")
  }

  @Test
  fun `the widget offers a named environment with a Python version`(): Unit = timeoutRunBlocking {
    pathFixture.get().resolve("pixi.toml").writeText("[workspace]\n[environments]\ndocs = []\n")

    val spec = PixiEvoEnvironmentProvider().addNewEnvSpec(context(), EvoSectionDto(label = "workspace", leaves = emptyList()))

    assertThat(spec).isNotNull()
    assertThat(spec?.nameEditable).isTrue()
    assertThat(spec?.takenNames).containsExactlyInAnyOrder("default", "docs")
    assertThat(spec?.options?.map { it.token }).contains("3.13")
  }

  @ParameterizedTest
  @CsvSource(
    ".pixi/envs/docs/bin/python, docs",
    ".pixi/envs/default/bin/python3, default",
    ".pixi/envs/docs/python.exe, docs",
    ".pixi/envs/docs/Scripts/python.exe, docs",
    ".venv/bin/python, NULL",
    "other/envs/docs/bin/python, NULL",
    ".pixi/other/docs/bin/python, NULL",
    nullValues = ["NULL"],
  )
  fun `the Pixi SDK flavor recognizes platform layouts without accepting other tools`(relativePath: String, expected: String?) {
    assertThat(PixiSdkFlavor.toolEnvRefOf(
      PathHolder.Eel(Path.of("workspace").resolve(relativePath)),
      PyFlavorData.Empty,
    ))
      .isEqualTo(expected)
  }

  @Test
  fun `the widget offers selection for installed environments and creation for missing environments`(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    root.resolve("pixi.toml").writeText("[workspace]\n[environments]\ndocs = ['docs']\n")
    val home = root.resolve(".pixi/envs/default")
    home.resolve("bin").createDirectories().resolve("python").writeText("")
    home.resolve("python.exe").writeText("")
    val provider = PixiEvoEnvironmentProvider()
    val context = context()
    val binary = provider.pythonBinaryOf(context.workspace.pyProject, PyEnvRef("default"), context.fileSystem).getOrThrow()

    val result = provider.loadSections(context, emptyList())

    assertThat(result).asInstanceOf(type(EvoLoadResultDto.Ok::class.java)).satisfies(Consumer { loaded ->
      assertThat(loaded.sections).hasSize(1)
      val leaves = loaded.sections.single().leaves
      assertThat(leaves.map { it.title }).containsExactly("default", "docs")
      assertThat(leaves.map { it.action }).containsExactly(
        EvoRowAction.Select(provider.interpreterRefOf(PyEnvRef("default"))),
        EvoRowAction.CreateEnv("docs"),
      )
      assertThat(leaves.map { it.pythonBinary }).containsExactly(binary.path.pathString, null)
    })
  }

  @Test
  fun `the widget owns the declared Pixi environment directory`(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    root.resolve("pixi.toml").writeText("[workspace]\n[environments]\ndocs = []\n")
    val home = root.resolve(".pixi/envs/docs").createDirectories()
    root.resolve("docs").createDirectories()
    val provider = PixiEvoEnvironmentProvider()

    val directory = provider.ownedEnvDirOf(context(), EvoRowAction.Select(provider.interpreterRefOf(PyEnvRef("docs"))))

    assertThat(directory).isEqualTo(home)
    assertThat(provider.ownedEnvDirOf(context(), EvoRowAction.Select(provider.interpreterRefOf(PyEnvRef("undeclared"))))).isNull()
    assertThat(provider.ownedEnvDirOf(context(), EvoRowAction.CreateEnv("docs"))).isNull()
  }

  @Test
  fun `an undeclared environment cannot select a Python binary`(): Unit = timeoutRunBlocking {
    val root = pathFixture.get()
    root.resolve("pixi.toml").writeText("[workspace]\n")
    val home = root.resolve(".pixi/envs/undeclared")
    home.resolve("bin").createDirectories().resolve("python").writeText("")
    home.resolve("python.exe").writeText("")
    val context = context()

    val result = PixiEvoEnvironmentProvider().pythonBinaryOf(context.workspace.pyProject, PyEnvRef("undeclared"), context.fileSystem)

    assertThat(result.isFailure).isTrue()
  }

  @Test
  fun `a project without a Pixi manifest has no Pixi widget rows`(): Unit = timeoutRunBlocking {
    val result = PixiEvoEnvironmentProvider().loadSections(context(), emptyList())

    assertThat(result).isEqualTo(EvoLoadResultDto.Ok(emptyList()))
  }

  private fun context(): EvoToolContext {
    val pyProject = EvoPyProject(pyProjectFixture.get(), null)
    return EvoToolContext(EvoWorkspace(pyProject, listOf(pyProject)), pyProject, EelFileSystem(localEel), errorSink.get()) { emptyList() }
  }
}
