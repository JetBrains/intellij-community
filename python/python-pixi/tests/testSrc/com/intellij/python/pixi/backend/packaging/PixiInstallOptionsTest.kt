// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiInstallOptionsTest {
  @ParameterizedTest
  @ValueSource(strings = ["--feature=tools", "--feature tools", "-f tools"])
  fun `browser options select the feature without passing it twice`(option: String) {
    val result = parsePixiInstallOptions(option.split(' ') + "--no-progress", PyDependencyGroup("tools"), false)
    assertThat(result.getOrThrow()).isEqualTo(PixiInstallOptions("tools", listOf("--no-progress")))
  }

  @ParameterizedTest
  @ValueSource(strings = ["--feature", "--feature=", "--feature=--bad", "--feature=other"])
  fun `invalid or conflicting features fail before a command runs`(option: String) {
    assertThat(parsePixiInstallOptions(listOf(option), PyDependencyGroup("tools"), false).isSuccess).isFalse()
  }

  @Test
  fun `the pip editable flag requires a local location`() {
    assertThat(parsePixiInstallOptions(listOf("-e"), null, false).isSuccess).isFalse()
    assertThat(parsePixiInstallOptions(listOf("-e"), null, true).getOrThrow().arguments).containsExactly("--editable")
  }

  @Test
  fun `the main group explicitly selects the default feature`() {
    assertThat(parsePixiInstallOptions(emptyList(), PyDependencyGroup("main"), false).getOrThrow().feature).isEqualTo("default")
  }

  @ParameterizedTest
  @ValueSource(strings = ["--package app", "--package=app"])
  fun `a member option fails before changing the shared environment`(option: String) {
    assertThat(parsePixiInstallOptions(option.split(' '), null, false).isSuccess).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "--manifest-path other/pixi.toml", "--manifest-path=other/pixi.toml", "-m other/pixi.toml", "-mother/pixi.toml",
    "--environment other", "--environment=other", "--workspace other", "--workspace=other", "-w other", "-wother",
    "--script other.py", "--script=other.py", "-s other.py", "-sother.py",
  ])
  fun `custom options cannot redirect the active workspace`(option: String) {
    assertThat(parsePixiInstallOptions(option.split(' '), null, false).isSuccess).isFalse()
  }
}
