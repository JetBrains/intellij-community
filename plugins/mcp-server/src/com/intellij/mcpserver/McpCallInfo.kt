package com.intellij.mcpserver

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class McpCallInfo(
  val callId: Int,
  val clientInfo: ClientInfo,
  val project: Project?,
  val mcpToolDescriptor: McpToolDescriptor,
  val rawArguments: JsonObject,
  val meta: JsonObject
) {
  override fun toString(): String {
    return "McpCallAdditionalData(id=$callId, clientInfo=$clientInfo, toolName=${mcpToolDescriptor.name}"
  }
}

class ClientInfo(val name: String, val version: String)

class McpCallAdditionalDataElement(val additionalData: McpCallInfo) : AbstractCoroutineContextElement(Key), IntelliJContextElement {
  companion object Key : CoroutineContext.Key<McpCallAdditionalDataElement>
}

val CoroutineContext.mcpCallInfoOrNull: McpCallInfo? get() = get(McpCallAdditionalDataElement)?.additionalData
val CoroutineContext.mcpCallInfo: McpCallInfo get() = mcpCallInfoOrNull ?: error("mcpCallAdditionalData called outside of a MCP call")

/**
 * Returns information about the MCP client that is calling a tool.
 */
val CoroutineContext.clientInfo: ClientInfo get() = mcpCallInfo.clientInfo


/**
 * Returns information about the MCP tool that is called.
 */
val CoroutineContext.currentToolDescriptor: McpToolDescriptor get() = mcpCallInfo.mcpToolDescriptor

/**
 * The same as [projectOrNull], but throws an McpExpectedError if no project is open.
 */
val CoroutineContext.project: Project
  get() = projectOrNull ?: throw McpExpectedError("No project opened")

/*
 * The project path can be specified by the several ways: env, headers or as via the implicit tool parameter `projectPathParameterName`
 *
 * If a project is not specified and the only project is opened it will be returned.
 * If a project is not specified and multiple projects are opened it will throw an McpExpectedError with a list of open projects to suggest them to an LLM
 */
val CoroutineContext.projectOrNull: Project?
  get() {
    val projectFromContext = mcpCallInfo.project
    if (projectFromContext != null) return projectFromContext
    val openProjects = ProjectManager.getInstance().openProjects
    when (openProjects.size) {
      0 -> return null
      1 -> return openProjects[0]
      else -> {
        throw noSuitableProjectError("No exact project is specified while multiple projects are opened.")
      }
    }
  }

fun noSuitableProjectError(messagePrefix: String): McpExpectedError {
  val openProjects = ProjectManager.getInstance().openProjects
  val projects = OpenProjects(openProjects.mapNotNull { project -> project.basePath?.let { ProjectInfo(project.basePath.toString()) } })

  return McpExpectedError(mcpErrorText = """$messagePrefix
              | You may specify the project path via `$projectPathParameterName` parameter when calling a tool. 
              | If you're aware of the current working directory you may pass it as `$projectPathParameterName`. 
              | In the case when it's unobvious which project to use you have to ASK the USER about a project providing him a numbered list of the projects.
              | Currently open projects: ${Json.encodeToString(projects)}""".trimMargin(),
                         mcpErrorStructureContent = Json.encodeToJsonElement(projects).jsonObject)
}

@Serializable
data class ProjectInfo(val path: String)
@Serializable
data class OpenProjects(val projects: List<ProjectInfo>)