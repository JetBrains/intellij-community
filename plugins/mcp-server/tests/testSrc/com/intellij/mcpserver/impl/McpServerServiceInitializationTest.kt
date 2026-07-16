// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class McpServerServiceInitializationTest {
  @Test
  @SystemProperty(propertyKey = IJ_MCP_FORCE_ENABLE_PROPERTY, propertyValue = "false")
  fun `reading running state does not initialize tools`() {
    val settings = McpServerSettings.getInstance().state
    val wasEnabled = settings.enableMcpServer
    settings.enableMcpServer = false
    try {
      runBlocking {
        val service = McpServerService(this)

        assertThat(service.isToolsStateProviderInitialized()).isFalse()
        assertThat(service.isRunning).isFalse()
        assertThat(service.isToolsStateProviderInitialized()).isFalse()

        assertThat(service.getAllMcpTools()).isNotEmpty()
        assertThat(service.isToolsStateProviderInitialized()).isTrue()
      }
    }
    finally {
      settings.enableMcpServer = wasEnabled
    }
  }
}
