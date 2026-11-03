// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@Subsystems.Interpreters
@Layers.Functional
internal class PixiWorkspaceTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `a workspace accepts mixed dependency group entries`() {
    temporaryDirectory.resolve("pyproject.toml").writeText("""
      [project]
      name = 'sample'
      [dependency-groups]
      tools = ['six>=1', {include-group = 'shared'}]
      shared = ['testpath']
      [tool.pixi.workspace]
      channels = ['conda-forge']
      [tool.pixi.environments]
      analysis = ['tools']
    """.trimIndent())

    assertThat(findPixiWorkspace(temporaryDirectory))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "analysis")))
  }

  @Test
  fun `finds environments in a pixi manifest`() {
    temporaryDirectory.resolve("pixi.toml").writeText(
      """
        [workspace]
        channels = ["conda-forge"]

        [environments]
        test = ["test"]
        lint = { features = ["lint"] }
      """.trimIndent()
    )

    assertThat(findPixiWorkspace(temporaryDirectory))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "lint", "test")))
  }

  @Test
  fun `finds a pyproject workspace`() {
    temporaryDirectory.resolve("pyproject.toml").writeText(
      """
        [project]
        name = "sample"

        [tool.pixi.workspace]
        channels = ["conda-forge"]

        [tool.pixi.environments]
        docs = ["docs"]
      """.trimIndent()
    )

    assertThat(findPixiWorkspace(temporaryDirectory))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "docs")))
  }

  @Test
  fun `uses a pyproject workspace root for a child directory`() {
    temporaryDirectory.resolve("pyproject.toml").writeText(
      """
        [tool.pixi.workspace]
        channels = ["conda-forge"]

        [tool.pixi.feature.test.dependencies]
        python = "*"

        [tool.pixi.environments]
        test = ["test"]
      """.trimIndent()
    )
    val member = temporaryDirectory.resolve("packages/service").createDirectories()
    member.resolve("pyproject.toml").writeText(
      """
        [project]
        name = "service"

      """.trimIndent()
    )

    assertThat(findPixiWorkspace(member.resolve("src").createDirectories()))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "test")))
  }

  @Test
  fun `lists the default environment once`() {
    temporaryDirectory.resolve("pixi.toml").writeText(
      """
        [workspace]

        [environments]
        default = ["default"]
        test = ["test"]
      """.trimIndent()
    )

    assertThat(findPixiWorkspace(temporaryDirectory))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "test")))
  }

  @Test
  fun `uses the workspace root for a child package`() {
    temporaryDirectory.resolve("pixi.toml").writeText(
      """
        [workspace]
        channels = ["conda-forge"]

        [feature.dev.dependencies]
        python = "*"

        [environments]
        dev = ["dev"]
      """.trimIndent()
    )
    val member = temporaryDirectory.resolve("packages/service").createDirectories()
    member.resolve("pixi.toml").writeText(
      """
        [package]
        name = "service"
        version = "1.0.0"
      """.trimIndent()
    )

    assertThat(findPixiWorkspace(member.resolve("src").createDirectories()))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "dev")))
  }

  @Test
  fun `ignores a pyproject without Pixi data`() {
    temporaryDirectory.resolve("pyproject.toml").writeText("[project]\nname = \"sample\"")

    assertThat(findPixiWorkspace(temporaryDirectory)).isNull()
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `an invalid manifest does not hide the enclosing workspace`(manifestName: String) {
    temporaryDirectory.resolve("pixi.toml").writeText("[workspace]\nchannels = ['conda-forge']\n")
    val child = temporaryDirectory.resolve("child").createDirectories()
    child.resolve(manifestName).writeText("[invalid\n")

    assertThat(findPixiWorkspace(child))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default")))
  }

  @ParameterizedTest
  @ValueSource(strings = ["source.py", "not-created/yet"])
  fun `finds a workspace from a file or a future directory`(relativePath: String) {
    temporaryDirectory.resolve("pixi.toml").writeText("[workspace]\n")
    temporaryDirectory.resolve("source.py").writeText("print('sample')\n")

    assertThat(findPixiWorkspace(temporaryDirectory.resolve(relativePath)))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default")))
  }

  @Test
  fun `uses the nearest nested workspace`() {
    temporaryDirectory.resolve("pixi.toml").writeText("[workspace]\n[environments]\nouter = []\n")
    val inner = temporaryDirectory.resolve("inner").createDirectories()
    inner.resolve("pixi.toml").writeText("[workspace]\n[environments]\ninner = []\n")

    assertThat(findPixiWorkspace(inner.resolve("src").createDirectories()))
      .isEqualTo(PixiWorkspace(inner, listOf("default", "inner")))
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a legacy project table defines a nested workspace`(manifestName: String) {
    temporaryDirectory.resolve("pixi.toml").writeText("[workspace]\n[environments]\nouter = []\n")
    val inner = temporaryDirectory.resolve("inner").createDirectories()
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    inner.resolve(manifestName).writeText("[${prefix}project]\n[${prefix}environments]\ninner = []\n")

    assertThat(findPixiWorkspace(inner.resolve("src").createDirectories()))
      .isEqualTo(PixiWorkspace(inner, listOf("default", "inner")))
  }

  @Test
  fun `uses a standalone package when there is no enclosing workspace`() {
    temporaryDirectory.resolve("pixi.toml").writeText("[package]\nname = 'sample'\nversion = '1.0.0'\n")

    assertThat(findPixiWorkspace(temporaryDirectory))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default")))
  }

  @Test
  fun `prefers the Pixi manifest when both formats exist`() {
    temporaryDirectory.resolve("pixi.toml").writeText("[workspace]\n[environments]\npixi = []\n")
    temporaryDirectory.resolve("pyproject.toml").writeText("[tool.pixi.workspace]\n[tool.pixi.environments]\npyproject = []\n")

    assertThat(findPixiWorkspace(temporaryDirectory))
      .isEqualTo(PixiWorkspace(temporaryDirectory, listOf("default", "pixi")))
  }

  @Test
  fun `returns no workspace when there is no manifest`() {
    assertThat(findPixiWorkspace(temporaryDirectory)).isNull()
  }
}
