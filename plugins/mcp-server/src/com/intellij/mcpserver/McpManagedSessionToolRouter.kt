// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.settings.McpToolFilterSettings
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object McpManagedSessionToolRouter {
  @JvmStatic
  fun isEnabled(): Boolean = McpToolFilterSettings.getInstance().managedSessionToolRouterEnabled

  @JvmStatic
  fun invocationModeOverride(): McpSessionInvocationMode? =
    if (isEnabled()) McpSessionInvocationMode.VIA_ROUTER else null

  @JvmStatic
  fun enabledFlow(): StateFlow<Boolean> = McpToolFilterSettings.getInstance().managedSessionToolRouterEnabledFlow

  @JvmStatic
  @TestOnly
  fun setEnabledForTests(enabled: Boolean): Boolean {
    val settings = McpToolFilterSettings.getInstance()
    val previous = settings.managedSessionToolRouterEnabled
    settings.managedSessionToolRouterEnabled = enabled
    return previous
  }
}
