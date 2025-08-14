package com.intellij.mcpserver.settings

import com.intellij.openapi.components.*

@Service
@State(name = "McpServerSettings", storages = [Storage("mcpServer.xml")])
internal class McpServerSettings : SimplePersistentStateComponent<McpServerSettings.MyState>(MyState()) {
  companion object {
    @JvmStatic
    fun getInstance(): McpServerSettings = service()

    const val DEFAULT_MCP_PORT: Int = 64342
    const val DEFAULT_MCP_PRIVATE_PORT: Int = DEFAULT_MCP_PORT + 100
  }

  override fun loadState(state: MyState) {
    super.loadState(state)

  }

  internal class MyState : BaseState() {
    var enableBraveMode: Boolean by property(false)
    var enableMcpServer: Boolean by property(false)
    var mcpServerPort: Int by property(DEFAULT_MCP_PORT)
  }
}