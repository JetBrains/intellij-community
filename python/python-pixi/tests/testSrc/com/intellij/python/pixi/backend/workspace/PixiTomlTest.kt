// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.workspace

import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.PackagingRequirements
@Layers.Functional
internal class PixiTomlTest {
  @Test
  fun `mixed groups retain strings and included tables`() {
    val manifest = checkNotNull(parsePixiToml("""
      [dependency-groups]
      tools = ['six>=1', {include-group = 'shared.group'}]
      'shared.group' = ['testpath']
    """.trimIndent()))
    val groups = checkNotNull(manifest.getTable("dependency-groups"))
    val tools = checkNotNull(groups.getArray("tools"))
    assertThat(tools).hasSize(2)
    assertThat(tools.first()).isEqualTo("six>=1")
    assertThat((tools.last() as PixiTomlTable).getString("include-group")).isEqualTo("shared.group")
    assertThat(groups.getArray(listOf("shared.group"))).containsExactly("testpath")
    assertThat(groups.keySet()).containsExactlyInAnyOrder("tools", "shared.group")
  }

  @ParameterizedTest
  @ValueSource(strings = ["[broken", "[dependency-groups]\ntools = ['six', {include-group = 'shared'} invalid]"])
  fun `invalid documents return no partial table`(text: String) {
    assertThat(parsePixiToml(text)).isNull()
  }
}
