package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
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
    this.state.migrateLegacyState()
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
    val onDemand: Boolean = true,
  )

  internal class ToolStateBean() : BaseState() {
    constructor(toolState: ToolState) : this() {
      enabled = toolState.enabled
      onDemand = toolState.onDemand
    }

    var enabled: Boolean by property(true)
    var onDemand: Boolean by property(true)

    fun toToolState(): ToolState = ToolState(enabled = enabled, onDemand = onDemand)
  }

  internal class MyState : BaseState() {
    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(sortBeforeSave = false)
    var toolStatesData: MutableMap<String, ToolStateBean> by map()

    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(sortBeforeSave = false)
    var legacyToolStates: MutableMap<String, McpToolState> by map()

    fun getToolStates(): Map<String, ToolState> {
      migrateLegacyState()
      return toolStatesData.mapValues { (_, toolState) -> toolState.toToolState() }
    }

    fun migrateLegacyState() {
      if (toolStatesData.isNotEmpty() || legacyToolStates.isEmpty()) return

      toolStatesData.putAll(legacyToolStates.mapValues { (_, toolState) -> ToolStateBean(toolState.toToolState()) })
      legacyToolStates.clear()
    }
  }
}

internal fun McpToolState.toToolState(): McpToolDisallowListSettings.ToolState =
  when (this) {
    McpToolState.ON -> McpToolDisallowListSettings.ToolState(enabled = true, onDemand = false)
    McpToolState.ON_DEMAND -> McpToolDisallowListSettings.ToolState(enabled = true, onDemand = true)
    McpToolState.OFF -> McpToolDisallowListSettings.ToolState(enabled = false, onDemand = false)
  }
