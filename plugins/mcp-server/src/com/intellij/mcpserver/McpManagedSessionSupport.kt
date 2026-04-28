// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    fun invocationModeOverride(): McpSessionInvocationMode? = if (isAvailable()) McpSessionInvocationMode.VIA_ROUTER else null

    @JvmStatic
    fun availableFlow(scope: CoroutineScope): StateFlow<Boolean> {
      val flow = MutableStateFlow(isAvailable())
      EP_NAME.addChangeListener(scope) { flow.value = isAvailable() }
      return flow.asStateFlow()
    }
  }
}
