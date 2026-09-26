// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class McpServerSettingsPersistenceTest {
  @Test
  fun `accessors observe the state installed by loadState`() = withRestoredSettings { settings ->
    settings.mcpServerPort = 1
    settings.enableMcpServer = false
    settings.enableBraveMode = false

    settings.loadState(McpServerSettingsImpl.MyState().also {
      it.enableMcpServer = true
      it.enableBraveMode = true
      it.mcpServerPort = 64444
    })

    assertThat(settings.enableMcpServer).isTrue()
    assertThat(settings.enableBraveMode).isTrue()
    assertThat(settings.mcpServerPort).isEqualTo(64444)
  }

  @Test
  fun `writes after loadState reach the persisted state`() = withRestoredSettings { settings ->
    settings.loadState(McpServerSettingsImpl.MyState())

    settings.enableMcpServer = true
    settings.enableBraveMode = true
    settings.mcpServerPort = 64445

    val persisted = settings.getState()
    assertThat(persisted.enableMcpServer).isTrue()
    assertThat(persisted.enableBraveMode).isTrue()
    assertThat(persisted.mcpServerPort).isEqualTo(64445)
  }

  private fun withRestoredSettings(action: (McpServerSettingsImpl) -> Unit) {
    val settings = McpServerSettings.getInstance() as McpServerSettingsImpl
    val original = settings.getState()
    try {
      action(settings)
    }
    finally {
      settings.loadState(original)
    }
  }
}
