// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val CONTRIB_DIRECT_EXPOSURE_REGISTRY_KEY = "llm.agents.contrib.tools.direct.exposure.enabled"

/**
 * Marker extension for agent integrations that need managed-session-specific MCP tool router UI.
 */
@ApiStatus.Internal
interface McpManagedSessionSupport {
  companion object {
    val EP_NAME: ExtensionPointName<McpManagedSessionSupport> = ExtensionPointName
      .create("com.intellij.mcpServer.mcpManagedSessionSupport")

    @JvmStatic
    fun isAvailable(): Boolean = EP_NAME.extensionList.isNotEmpty()

    @JvmStatic
    fun invocationModeOverride(): McpSessionInvocationMode? =
      if (isAvailable() && Registry.`is`(CONTRIB_DIRECT_EXPOSURE_REGISTRY_KEY, false)) McpSessionInvocationMode.VIA_ROUTER else null

    @JvmStatic
    fun invocationModeOverrideFlow(scope: CoroutineScope): StateFlow<McpSessionInvocationMode?> {
      val flow = MutableStateFlow(invocationModeOverride())
      EP_NAME.addChangeListener(scope) { flow.value = invocationModeOverride() }
      scope.launch {
        Registry.get(CONTRIB_DIRECT_EXPOSURE_REGISTRY_KEY).asStringFlow().collect {
          flow.value = invocationModeOverride()
        }
      }
      return flow.asStateFlow()
    }
  }
}
