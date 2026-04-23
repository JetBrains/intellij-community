package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service
@State(name = "McpToolFilterSettings", storages = [Storage("mcpToolFilter.xml")])
internal class McpToolFilterSettings : SimplePersistentStateComponent<McpToolFilterSettings.MyState>(MyState()) {
  companion object {
    @JvmStatic
    fun getInstance(): McpToolFilterSettings = service()

    const val DEFAULT_FILTER: String = ""
  }

  private val _toolsFilterFlow = MutableStateFlow(state.toolsFilter ?: DEFAULT_FILTER)
  private val _managedSessionToolRouterEnabledFlow = MutableStateFlow(state.managedSessionToolRouterEnabled)

  val toolsFilterFlow: StateFlow<String>
    get() = _toolsFilterFlow.asStateFlow()

  val managedSessionToolRouterEnabledFlow: StateFlow<Boolean>
    get() = _managedSessionToolRouterEnabledFlow.asStateFlow()

  override fun loadState(state: MyState) {
    super.loadState(state)
    _toolsFilterFlow.value = state.toolsFilter ?: DEFAULT_FILTER
    _managedSessionToolRouterEnabledFlow.value = state.managedSessionToolRouterEnabled
  }

  var toolsFilter: String
    get() = state.toolsFilter ?: DEFAULT_FILTER
    set(value) {
      state.toolsFilter = value
      _toolsFilterFlow.value = value
    }

  var managedSessionToolRouterEnabled: Boolean
    get() = state.managedSessionToolRouterEnabled
    set(value) {
      state.managedSessionToolRouterEnabled = value
      _managedSessionToolRouterEnabledFlow.value = value
    }

  var invocationMode: McpSessionInvocationMode
    get() = state.invocationMode?.let { McpSessionInvocationMode.valueOf(it) } ?: McpSessionInvocationMode.DIRECT
    set(value) {
      state.invocationMode = value.name
    }

  internal class MyState : BaseState() {
    var toolsFilter: String? by string(DEFAULT_FILTER)
    var managedSessionToolRouterEnabled: Boolean by property(true)
    var invocationMode: String? by string(McpSessionInvocationMode.DIRECT.name)
  }
}
