package com.intellij.mcpserver

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.impl.util.network.findFirstFreePort
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.stdio.main
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.util.DebugAttachDetectorArgs
import com.intellij.util.Restarter
import com.intellij.util.system.OS
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.path.pathString
import kotlin.reflect.jvm.javaMethod

/**
 * Build a commandline to run MCP IDE server stdio transport in a separate process
 * @param ideServerPort port that the MCP server is running on. Can be obtained from [com.intellij.mcpserver.impl.McpServerService.port]
 * @param projectBasePath path to the project. It's passed as a metadata into MCP requests and used on the server side to determine the proper project for a tool
 * @return commandline to run MCP stdio transport process
 */
fun createStdioMcpServerCommandLine(ideServerPort: Int, projectBasePath: String?, authToken: Pair<String, String>? = null): GeneralCommandLine {
  val launcher = Restarter.getIdeStarter()
  val commandLine = if (launcher != null) {
    GeneralCommandLine(launcher.pathString, "stdioMcpServer")
  }
  else {
    @Suppress("OPT_IN_USAGE")
    val javaLauncher = "${System.getProperty("java.home")}/bin/java${if (OS.CURRENT == OS.Windows) ".exe" else ""}"
    GeneralCommandLine(javaLauncher, "-classpath", getClasspath())
      .apply {
        if (DebugAttachDetectorArgs.isAttached()) {
          withParameters("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y,address=127.0.0.1:${findFirstFreePort(64123)}")
        }
      }
      .withParameters(::main.javaMethod!!.declaringClass.name)
  }
  return commandLine.withEnvironment(IJ_MCP_SERVER_PORT, ideServerPort.toString()).apply {
    if (projectBasePath != null) withEnvironment(IJ_MCP_SERVER_PROJECT_PATH, projectBasePath)
    if (authToken != null) withEnvironment(authToken.first, authToken.second)
  }
}

private fun getClasspath(): String {
  val classes = buildSet {
    addAll(McpStdioRunnerClasspath.CLASSPATH_CLASSES)
    if (ApplicationManager.getApplication().isUnitTestMode) {
      add(AbstractTransport::class.java)
      add(SseClientTransport::class.java)
    }
  }
  return classes.joinToString(@Suppress("IO_FILE_USAGE") java.io.File.pathSeparator) {
    (PathManager.getJarForClass(it) ?: error("No path for class $it")).pathString
  }
}

/**
 * Convert a commandline to run MCP IDE server stdio transport to a JSON entry that can be passed to MCP client JSON config
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
 * Creates a JSON entry for an MCP server that uses SSE transport
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
fun createSseServerJsonEntry(port: Int, projectBasePath: String?, authToken: Pair<String, String>? = null): JsonObject {
  val provider = McpServerConnectionAddressProvider.getInstanceOrNull()
  val url = provider?.httpUrl("/sse", portOverride = port) ?: "http://localhost:$port/sse"
  return buildTransportJson(
    type = "sse",
    url = url,
    projectBasePath = projectBasePath,
    authToken = authToken,
  )
}
fun createStreamableServerJsonEntry(port: Int, projectBasePath: String?, authToken: Pair<String, String>? = null): JsonObject {
  val provider = McpServerConnectionAddressProvider.getInstanceOrNull()
  val url = provider?.httpUrl("/stream", portOverride = port) ?: "http://localhost:$port/stream"
  return buildTransportJson(
    type = "streamable-http",
    url = url,
    projectBasePath = projectBasePath,
    authToken = authToken,
  )
}

private fun buildTransportJson(type: String, url: String, projectBasePath: String?, authToken: Pair<String, String>?): JsonObject {
  return buildJsonObject {
    put("type", type)
    put("url", url)
    put("headers", buildJsonObject {
      if (projectBasePath != null) {
        put(IJ_MCP_SERVER_PROJECT_PATH, projectBasePath)
      }
      if (authToken != null) {
        put(authToken.first, authToken.second)
      }
    })
  }
}

/**
 * Creates a JSON configuration entry for an MCP server that uses stdio transport based on createStdioMcpServerCommandLine()
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
