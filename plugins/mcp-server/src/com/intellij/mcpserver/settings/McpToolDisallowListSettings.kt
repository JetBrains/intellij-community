package com.intellij.mcpserver.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service
@State(name = "McpToolDisallowListSettings", storages = [Storage("mcpToolDisallowList.xml")])
internal class McpToolDisallowListSettings : SimplePersistentStateComponent<McpToolDisallowListSettings.MyState>(MyState()) {
  companion object {
    @JvmStatic
    fun getInstance(): McpToolDisallowListSettings = service()
  }

  private val _toolStatesFlow = MutableStateFlow(state.getToolStates())

  val toolStatesFlow: StateFlow<Map<String, ToolState>>
    get() = _toolStatesFlow.asStateFlow()

  override fun loadState(state: MyState) {
    super.loadState(state)
    _toolStatesFlow.value = this.state.getToolStates()
  }

  var toolStates: Map<String, ToolState>
    get() = state.getToolStates()
    set(value) {
      state.toolStatesData.clear()
      state.toolStatesData.putAll(value.mapValues { (_, toolState) -> ToolStateBean(toolState) })
      state.legacyToolStates.clear()
      _toolStatesFlow.value = value
    }

  data class ToolState(
    val enabled: Boolean = true,
    val routerOnly: Boolean = true,
  )

  internal class ToolStateBean() : BaseState() {
    constructor(toolState: ToolState) : this() {
      enabled = toolState.enabled
      routerOnly = toolState.routerOnly
    }

    var enabled: Boolean by property(true)
    var onDemand: Boolean by property(true) // legacy: reads old XML for migration
    var routerOnly: Boolean by property(true)

    fun toToolState(): ToolState = ToolState(enabled = enabled, routerOnly = routerOnly)
  }

  internal class MyState : BaseState() {
    enum class LegacyMcpToolState { OFF, ON, ON_DEMAND }

    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(sortBeforeSave = false)
    var toolStatesData: MutableMap<String, ToolStateBean> by map()

    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(sortBeforeSave = false)
    var legacyToolStates: MutableMap<String, LegacyMcpToolState> by map()

    @get:XCollection(elementName = "option", valueAttributeName = "value")
    var disallowedToolNames: MutableList<String> by list()

    internal fun getToolStates(): Map<String, ToolState> {
      migrateLegacy261State()
      migrateLegacy262NightlyState()
      return toolStatesData.mapValues { (_, toolState) -> toolState.toToolState() }
    }

    internal fun migrateLegacy261State() {
      if (disallowedToolNames.isEmpty()) return
      disallowedToolNames.forEach { toolName ->
        toolStatesData[toolName] = ToolStateBean(ToolState(enabled = false))
      }
      disallowedToolNames.clear()
    }

    internal fun migrateLegacy262NightlyState() {
      if (toolStatesData.isNotEmpty() || legacyToolStates.isEmpty()) {
        migrateOnDemandToRouterOnly()
        return
      }

      toolStatesData.putAll(legacyToolStates.mapValues { (_, toolState) -> ToolStateBean(toolState.toToolState()) })
      legacyToolStates.clear()
      migrateOnDemandToRouterOnly()
    }

    private fun migrateOnDemandToRouterOnly() {
      for (bean in toolStatesData.values) {
        if (!bean.onDemand) {
          bean.routerOnly = false
          bean.onDemand = true
        }
      }
    }

    private fun LegacyMcpToolState.toToolState(): ToolState =
      when (this) {
        LegacyMcpToolState.ON -> ToolState(enabled = true, routerOnly = false)
        LegacyMcpToolState.ON_DEMAND -> ToolState(enabled = true, routerOnly = true)
        LegacyMcpToolState.OFF -> ToolState(enabled = false, routerOnly = false)
      }
  }
}
