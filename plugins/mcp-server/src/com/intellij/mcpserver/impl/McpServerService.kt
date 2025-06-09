package com.intellij.mcpserver.impl

import com.intellij.mcpserver.*
import com.intellij.mcpserver.impl.util.network.findFirstFreePort
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.ide.RestService.Companion.getLastFocusedOrOpenedProject
import kotlin.coroutines.cancellation.CancellationException

const val DEFAULT_MCP_PORT: Int = 64342

private val logger = logger<McpServerService>()

@Service(Service.Level.APP)
class McpServerService(val cs: CoroutineScope) {
  companion object {
    fun getInstance(): McpServerService = service()
  }

  private val server = MutableStateFlow(startServerIfEnabled())

  val isRunning: Boolean
    get() = server.value != null

  fun start() {
    McpServerSettings.getInstance().state.enableMcpServer = true
    settingsChanged()
  }

  fun stop() {
    McpServerSettings.getInstance().state.enableMcpServer = false
    settingsChanged()
  }

  val port: Int
    get() = (server.value ?: error("MCP Server is not enabled")).engineConfig.connectors.first().port

  internal fun settingsChanged() {
    val enabled = McpServerSettings.getInstance().state.enableMcpServer
    server.update { currentServer ->
      if (!enabled) {
        // stop old
        currentServer?.stop()
        return@update null
      }
      else {
        // reuse old or start new
        return@update currentServer ?: startServerIfEnabled()
      }
    }
  }

  class MyProjectListener: ProjectActivity {
    override suspend fun execute(project: Project) {
      // TODO: consider start on app startup
      project.serviceAsync<McpServerService>() // initialize service
    }
  }

  private fun startServerIfEnabled(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? {
    if (!McpServerSettings.getInstance().state.enableMcpServer) return null

    val freePort = findFirstFreePort(DEFAULT_MCP_PORT)

    val mcpTools = MutableStateFlow(getMcpTools())

    McpToolsProvider.EP.addExtensionPointListener(cs, object : ExtensionPointListener<McpToolsProvider> {
      override fun extensionAdded(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        mcpTools.tryEmit(getMcpTools())
      }

      override fun extensionRemoved(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        mcpTools.tryEmit(getMcpTools())
      }
    })

    McpToolset.EP.addExtensionPointListener(cs, object : ExtensionPointListener<McpToolset> {
      override fun extensionAdded(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        mcpTools.tryEmit(getMcpTools())
      }

      override fun extensionRemoved(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        mcpTools.tryEmit(getMcpTools())
      }
    })

    val mcpServer = Server(
      Implementation(
        name = "${ApplicationNamesInfo.getInstance().fullProductName} MCP Server",
        version = ApplicationInfo.getInstance().fullVersion
      ),
      ServerOptions(
        capabilities = ServerCapabilities(
          //prompts = ServerCapabilities.Prompts(listChanged = true),
          //resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
          tools = ServerCapabilities.Tools(listChanged = true),
        )
      )
    )
    cs.launch {
      var previousTools: List<RegisteredTool>? = null
      mcpTools.collectLatest { updatedTools ->
        previousTools?.forEach { previousTool ->
          mcpServer.removeTool(previousTool.tool.name)
        }
        mcpServer.addTools(updatedTools)
        previousTools = updatedTools
      }
    }


    return cs.embeddedServer(CIO, host = "127.0.0.1", port = freePort) {
      mcp {
        return@mcp mcpServer
      }
    }.start(wait = false)
  }

  private fun getMcpTools() = McpToolsProvider.EP.extensionList.flatMap {
    try {
      it.getTools()
    }
    catch (e: Exception) {
      logger.error("Cannot load tools for $it", e)
      emptyList()
    }
  }.map { it.mcpToolToRegisteredTool() }
}

private fun McpTool.mcpToolToRegisteredTool(): RegisteredTool {
  val tool = Tool(name = descriptor.name,
                  description = descriptor.description,
                  inputSchema = Tool.Input(
                    properties = descriptor.inputSchema.properties,
                    required = descriptor.inputSchema.requiredParameters.toList()))
  return RegisteredTool(tool) { request ->
    val projectPath = (request._meta[IJ_MCP_SERVER_PROJECT_PATH] as? JsonPrimitive)?.content
    val project = if (!projectPath.isNullOrBlank()) {
      ProjectManager.getInstance().openProjects.find { it.basePath == projectPath } ?: getLastFocusedOrOpenedProject()
    }
    else {
      getLastFocusedOrOpenedProject()
    }

    @Suppress("IncorrectCancellationExceptionHandling")
    val callResult = try {
      withContext(ProjectContextElement(project)) {
        call(request.arguments)
      }
    }
    catch (ce: CancellationException) {
      val message = "MCP tool call has been cancelled: ${ce.message}"
      logger.traceThrowable { CancellationException(message, ce) }
      McpToolCallResult.error(message)
    }
    catch (mcpException: McpExpectedError) {
      logger.traceThrowable { mcpException }
      McpToolCallResult.error(mcpException.mcpErrorText)
    }
    catch (t: Throwable) {
      val errorMessage = "MCP tool call has been failed: ${t.message}"
      logger.traceThrowable { Exception(errorMessage, t) }
      McpToolCallResult.error(errorMessage)
    }
    val contents = callResult.content.map { content ->
      when (content) {
        is McpToolCallResultContent.Text -> TextContent(content.text)
      }
    }
    return@RegisteredTool CallToolResult(content = contents, callResult.isError)
  }
}