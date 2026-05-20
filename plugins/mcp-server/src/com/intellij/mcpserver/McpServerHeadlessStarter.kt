// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.mcpserver.util.parsePathForProjectLookup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.waitForSmartMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Headless starter for running MCP server without IDE UI.
 *
 * Usage: `bazel run build:mcp_server -- mcpServer <project-path> [--port=<port>] [--invocation-mode=<mode>] [--allowed-tools=<names>]`
 *
 * Arguments:
 *   --project=<project-path>      Project path for file operations (required positional argument)
 *   --port=<port>                 Specify the TCP port for the MCP server (default: auto-select free port)
 *   -p <port>                     Short form of --port
 *   --invocation-mode=<mode>      Session invocation mode: `direct` or `via-router` (default: `via-router`).
 *                                 When `via-router`, tools are exposed through the universal router tool,
 *                                 so `McpSessionHandler` runs with `McpSessionInvocationMode.VIA_ROUTER`.
 *   --allowed-tools=<names>       Comma-separated whitelist of short tool names to expose.
 *                                 Converted into a `toolsFilter` mask: `-*,+*.<name1>,+*.<name2>,...`.
 *                                 Applies in both DIRECT and VIA_ROUTER modes (in VIA_ROUTER the router
 *                                 tool itself is always exposed; the whitelist constrains tools it can invoke).
 *                                 If omitted, a built-in [DEFAULT_ALLOWED_TOOLS] whitelist is applied.
 *                                 Pass `--allowed-tools=*` to disable whitelist and expose all tools.
 */
private sealed interface AllowedToolsSpec
private data object WildcardAllowedTools : AllowedToolsSpec
private data class ExplicitAllowedTools(val names: Set<String>) : AllowedToolsSpec

private val DEFAULT_ALLOWED_TOOLS: Set<String> = setOf(
  "skill_search",
)

private const val PROJECT_INIT_TIMEOUT_SECONDS_PROPERTY = "idea.mcp.server.project.init.timeout.seconds"
private const val SMART_MODE_TIMEOUT_SECONDS_PROPERTY = "idea.mcp.server.smart.mode.timeout.seconds"

internal class McpServerHeadlessStarter : ModernApplicationStarter() {
  override val isHeadless: Boolean = true

