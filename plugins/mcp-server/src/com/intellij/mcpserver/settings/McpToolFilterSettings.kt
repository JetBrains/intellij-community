package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.settings.McpToolFilterSettings.Companion.DEFAULT_FILTER
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface McpToolFilterSettings {
  companion object {
    @JvmStatic
    fun getInstance(): McpToolFilterSettings = service<McpToolFilterSettingsImpl>()

    const val DEFAULT_FILTER: String = ""
  }

  var invocationMode: McpSessionInvocationMode
  var toolsFilter: String
  val toolsFilterFlow: StateFlow<String>
}

@Service
@State(name = "McpToolFilterSettings", storages = [Storage("mcpToolFilter.xml")])
internal class McpToolFilterSettingsImpl : McpToolFilterSettings, SimplePersistentStateComponent<McpToolFilterSettingsImpl.MyState>(MyState()) {
  private val _toolsFilterFlow = MutableStateFlow(state.toolsFilter ?: DEFAULT_FILTER)

  override val toolsFilterFlow: StateFlow<String>
    get() = _toolsFilterFlow.asStateFlow()

  override fun loadState(state: MyState) {
    super.loadState(state)
    _toolsFilterFlow.value = state.toolsFilter ?: DEFAULT_FILTER
  }

  override var toolsFilter: String
    get() = state.toolsFilter ?: DEFAULT_FILTER
    set(value) {
      state.toolsFilter = value
      _toolsFilterFlow.value = value
    }

  override var invocationMode: McpSessionInvocationMode
    get() = state.invocationMode?.let { McpSessionInvocationMode.valueOf(it) } ?: McpSessionInvocationMode.DIRECT
    set(value) {
      state.invocationMode = value.name
    }

  internal class MyState : BaseState() {
    var toolsFilter: String? by string(DEFAULT_FILTER)
    var invocationMode: String? by string(McpSessionInvocationMode.DIRECT.name)
  }
}
