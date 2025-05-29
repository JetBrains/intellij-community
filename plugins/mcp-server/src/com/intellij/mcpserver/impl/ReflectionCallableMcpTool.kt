// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.impl.util.CallableBridge
import kotlinx.serialization.json.JsonObject

class ReflectionCallableMcpTool(override val descriptor: McpToolDescriptor, private val callableBridge: CallableBridge) : McpTool {
  override suspend fun call(args: JsonObject): McpToolCallResult {
    val result = callableBridge.call(args)
    if (result.result is String) return McpToolCallResult.text(result.result)
    val text = result.encodeToString()
    return McpToolCallResult.text(text)
  }
}