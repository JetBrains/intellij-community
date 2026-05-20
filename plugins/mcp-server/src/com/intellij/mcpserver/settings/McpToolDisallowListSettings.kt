package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpTool
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XMap
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
    if (state.toolStatesData.isEmpty()) {
      state.migrateLegacy261State()
      state.migrateLegacy262NightlyState()
    }
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

  fun toolStateFor(tool: McpTool): ToolState {
    val states = toolStates
    return states[tool.descriptor.fullyQualifiedName]
           ?: states[tool.descriptor.name]
           ?: ToolState()
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
    var routerOnly: Boolean by property(true)

    fun toToolState(): ToolState = ToolState(enabled = enabled, routerOnly = routerOnly)
  }

  internal class MyState : BaseState() {
    enum class LegacyMcpToolState { OFF, ON, ON_DEMAND }

    @get:XMap(propertyElementName = "toolStates")
    var toolStatesData: MutableMap<String, ToolStateBean> by map()

    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(sortBeforeSave = false)
    var legacyToolStates: MutableMap<String, LegacyMcpToolState> by map()

    @get:XCollection(style = XCollection.Style.v2)
    var disallowedToolNames: MutableList<String> by list()

    internal fun getToolStates(): Map<String, ToolState> = toolStatesData.mapValues { (_, toolState) -> toolState.toToolState() }

    internal fun migrateLegacy261State() {
      if (disallowedToolNames.isEmpty()) return
      val data = disallowedToolNames.associateWith { ToolStateBean(ToolState(enabled = false)) }
      toolStatesData.putAll(data)
      disallowedToolNames.clear()
    }

    internal fun migrateLegacy262NightlyState() {
      if (legacyToolStates.isEmpty()) return
      val data = legacyToolStates.mapValues { ToolStateBean(it.value.toToolState()) }
      toolStatesData.putAll(data)
      legacyToolStates.clear()
    }

    private fun LegacyMcpToolState.toToolState(): ToolState =
      when (this) {
        LegacyMcpToolState.ON -> ToolState(enabled = true, routerOnly = false)
        LegacyMcpToolState.ON_DEMAND -> ToolState(enabled = true, routerOnly = true)
        LegacyMcpToolState.OFF -> ToolState(enabled = false, routerOnly = false)
      }
  }
}
