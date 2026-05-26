package com.intellij.mcpserver.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.PlatformUtils

@Service
@State(name = "McpServerSettings", storages = [Storage("mcpServer.xml")])
internal class McpServerSettings : SimplePersistentStateComponent<McpServerSettings.MyState>(MyState()) {
  companion object {
    @JvmStatic
    fun getInstance(): McpServerSettings = service()

    private const val BASE_MCP_PORT: Int = 64342
    private const val PORT_STEP: Int = 20

    @JvmStatic
    val DEFAULT_MCP_PORT: Int = BASE_MCP_PORT + getPortOffset()

    @JvmStatic
    val DEFAULT_MCP_PRIVATE_PORT: Int = DEFAULT_MCP_PORT + 100

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