  override suspend fun start(args: List<String>) {
    if (args.isEmpty() || args[0] != "mcpServer") {
      System.err.println("Error: Invalid command. Expected 'mcpServer' as the first argument.")
      kotlin.system.exitProcess(1)
    }
    val actualArgs = args.drop(1)
    val startedAt = System.currentTimeMillis()

    val port = parsePort(actualArgs)
    if (port != null) {
      McpServerSettings.getInstance().state.mcpServerPort = port
    }

    val projectInitTimeout = timeoutFromProperty(PROJECT_INIT_TIMEOUT_SECONDS_PROPERTY, 10.minutes)
    val smartModeTimeout = timeoutFromProperty(SMART_MODE_TIMEOUT_SECONDS_PROPERTY, 30.minutes)
    System.err.println("MCP headless startup timeouts: projectInitialization=$projectInitTimeout, smartMode=$smartModeTimeout")

    val invocationMode = parseInvocationMode(actualArgs) ?: McpSessionInvocationMode.VIA_ROUTER
    McpToolFilterSettings.getInstance().invocationMode = invocationMode
    System.err.println("MCP session invocation mode: $invocationMode")

    val allowedTools = parseAllowedTools(actualArgs)
    when (allowedTools) {
      WildcardAllowedTools -> {
        McpToolFilterSettings.getInstance().toolsFilter = ""
        System.err.println("MCP tools whitelist: disabled (all tools exposed)")
      }
      is ExplicitAllowedTools -> {
        val mask = "-*," + allowedTools.names.joinToString(",") { "+*.$it" }
        McpToolFilterSettings.getInstance().toolsFilter = mask
        System.err.println("MCP tools whitelist: ${allowedTools.names.joinToString(", ")} (mask: $mask)")
      }
      null -> {
        val mask = "-*," + DEFAULT_ALLOWED_TOOLS.joinToString(",") { "+*.$it" }
        McpToolFilterSettings.getInstance().toolsFilter = mask
        System.err.println("MCP tools whitelist (default): ${DEFAULT_ALLOWED_TOOLS.joinToString(", ")} (mask: $mask)")
      }
    }

    val projectPaths = parseProjectPaths(actualArgs)

    System.err.println("Waiting for project initialization for ${projectPaths.joinToString(", ")}")
    val projects = projectPaths.mapNotNull { path ->
      loadProject(path)
    }
    if (projects.isEmpty()) {
      System.err.println("Error: no projects were opened; MCP server cannot start")
      kotlin.system.exitProcess(1)
    }

    waitForSmartMode(projects, smartModeTimeout)
    System.err.println("Projects are smart, starting MCP server after ${formatElapsed(startedAt)}...")

    try {
      McpServerService.getInstance().start()

      val actualPort = McpServerService.getInstance().port
      val sseUrl = McpServerService.getInstance().serverSseUrl
      System.err.println("*".repeat(30))
      System.err.println("* MCP Server started on port $actualPort")
      System.err.println("* SSE URL: $sseUrl")
      projects.forEach { project ->
        System.err.println("* Project: ${project.name} (${project.basePath})")
      }
      System.err.println("*".repeat(30))

      println("Copy-pastable JSON setting for ~/.claude.json or ~/.claude/settings.json:")
      println("""
        {
          "mcpServers": {
            "ide-headless-mcp": {
              "type": "sse",
              "url": "$sseUrl"
            }
          }
        }
      """.trimIndent())

      awaitCancellation()
    }
    finally {
      System.err.println("Shutting down MCP server...")
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          projects.forEach { project ->
            ProjectManager.getInstance().closeAndDispose(project)
          }
        }
      }
      System.err.println("Projects closed")
    }
  }

  private suspend fun loadProject(projectPathStr: String): Project? {
    val projectPath = parsePathForProjectLookup(projectPathStr)
    if (projectPath == null) {
      System.err.println("Warning: Invalid project path '$projectPathStr', skipping...")
      return null
    }

    TrustedProjects.setProjectTrusted(projectPath, true)

    val openStartedAt = System.currentTimeMillis()
    System.err.println("Opening/importing project '$projectPathStr' resolved to '$projectPath'...")
    val project = try {
      ProjectUtil.openOrImportAsync(
        file = projectPath,
        options = OpenProjectTask()
      )
    }
    catch (e: Exception) {
      System.err.println("Error opening project at $projectPathStr after ${formatElapsed(openStartedAt)}: ${e.stackTraceToString()}")
      return null
    }

    if (project == null) {
      System.err.println(
        "Warning: Unable to open project at $projectPathStr after ${formatElapsed(openStartedAt)}, skipping..."
      )
      return null
    }
    TrustedProjects.setProjectTrusted(project, true)

    System.err.println("Project opened/imported in ${formatElapsed(openStartedAt)}: ${describeProjectState(project)}")
    return project
  }

  private fun parsePort(args: List<String>): Int? {
    for (arg in args) {
      if (arg.startsWith("--port=")) {
        val portStr = arg.removePrefix("--port=")
        return portStr.toIntOrNull() ?: run {
          System.err.println("Invalid port number: $portStr")
          null
        }
      }
    }
    return null
  }

  private fun parseInvocationMode(args: List<String>): McpSessionInvocationMode? {
    val raw = args.firstOrNull { it.startsWith("--invocation-mode=") }
                ?.removePrefix("--invocation-mode=")
              ?: return null
    return when (raw.lowercase()) {
      "direct" -> McpSessionInvocationMode.DIRECT
      "via-router", "via_router", "router" -> McpSessionInvocationMode.VIA_ROUTER
      else -> {
        System.err.println("Invalid --invocation-mode value: '$raw'. Expected 'direct' or 'via-router'.")
        null
      }
    }
  }

  private fun parseAllowedTools(args: List<String>): AllowedToolsSpec? {
    val raw = args.firstOrNull { it.startsWith("--allowed-tools=") }
                ?.removePrefix("--allowed-tools=")
              ?: return null
    if (raw.trim() == "*") return WildcardAllowedTools
    val names = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return names.takeIf { it.isNotEmpty() }?.let(::ExplicitAllowedTools)
  }

  private fun parseProjectPaths(args: List<String>): List<String> {
    val projectPaths = args.filter { it.startsWith("--project=") }
      .map { it.removePrefix("--project=") }
      .distinct()

    if (projectPaths.isEmpty()) {
      return listOf(System.getProperty("user.dir"))
    }
    return projectPaths
  }

  private suspend fun waitForSmartMode(projects: List<Project>, timeout: Duration) {
    val waitStartedAt = System.currentTimeMillis()
    try {
      withTimeout(timeout) {
        projects.forEach { project ->
          System.err.println("Waiting for smart mode: ${describeProjectState(project)}")
          project.waitForSmartMode()
          System.err.println("Smart mode reached for ${project.name} in ${formatElapsed(waitStartedAt)}: ${describeProjectState(project)}"
          )
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      System.err.println("Error: timed out after $timeout waiting for smart mode.")
      projects.forEach { project ->
        System.err.println("Project smart mode state after timeout: ${describeProjectState(project)}")
      }
      throw e
    }
  }

  private fun timeoutFromProperty(propertyName: String, defaultValue: Duration): Duration {
    val rawValue = System.getProperty(propertyName) ?: return defaultValue
    val seconds = rawValue.toLongOrNull()
    if (seconds == null || seconds <= 0) {
      System.err.println("Warning: ignoring invalid $propertyName='$rawValue'; using $defaultValue")
      return defaultValue
    }
    return seconds.seconds
  }

  private fun describeProjectState(project: Project): String {
    val isDumb = runCatching { DumbService.getInstance(project).isDumb }.getOrElse { "error:${it::class.simpleName}:${it.message}" }
    return "name='${project.name}', basePath='${project.basePath}', disposed=${project.isDisposed}, dumb=$isDumb"
  }

  private fun formatElapsed(startedAt: Long): String {
    return "${System.currentTimeMillis() - startedAt} ms."
  }
}
