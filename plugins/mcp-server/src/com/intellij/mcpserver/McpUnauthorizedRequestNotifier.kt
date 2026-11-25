// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import org.jetbrains.annotations.ApiStatus

/**
 * Tells the user that an application reached the global MCP server without the token.
 *
 * The frontend registers the implementation. A host without a frontend has no service and only logs the attempt.
 */
@ApiStatus.Internal
interface McpUnauthorizedRequestNotifier {
  suspend fun notifyUnauthorizedRequest(port: Int)
}
