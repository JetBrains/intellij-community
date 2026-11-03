// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiSourceMetadataOptionsTest {
  @Test
  fun `source metadata combines indexes from the workspace and selected features`() {
    val manifest = checkNotNull(parsePixiToml("""
      [pypi-options]
      index-url = 'https://workspace.example/simple'
      extra-index-urls = ['https://shared.example/simple']
      [feature.tools.pypi-options]
      extra-index-urls = ['https://tools.example/simple', 'https://shared.example/simple']
      [feature.other.pypi-options]
      extra-index-urls = ['https://other.example/simple']
      [environments]
      analysis = ['tools']
    """.trimIndent()))

    val arguments = pixiSourceMetadataIndexArguments(manifest, "analysis", null, emptyList())

    assertThat(arguments).containsExactly(
      "--index-url", "https://workspace.example/simple",
      "--extra-index-url", "https://shared.example/simple",
      "--extra-index-url", "https://tools.example/simple",
    )
  }

  @Test
  fun `an explicit source index replaces the workspace index`() {
    val manifest = checkNotNull(parsePixiToml("""
      [pypi-options]
      index-url = 'https://workspace.example/simple'
      extra-index-urls = ['https://shared.example/simple']
    """.trimIndent()))
    for (options in listOf(listOf("--index", "https://selected.example/simple"), listOf("--index=https://selected.example/simple"))) {
      val arguments = pixiSourceMetadataIndexArguments(manifest, "default", null, options)

      assertThat(arguments).describedAs(options.toString()).containsExactly(
        "--index-url", "https://selected.example/simple",
        "--extra-index-url", "https://shared.example/simple",
      )
    }
  }

  @Test
  fun `source metadata reads feature indexes from a pyproject manifest`() {
    val manifest = checkNotNull(parsePixiToml("""
      [tool.pixi.pypi-options]
      extra-index-urls = ['https://shared.example/simple']
      [tool.pixi.feature.tools.pypi-options]
      index-url = 'https://tools.example/simple'
      [tool.pixi.feature.other.pypi-options]
      index-url = 'https://other.example/simple'
    """.trimIndent()))

    val arguments = pixiSourceMetadataIndexArguments(manifest, "default", "tools", emptyList())

    assertThat(arguments).containsExactly(
      "--index-url", "https://tools.example/simple",
      "--extra-index-url", "https://shared.example/simple",
    )
  }

  @Test
  fun `source metadata combines links and insecure hosts from the active features`() {
    val manifest = checkNotNull(parsePixiToml("""
      [pypi-options]
      find-links = [{ url = 'https://shared.example/wheels.html' }]
      allow-insecure-host = ['shared.example']
      [feature.tools.pypi-options]
      find-links = [{ url = 'https://shared.example/wheels.html' }, { url = 'https://tools.example/wheels.html' }]
      allow-insecure-host = ['tools.example:8443', 'shared.example']
      [feature.other.pypi-options]
      find-links = [{ url = 'https://unused.example/wheels.html' }]
      allow-insecure-host = ['unused.example']
      [environments]
      analysis = { features = ['tools'] }
    """.trimIndent()))

    val arguments = pixiSourceMetadataIndexArguments(manifest, "analysis", null, emptyList())

    assertThat(arguments).containsExactly(
      "--find-links", "https://shared.example/wheels.html",
      "--find-links", "https://tools.example/wheels.html",
      "--trusted-host", "shared.example",
      "--trusted-host", "tools.example:8443",
    )
  }

  @Test
  fun `source metadata resolves relative links from the manifest directory`(@TempDir root: Path) {
    val manifest = checkNotNull(parsePixiToml("""
      [tool.pixi.pypi-options]
      find-links = [{ path = 'private wheels' }]
      [tool.pixi.feature.tools.pypi-options]
      find-links = [{ path = './private wheels' }]
      [tool.pixi.environments]
      analysis = ['tools']
    """.trimIndent()))

    val arguments = pixiSourceMetadataIndexArguments(manifest, "analysis", null, emptyList(), root)

    assertThat(arguments).containsExactly("--find-links", root.resolve("private wheels").toUri().toASCIIString())
  }

  @Test
  fun `an explicit feature keeps resolver options from all active environment features`() {
    val manifest = checkNotNull(parsePixiToml("""
      [pypi-options]
      index-url = 'https://workspace.example/simple'
      [feature.build.pypi-options]
      extra-index-urls = ['https://build.example/simple']
      [feature.tools.pypi-options]
      find-links = [{ url = 'https://tools.example/wheels.html' }]
      allow-insecure-host = ['tools.example']
      [environments]
      analysis = ['build', 'tools']
    """.trimIndent()))

    for (feature in listOf("default", "build", "tools")) {
      val arguments = pixiSourceMetadataIndexArguments(manifest, "analysis", feature, emptyList())

      assertThat(arguments).describedAs(feature).containsExactly(
        "--index-url", "https://workspace.example/simple",
        "--extra-index-url", "https://build.example/simple",
        "--find-links", "https://tools.example/wheels.html",
        "--trusted-host", "tools.example",
      )
    }
  }

  @Test
  fun `an explicit feature adds its resolver options to the active environment options`() {
    val manifest = checkNotNull(parsePixiToml("""
      [feature.build.pypi-options]
      extra-index-urls = ['https://build.example/simple']
      [feature.tools.pypi-options]
      extra-index-urls = ['https://tools.example/simple']
      [environments]
      analysis = ['build']
    """.trimIndent()))

    val arguments = pixiSourceMetadataIndexArguments(manifest, "analysis", "tools", emptyList())

    assertThat(arguments).containsExactly(
      "--extra-index-url", "https://build.example/simple",
      "--extra-index-url", "https://tools.example/simple",
    )
  }
}
