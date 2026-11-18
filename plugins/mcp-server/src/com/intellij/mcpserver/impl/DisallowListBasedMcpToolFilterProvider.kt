package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

internal class DisallowListBasedMcpToolFilterProvider : UserConfigurableMcpToolFilterProvider {
  override fun applyFilters(
    context: McpToolFilterContext,
    clientInfo: Implementation?,
    sessionOptions: McpServerService.McpSessionOptions?,
    invocationMode: McpToolInvocationMode,
  ) {
    val settings = McpToolDisallowListSettings.getInstance()

    context.updateState(enabled = true) {
      it.isUserConfigurable && settings.toolStateFor(it).enabled
    }

    context.updateState(enabled = false) {
      it.isUserConfigurable && !settings.toolStateFor(it).enabled
    }

    // While the router controls are off, the stored flags do not apply. The tools keep the flags that the context and the other providers set.
    if (!McpToolFilterSettings.getInstance().isRouterConfigurable) return

    context.updateState(routerOnly = true) {
      it.isUserConfigurable && settings.toolStateFor(it).routerOnly
    }

    context.updateState(routerOnly = false) {
      it.isUserConfigurable && !settings.toolStateFor(it).routerOnly
    }
  }

  override fun getUpdates(
    clientInfo: Implementation?,
    scope: CoroutineScope,
    sessionOptions: McpServerService.McpSessionOptions?,
    invocationMode: McpToolInvocationMode,
  ): Flow<Unit> {
    val settings = McpToolDisallowListSettings.getInstance()
    return combine(settings.toolStatesFlow, routerConfigurableFlow()) { _, _ -> }
  }
}

/**
 * Emits [McpToolFilterSettings.isRouterConfigurable] after it subscribes, and then emits it again after each change of the advanced setting.
 */
private fun routerConfigurableFlow(): Flow<Boolean> = callbackFlow {
  val connection = ApplicationManager.getApplication().messageBus.connect(this)
  connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
    override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
      if (id == McpToolFilterSettings.ROUTER_CONTROLS_ADVANCED_SETTING_ID) {
        trySend(McpToolFilterSettings.getInstance().isRouterConfigurable)
      }
    }
  })
  send(McpToolFilterSettings.getInstance().isRouterConfigurable)
  awaitClose()
}
