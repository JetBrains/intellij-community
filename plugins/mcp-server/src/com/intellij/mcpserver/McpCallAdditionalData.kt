package com.intellij.mcpserver

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import org.jetbrains.ide.RestService.Companion.getLastFocusedOrOpenedProject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class McpCallAdditionalData(
  val clientInfo: ClientInfo,
  val project: Project?,
  val mcpToolDescriptor: McpToolDescriptor,
  val rawArguments: JsonObject,
  val meta: JsonObject
)

class ClientInfo(val name: String, val version: String)

class McpCallAdditionalDataElement(val additionalData: McpCallAdditionalData) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<McpCallAdditionalDataElement>
}

val CoroutineContext.mcpCallAdditionalData: McpCallAdditionalData get() = get(McpCallAdditionalDataElement)?.additionalData ?: error("mcpCallAdditionalData called outside of a MCP call")

/**
 * Returns information about the MCP client that is calling a tool.
 */
val CoroutineContext.clientInfo: ClientInfo get() = mcpCallAdditionalData.clientInfo


/**
 * Returns information about the MCP tool that is called.
 */
val CoroutineContext.currentToolDescriptor: McpToolDescriptor get() = mcpCallAdditionalData.mcpToolDescriptor

/**
 * MCP tool can resolve a project with this extension property. In the case of running some MCP clients (like Claude) by IJ infrastructure
 * the project path can be specified by some ways (env or headers), then it can be resolved when calling MCP tool
 *
 * If a project is not specified, it will try to get the last focused or opened project
 */
val CoroutineContext.projectOrNull: Project?
  get() = getProjectOrNull(lookForAnyProject = true)

/**
 * The same as [projectOrNull], but throws an McpExpectedError if no project is open.
 */
val CoroutineContext.project: Project
  get() = projectOrNull ?: throw McpExpectedError("No project opened")

/**
 * The same as [projectOrNull], but allows to specify whether to look for any/last focused project or take only the one from the context element
 */
fun CoroutineContext.getProjectOrNull(lookForAnyProject: Boolean): Project? {
  val projectFromContext = mcpCallAdditionalData.project
  if (projectFromContext != null) return projectFromContext
  if (!lookForAnyProject) return null
  return getLastFocusedOrOpenedProject()
}