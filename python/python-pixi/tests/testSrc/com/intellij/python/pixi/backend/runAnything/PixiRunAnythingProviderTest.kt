// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.runAnything

import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.python.pixi.common.icons.PythonPixiCommonIcons
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
internal class PixiRunAnythingProviderTest {
  @Test
  fun `package completion checks all search pages before limiting results`() {
    val names = List(200) { "a-python-$it" } + listOf("python", "python_abi")
    val result = InMemorySearchPage.resultFromMatches(names, 100)

    assertThat(pixiCommandCompletionNames(result, "py")).containsExactly("python", "python_abi")
  }

  @Test
  fun `Run Anything exposes Pixi command help`() {
    val provider = provider()

    assertThat(provider.helpCommandPlaceholder).isEqualTo("pixi <command>")
    assertThat(provider.helpGroupTitle).isEqualTo("Python")
    assertThat(provider.helpIcon).isSameAs(PythonPixiCommonIcons.Pixi)
    assertThat(provider.getHelpItem(DataContext.EMPTY_CONTEXT)).isNotNull()
    assertThat(provider.findMatchingValue(DataContext.EMPTY_CONTEXT, "pixi add six")).isEqualTo("pixi add six")
    assertThat(provider.findMatchingValue(DataContext.EMPTY_CONTEXT, "pip install six")).isNull()
  }

  @Test
  fun `Run Anything completes Pixi workspace commands`() {
    val values = provider().getValues(DataContext.EMPTY_CONTEXT, "pixi ")

    assertThat(values).contains("pixi add", "pixi remove", "pixi update", "pixi install", "pixi list", "pixi run", "pixi info")
    assertThat(values).doesNotHaveDuplicates()
  }

  private fun provider(): RunAnythingCommandLineProvider {
    val providers = RunAnythingProvider.EP_NAME.extensionList.filterIsInstance<RunAnythingCommandLineProvider>()
      .filter { it.helpCommand == "pixi" }
    assertThat(providers).describedAs("The registered Pixi Run Anything provider").hasSize(1)
    return providers.single()
  }
}
