package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpToolFilterProvider
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

  private val _toolStatesFlow = MutableStateFlow(state.toolStates.toMap())

  val toolStatesFlow: StateFlow<Map<String, McpToolFilterProvider.McpToolState>>
    get() = _toolStatesFlow.asStateFlow()

  override fun loadState(state: MyState) {
    super.loadState(state)
    _toolStatesFlow.value = state.toolStates.toMap()
  }

  var toolStates: Map<String, McpToolFilterProvider.McpToolState>
    get() = state.toolStates.toMap()
    set(value) {
      state.toolStates.clear()
      state.toolStates.putAll(value)
      _toolStatesFlow.value = value
    }

  internal class MyState : BaseState() {
    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(sortBeforeSave = false)
    var toolStates: MutableMap<String, McpToolFilterProvider.McpToolState> by map()
  }
}
