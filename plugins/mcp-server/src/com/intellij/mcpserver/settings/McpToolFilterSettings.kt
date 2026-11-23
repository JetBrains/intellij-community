package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.settings.McpToolFilterSettings.Companion.DEFAULT_FILTER
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

interface McpToolFilterSettings {
  companion object {
    @JvmStatic
    fun getInstance(): McpToolFilterSettings = service<McpToolFilterSettingsImpl>()

    const val DEFAULT_FILTER: String = ""

    /** The id of the advanced setting that shows the router controls. See [isRouterConfigurable]. */
    const val ROUTER_CONTROLS_ADVANCED_SETTING_ID: String = "mcp.server.router.controls"
  }

  /** The value of the router setting. The settings page edits it, and it applies only while [isRouterConfigurable] is true. */
  var invocationMode: McpSessionInvocationMode

  /**
   * True when the user can see and change the router controls: the global router setting and the router-only flag of each tool.
   * The advanced setting [ROUTER_CONTROLS_ADVANCED_SETTING_ID] turns it on.
   * While it is false, the router is a quiet part of the managed sessions, and the stored router values do not apply.
   */
  val isRouterConfigurable: Boolean

  /**
   * The invocation mode that a session uses when it has no mode of its own.
   * It is the mode of [overrideInvocationModeForProcess] when one exists.
   * Otherwise it is the stored mode while [isRouterConfigurable] is true, and DIRECT while it is false.
   */
  val effectiveInvocationMode: McpSessionInvocationMode

  /**
   * Sets the mode of [effectiveInvocationMode] for this process only. The override beats the stored mode and the advanced setting.
   * No file keeps it. A null mode removes the override. The headless starter uses it, because it picks the mode itself.
   */
  @ApiStatus.Internal
  fun overrideInvocationModeForProcess(mode: McpSessionInvocationMode?)

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

  override val isRouterConfigurable: Boolean
    get() = AdvancedSettings.getBoolean(McpToolFilterSettings.ROUTER_CONTROLS_ADVANCED_SETTING_ID)

  @Volatile
  private var processInvocationMode: McpSessionInvocationMode? = null

  override val effectiveInvocationMode: McpSessionInvocationMode
    get() = processInvocationMode ?: if (isRouterConfigurable) invocationMode else McpSessionInvocationMode.DIRECT

  override fun overrideInvocationModeForProcess(mode: McpSessionInvocationMode?) {
    processInvocationMode = mode
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
