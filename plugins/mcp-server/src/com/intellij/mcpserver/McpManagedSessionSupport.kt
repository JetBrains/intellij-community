// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Marker extension for agent integrations that need managed-session-specific MCP tool router UI.
 */
@ApiStatus.Internal
interface McpManagedSessionSupport {
  companion object {
    private val EP_NAME: ExtensionPointName<McpManagedSessionSupport> =
      ExtensionPointName.create("com.intellij.mcpServer.mcpManagedSessionSupport")

    @JvmStatic
    fun isAvailable(): Boolean = EP_NAME.extensionList.isNotEmpty()
  }
}
