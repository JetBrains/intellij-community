// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider

/**
 * Marker for [McpToolFilterProvider]s whose filtering reflects the user-editable settings managed by the MCP tools
 * settings UI ([com.intellij.mcpserver.settings.McpToolFilterConfigurable]).
 *
 * The configurable excludes such providers when building its tool list, so that the list shows every tool available
 * in the product instead of being filtered by the configurable's own persisted state.
 */
internal interface UserConfigurableMcpToolFilterProvider : McpToolFilterProvider
