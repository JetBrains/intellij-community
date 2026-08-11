// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework

import com.intellij.openapi.project.Project
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Extracts the text payload from a tool response and fails fast when the tool returned a different content kind.
 */
fun CallToolResult.textContentOrThrow(): TextContent =
  content.firstOrNull() as? TextContent
  ?: throw AssertionError("Tool call result should be TextContent")

/**
 * A single progress notification observed while a tool call was in flight, together with the monotonic timestamp
 * (in nanoseconds) at which it was received.
 */
data class ObservedProgress(
  val progress: Progress,
  val receivedAtNanos: Long,
)

/**
 * The result of a tool call along with every [ObservedProgress] event that arrived during the call.
 */
data class ToolCallWithProgress(
  val result: CallToolResult,
  val progressEvents: List<ObservedProgress>,
)

/**
 * Calls an MCP tool bound to [project] and delegates response conversion/validation to [resultConverter],
 * returning whatever the converter produces.
 */
suspend fun <T> testMcpTool(
  project: Project,
  toolName: String,
  input: JsonObject,
  timeout: Duration = 239.seconds,
  resultConverter: (CallToolResult) -> T,
): T {
  return withMcpServerConnection(project) { client ->
    val result = client.callTool(toolName, input, options = RequestOptions(timeout = timeout))
    resultConverter(result)
  }
}

/**
 * Calls an MCP tool bound to [project] and checks that the textual result matches [expectedOutput] exactly.
 */
suspend fun testMcpTool(
  project: Project,
  toolName: String,
  input: JsonObject,
  expectedOutput: String,
) {
  testMcpTool(project, toolName, input) { result ->
    assertEquals(expectedOutput, result.textContentOrThrow().text)
  }
}

/**
 * Calls an MCP tool bound to [project], collecting every progress notification received while the call runs.
 */
suspend fun callToolWithProgress(
  project: Project,
  toolName: String,
  arguments: Map<String, Any?> = emptyMap(),
  meta: Map<String, Any?> = emptyMap(),
  timeout: Duration = 239.seconds,
): ToolCallWithProgress {
  val progressEvents = ArrayList<ObservedProgress>()
  val result = withMcpServerConnection(project) { client ->
    client.callTool(
      name = toolName,
      arguments = arguments,
      meta = meta,
      options = RequestOptions(
        onProgress = { progress ->
          progressEvents.add(ObservedProgress(progress = progress, receivedAtNanos = System.nanoTime()))
        },
        timeout = timeout,
      ),
    )
  }
  return ToolCallWithProgress(
    result = result,
    progressEvents = progressEvents,
  )
}
