// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.impl.util.CallableBridge
import kotlinx.serialization.json.JsonObject

class ReflectionCallableMcpTool(override val descriptor: McpToolDescriptor, private val callableBridge: CallableBridge) : McpTool {
  override suspend fun call(args: JsonObject): McpToolCallResult {
    val result = callableBridge.call(args)
    return when {
      result.result == null -> McpToolCallResult.text("[null]")
      result.result is Unit -> McpToolCallResult.text("[success]")
      result.result is Char -> McpToolCallResult.text("'${result.result}'") // special case for String to avoid extra quotes added by Any?.toString()
      result.result is String -> McpToolCallResult.text(result.result) // special case for String to avoid extra quotes added by Any?.toString()
      result.result.javaClass.isPrimitive -> McpToolCallResult.text(result.result.toString())
      result.result is McpToolCallResult -> result.result
      result.result is McpToolCallResultContent -> McpToolCallResult(arrayOf(result.result), isError = false)
      else -> McpToolCallResult.text(result.encodeToString(), structuredContent = result.encodeToJson())
    }
  }
}