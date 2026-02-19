// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes

/**
 * Headless starter for running MCP server without IDE UI.
 *
 * Usage: `bazel run build:mcp_server -- mcpServer <project-path> [--port=<port>]`
 *
 * Arguments:
 *   <project-path>  Project path for file operations (required positional argument)
 *   --port=<port>   Specify the TCP port for the MCP server (default: auto-select free port)
 *   -p <port>       Short form of --port
 */
internal class McpServerHeadlessStarter : ModernApplicationStarter() {
  override val isHeadless: Boolean = true

  override suspend fun start(args: List<String>) {
    if (args.isEmpty() || args[0] != "mcpServer") {
      System.err.println("Error: Invalid command. Expected 'mcpServer' as the first argument.")
      kotlin.system.exitProcess(1)
    }
    val actualArgs = args.drop(1)

    val port = parsePort(actualArgs)
    if (port != null) {
      McpServerSettings.getInstance().state.mcpServerPort = port
    }

    val projectPaths = parseProjectPaths(actualArgs)
    val projects = mutableListOf<Project>()

    System.err.println("Waiting for project initialization...")
    try {
      for (projectPathStr in projectPaths) {
        val projectPath = Paths.get(projectPathStr)
        val project = ProjectUtil.openOrImportAsync(
          file = projectPath,
          options = OpenProjectTask()
        )
        if (project == null) {
          System.err.println("Warning: Unable to open project at $projectPathStr, skipping...")
          continue
        }
        projects.add(project)
      }
    }
    finally {
      // This timeout was chosen at random.
      withTimeout(1.minutes) {
        projects.forEach { project ->
          StartupManager.getInstance(project).allActivitiesPassedFuture.join()
        }
      }
    }
    System.err.println("Project initialization completed")

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
            "idea-headless-mcp": {
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

  private fun parseProjectPaths(args: List<String>): List<String> {
    val projectPaths = args.filter { it.startsWith("--project=") }
      .map { it.removePrefix("--project=") }
      .distinct()

    if (projectPaths.isEmpty()) {
      return listOf(System.getProperty("user.dir"))
    }
    return projectPaths
  }
}
