// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.settings.McpServerSettings.Companion.DEFAULT_MCP_PORT
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.PlatformUtils

interface McpServerSettings {
  companion object {
    @JvmStatic
    fun getInstance(): McpServerSettings = service<McpServerSettingsImpl>()

    @JvmStatic
    val DEFAULT_MCP_PORT: Int = BASE_MCP_PORT + getPortOffset()
    @JvmStatic
    val DEFAULT_MCP_PRIVATE_PORT: Int = DEFAULT_MCP_PORT + 100
  }

  var mcpServerPort: Int
  var enableMcpServer: Boolean
  var enableBraveMode: Boolean
}


@Service
@State(name = "McpServerSettings", storages = [Storage("mcpServer.xml")])
internal class McpServerSettingsImpl : McpServerSettings, SimplePersistentStateComponent<McpServerSettingsImpl.MyState>(MyState()) {

  override var mcpServerPort: Int
    get() = state.mcpServerPort
    set(value) {
      state.mcpServerPort = value
    }

  override var enableMcpServer: Boolean
    get() = state.enableMcpServer
    set(value) {
      state.enableMcpServer = value
    }

  override var enableBraveMode: Boolean
    get() = state.enableBraveMode
    set(value) {
      state.enableBraveMode = value
    }

  internal class MyState : BaseState() {
    var enableBraveMode: Boolean by property(false)
    var enableMcpServer: Boolean by property(false)
    var mcpServerPort: Int by property(DEFAULT_MCP_PORT)
  }
}

private const val BASE_MCP_PORT: Int = 64342
private const val PORT_STEP: Int = 20
private fun getPortOffset(): Int {
  return when (PlatformUtils.getPlatformPrefix()) {
    PlatformUtils.IDEA_PREFIX -> 0
    PlatformUtils.CLION_PREFIX -> PORT_STEP * 1
    PlatformUtils.DBE_PREFIX -> PORT_STEP * 3
    PlatformUtils.GOIDE_PREFIX -> PORT_STEP * 4
    PlatformUtils.PHP_PREFIX -> PORT_STEP * 5
    PlatformUtils.PYCHARM_PREFIX -> PORT_STEP * 6
    PlatformUtils.RIDER_PREFIX -> PORT_STEP * 7
    PlatformUtils.RUBY_PREFIX -> PORT_STEP * 8
    PlatformUtils.RUSTROVER_PREFIX -> PORT_STEP * 9
    PlatformUtils.WEB_PREFIX -> PORT_STEP * 10
    // todo android studio
    else -> 0
  }
}
