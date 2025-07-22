@file:Suppress("IO_FILE_USAGE")

package com.intellij.mcpserver

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.mcpserver.impl.util.network.findFirstFreePort
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.application.PathManager
import com.intellij.util.DebugAttachDetectorArgs
import kotlinx.serialization.json.*
import java.io.File
import kotlin.io.path.pathString
import kotlin.reflect.jvm.javaMethod

/**
 * Build a commandline to run MCP IDE server stdio transport in a separate process
 * @param ideServerPort port that the MCP server is running on. Can be obtained from [com.intellij.mcpserver.impl.McpServerService.port]
 * @param projectBasePath path to the project. It's passed as a metadata into MCP requests and used on the server side to determine the proper project for a tool
 * @return commandline to run MCP stdio transport process
 */
fun createStdioMcpServerCommandLine(ideServerPort: Int, projectBasePath: String?): GeneralCommandLine {
  val classpaths = McpStdioRunnerClasspath.CLASSPATH_CLASSES.map {
    (PathManager.getJarForClass(it) ?: error("No path for class $it")).pathString
  }.toSet()

  val commandLine = GeneralCommandLine()
    .withExePath("${System.getProperty("java.home")}${File.separator}bin${File.separator}java")
    .withParameters("-classpath", classpaths.joinToString(File.pathSeparator))

  if (DebugAttachDetectorArgs.isAttached()) {
    commandLine.withParameters("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y,address=127.0.0.1:${findFirstFreePort(64123)}")
  }
  commandLine
    .withParameters(::main.javaMethod!!.declaringClass.name)
    .withEnvironment(IJ_MCP_SERVER_PORT, ideServerPort.toString())
  if (projectBasePath != null) commandLine.withEnvironment(IJ_MCP_SERVER_PROJECT_PATH, projectBasePath)
  return commandLine
}

/**
 * Convert a commandline to run MCP IDE server stdio transport to a JSON entry that can be passed to MCP client json config
 *
 * See [Transports](https://modelcontextprotocol.io/docs/concepts/transports)
 *
 * Returns an object like
 * ``` json
 * {
 *   "command": "<mcp runner>",
 *   "args": ["<mcp runner args>"],
 *   "env": {
 *     "IJ_MCP_SERVER_PORT": "<ide server port>",
 *     "IJ_MCP_SERVER_PROJECT_PATH": "<project path>"
 *   }"
 * }
 * ```
 */
fun createStdioServerJsonEntry(cmd: GeneralCommandLine): JsonObject {
  return buildJsonObject {
    put("type", "stdio")
    put("env", buildJsonObject {
      for ((k, v) in cmd.environment) {
        put(k, v)
      }
    })
    put("command", cmd.exePath)
    put("args", buildJsonArray {
      cmd.parametersList.parameters.forEach {
        add(it)
      }
    })
  }
}

/**
 * Creates a JSON entry for a MCP server that uses SSE transport
 * @param port port that the MCP server is running on. Can be obtained from [com.intellij.mcpserver.impl.McpServerService.port]
 *
 * See [Transports](https://modelcontextprotocol.io/docs/concepts/transports)
 *
 * Returns an object like
 * ``` json
 * {
 *   "type": "sse",
 *   "url": "http://localhost:<port>/sse"
 * }
 * ```
 */
fun createSseServerJsonEntry(port: Int, projectBasePath: String?): JsonObject {
  return buildJsonObject {
    put("type", "sse")
    put("url", "http://localhost:$port/sse")
    put("headers", buildJsonObject {
      put(IJ_MCP_SERVER_PROJECT_PATH, projectBasePath)
    })
  }
}

/**
 * Creates a JSON configuration entry for a MCP server that uses stdio transport based on createStdioMcpServerCommandLine()
 *
 * Returns an object like
 * ``` json
 * {
 *   "command": "<mcp runner>",
 *   "args": ["<mcp runner args>"],
 *   "env": {
 *     "IJ_MCP_SERVER_PORT": "<ide server port>",
 *     "IJ_MCP_SERVER_PROJECT_PATH": "<project path>"
 *   }
 * }
 * ```
 */
fun createStdioMcpServerJsonConfiguration(ideServerPort: Int, projectBasePath: String?): JsonObject {
  val commandLine = createStdioMcpServerCommandLine(ideServerPort, projectBasePath)
  return createStdioServerJsonEntry(commandLine)
}