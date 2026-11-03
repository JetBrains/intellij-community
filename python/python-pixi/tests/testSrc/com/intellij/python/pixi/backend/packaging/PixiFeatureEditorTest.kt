// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.isSuccess
import org.apache.tuweni.toml.Toml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiFeatureEditorTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)

  @ParameterizedTest
  @CsvSource(
    "pixi.toml, tools, true, false", "pyproject.toml, tools, true, false",
    "pixi.toml, test.tools, true, false", "pyproject.toml, test.tools, true, false",
    "pixi.toml, tools, false, false", "pyproject.toml, tools, false, false",
    "pixi.toml, test.tools, false, false", "pyproject.toml, test.tools, false, false",
    "pixi.toml, tools, true, true", "pyproject.toml, tools, true, true",
    "pixi.toml, test.tools, true, true", "pyproject.toml, test.tools, true, true",
    "pixi.toml, tools, false, true", "pyproject.toml, tools, false, true",
    "pixi.toml, test.tools, false, true", "pyproject.toml, test.tools, false, true",
  )
  fun `restoring an empty feature preserves the original document`(
    manifestName: String, feature: String, finalNewline: Boolean, crlf: Boolean,
  ): Unit = timeoutRunBlocking {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val text = """
      # Keep this comment.
      [${prefix}workspace]
      channels = [ 'conda-forge' ] # Keep the spaces.
      [${prefix}environments]
      analysis = ['$feature']
    """.trimIndent() +
               (if (manifestName == "pyproject.toml") "\n[project]\nname = 'packaging-test'\nversion = '0.1.0'" else "") +
               (if (finalNewline) "\n" else "")
    val original = if (crlf) text.replace("\n", "\r\n") else text
    val manifest = rootFixture.get().resolve(manifestName)
    manifest.writeText(original)

    val result = preservePixiFeature(projectFixture.get(), rootFixture.get(), feature)
    assertThat(result.isSuccess).isTrue()
    assertThat(findPixiWorkspace(rootFixture.get())?.root).isEqualTo(rootFixture.get())
    val restored = manifest.readText()
    assertThat(restored).startsWith(original)
    val parsed = Toml.parse(restored)
    assertThat(parsed.errors()).isEmpty()
    assertThat(parsed.getTable("${prefix}feature")?.keySet()).describedAs("The restored manifest: %s", restored).containsExactly(feature)
    assertThat(parsed.getTable("${prefix}feature")?.getTable(listOf(feature))?.keySet()).isEmpty()

    assertThat(preservePixiFeature(projectFixture.get(), rootFixture.get(), feature).isSuccess).isTrue()
    assertThat(manifest.readText()).isEqualTo(restored)
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `an existing feature remains unchanged`(manifestName: String): Unit = timeoutRunBlocking {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val original = """
      [${prefix}workspace]
      channels = ['conda-forge']
      [${prefix}feature.tools.dependencies]
      six = '*' # Keep the declaration.
    """.trimIndent() + "\n"
    val manifest = rootFixture.get().resolve(manifestName)
    manifest.writeText(original)
    assertThat(preservePixiFeature(projectFixture.get(), rootFixture.get(), "tools").isSuccess).isTrue()
    assertThat(manifest.readText()).isEqualTo(original)
  }
}
