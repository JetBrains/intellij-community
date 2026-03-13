package com.intellij.mcpserver.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
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

  private val _disallowedToolNamesFlow = MutableStateFlow(state.disallowedToolNames.toSet())

  val disallowedToolNamesFlow: StateFlow<Set<String>>
    get() = _disallowedToolNamesFlow.asStateFlow()

  override fun loadState(state: MyState) {
    super.loadState(state)
    _disallowedToolNamesFlow.value = state.disallowedToolNames.toSet()
  }

  var disallowedToolNames: Set<String>
    get() = state.disallowedToolNames.toSet()
    set(value) {
      state.disallowedToolNames = value.toMutableList()
      _disallowedToolNamesFlow.value = value
    }

  internal class MyState : BaseState() {
    @get:XCollection(style = XCollection.Style.v2)
    var disallowedToolNames: MutableList<String> by list()
  }
}
