// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.impl.util.CallableBridge
import kotlinx.serialization.json.JsonObject

class ReflectionCallableMcpTool(
  override val descriptor: McpToolDescriptor,
  private val callableBridge: CallableBridge,
  override val isUserConfigurable: Boolean,
) : McpTool {
  constructor(descriptor: McpToolDescriptor, callableBridge: CallableBridge) : this(descriptor, callableBridge, true)

  override suspend fun call(args: JsonObject): McpToolCallResult {
    val result = callableBridge.call(args)
    return when (result.result) {
      null -> McpToolCallResult.text("[null]")
      is Unit -> McpToolCallResult.text("[success]")
      is Char -> McpToolCallResult.text("'${result.result}'") // special case for Char to show quotes
      is String -> McpToolCallResult.text(result.result) // special case for String to avoid extra quotes added by Any?.toString()
      is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> McpToolCallResult.text(result.result.toString())
      is McpToolCallResult -> result.result
      is McpToolCallResultContent -> McpToolCallResult(arrayOf(result.result), isError = false)
      else -> McpToolCallResult.text(result.encodeToString(), structuredContent = result.encodeToJson())
    }
  }
}
