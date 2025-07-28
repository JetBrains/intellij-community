package com.intellij.mcpserver.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.mcpserver.*
import com.intellij.mcpserver.impl.util.network.*
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.application
import com.intellij.util.asDisposable
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException


private val logger = logger<McpServerService>()
private val structuredToolOutputEnabled get() = Registry.`is`("mcp.server.structured.tool.output")

@Service(Service.Level.APP)
class McpServerService(val cs: CoroutineScope) {
  companion object {
    fun getInstance(): McpServerService = service()
    suspend fun getInstanceAsync(): McpServerService = serviceAsync()
  }

  private val server = MutableStateFlow(startServerIfEnabled())
  @OptIn(ExperimentalAtomicApi::class)
  private val callId = AtomicInteger(0)

  val isRunning: Boolean
    get() = server.value != null

  val serverSseUrl: String
    get() = "http://127.0.0.1:${port}/sse"

  fun start() {
    McpServerSettings.getInstance().state.enableMcpServer = true
    settingsChanged(true)
  }

  fun stop() {
    McpServerSettings.getInstance().state.enableMcpServer = false
    settingsChanged(false)
  }

  val port: Int
    get() = (server.value ?: error("MCP Server is not enabled")).engineConfig.connectors.first().port

  internal fun settingsChanged(enabled: Boolean) {
    server.update { currentServer ->
      if (!enabled) {
        // stop old
        currentServer?.stop()
        return@update null
      }
      else {
        // reuse old or start new
        return@update currentServer ?: startServer()
      }
    }
  }

  class MyProjectListener: ProjectActivity {
    override suspend fun execute(project: Project) {
      // TODO: consider start on app startup
      serviceAsync<McpServerService>() // initialize service
    }
  }

  private fun startServerIfEnabled(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? {
    if (!McpServerSettings.getInstance().state.enableMcpServer) return null
    return startServer()
  }

  private fun startServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val serverSavedPort = McpServerSettings.getInstance().state.mcpServerPort
    val freePort = findFirstFreePort(serverSavedPort)
    McpServerSettings.getInstance().state.mcpServerPort = freePort

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

    return cs.embeddedServer(CIO, host = "127.0.0.1", port = freePort) {
      installHostValidation()
      installHttpRequestPropagation()
      mcpPatched {
        // this is added because now Kotlin MCP client doesn't support header adjusting for each request, only for initial one, see McpStdioRunner
        val projectPath = call.request.headers[IJ_MCP_SERVER_PROJECT_PATH]
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
        launch {
          var previousTools: List<McpTool>? = null
          mcpTools.collectLatest { updatedTools ->
            previousTools?.forEach { previousTool ->
              mcpServer.removeTool(previousTool.descriptor.name)
            }
            mcpServer.addTools(updatedTools.map { it.mcpToolToRegisteredTool(mcpServer, projectPath) })
            previousTools = updatedTools
          }
        }
        return@mcpPatched mcpServer
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
  }

private fun McpTool.mcpToolToRegisteredTool(server: Server, projectPathFromInitialRequest: String?): RegisteredTool {
  val tool = toSdkTool()
  return RegisteredTool(tool) { request ->
    val httpRequest = currentCoroutineContext().httpRequestOrNull
    val projectPath = httpRequest?.headers?.get(IJ_MCP_SERVER_PROJECT_PATH) ?: (request._meta[IJ_MCP_SERVER_PROJECT_PATH] as? JsonPrimitive)?.content ?: projectPathFromInitialRequest
    val project = if (!projectPath.isNullOrBlank()) {
      ProjectManager.getInstance().openProjects.find { it.basePath == projectPath }
      }
      else {
        null
      }

      val vfsEvent = CopyOnWriteArrayList<VFileEvent>()
      val initialDocumentContents = ConcurrentHashMap<Document, String>()
      val clientVersion = server.clientVersion ?: Implementation("Unknown MCP client", "Unknown version")

      val additionalData = McpCallInfo(
        callId = callId.getAndAdd(1),
        clientInfo = ClientInfo(clientVersion.name, clientVersion.version),
        project = project,
        mcpToolDescriptor = descriptor,
        rawArguments = request.arguments,
        meta = request._meta
      )

      val callResult = coroutineScope {

        VirtualFileManager.getInstance().addAsyncFileListener(this, AsyncFileListener { events ->
          val inHandlerInfo = currentThreadContext().mcpCallInfoOrNull
          if (inHandlerInfo != null && inHandlerInfo.callId == additionalData.callId) {
            logger.trace { "VFS changes detected for call: $inHandlerInfo" }
            vfsEvent.addAll(events)
          }
          // probably we have to read initial contents here
          // see comment below near `is VFileContentChangeEvent`
          return@AsyncFileListener object : AsyncFileListener.ChangeApplier {}
        })

        val documentListener = object : DocumentListener {
          // record content before any change
          override fun beforeDocumentChange(event: DocumentEvent) {
            val inHandlerInfo = currentThreadContext().mcpCallInfoOrNull
            if (inHandlerInfo != null && inHandlerInfo.callId == additionalData.callId) {
              logger.trace { "Document changes detected for call: $inHandlerInfo" }
              initialDocumentContents.computeIfAbsent(event.document) { event.document.text }
            }
          }
        }

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this.asDisposable())

        withContext(
          McpCallAdditionalDataElement(additionalData)
        ) {
          val sideEffectEvents = mutableListOf<McpToolSideEffectEvent>()
          @Suppress("IncorrectCancellationExceptionHandling")
          try {
            application.messageBus.syncPublisher(ToolCallListener.TOPIC).beforeMcpToolCall(this@mcpToolToRegisteredTool.descriptor, additionalData)

            logger.trace { "Start calling tool '${this@mcpToolToRegisteredTool.descriptor.name}'. Arguments: ${request.arguments}" }

            val result = this@mcpToolToRegisteredTool.call(request.arguments)

            logger.trace { "Tool call successful '${this@mcpToolToRegisteredTool.descriptor.name}'. Result: ${result.content.joinToString("\n") { it.toString() }}" }
            try {
              val processedChangedFiles = mutableSetOf<VirtualFile>()

              for ((doc, oldContent) in initialDocumentContents) {
                val virtualFile = FileDocumentManager.getInstance().getFile(doc) ?: continue
                val newContent = readAction { doc.text }
                sideEffectEvents.add(FileContentChangeEvent(virtualFile, oldContent, newContent))
                processedChangedFiles.add(virtualFile)
              }

              for (event in vfsEvent) {
                when (event) {
                  is VFileMoveEvent -> {
                    sideEffectEvents.add(FileMovedEvent(event.file, event.oldParent, event.newParent))
                  }
                  is VFileCreateEvent -> {
                    val virtualFile = event.file ?: continue
                    val newContent = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                    sideEffectEvents.add(FileCreatedEvent(virtualFile, newContent))
                  }
                  is VFileDeleteEvent -> {
                    val virtualFile = event.file
                    val document = readAction { FileDocumentManager.getInstance().getDocument(virtualFile) } ?: continue
                    val oldContent = initialDocumentContents[document]
                    sideEffectEvents.add(FileDeletedEvent(virtualFile, oldContent))
                  }
                  is VFileCopyEvent -> {
                    val createdFile = event.findCreatedFile() ?: continue
                    val newContent = readAction { FileDocumentManager.getInstance().getDocument(createdFile)?.text } ?: continue
                    sideEffectEvents.add(FileCreatedEvent(createdFile, newContent))
                  }
                  is VFileContentChangeEvent -> {
                    // reported in documents loop
                    if (processedChangedFiles.contains(event.file)) continue
                    val virtualFile = event.file
                    val newContent = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                    // Important: there may be a case when file is changed via low level change (like File.replaceText).
                    // in this case we don't track the old content, because it may be heavy, it requires loading the file in
                    // AsyncFileListener above and decoding with encoding etc. The file can be binary etc.
                    sideEffectEvents.add(FileContentChangeEvent(virtualFile, oldContent = null, newContent = newContent))
                  }
                }
              }

            }
            catch (ce: CancellationException) {
              throw ce
            }
            catch (t: Throwable) {
              logger.error("Failed to process changed documents after calling MCP tool ${this@mcpToolToRegisteredTool.descriptor.name}", t)
            }
            application.messageBus.syncPublisher(ToolCallListener.TOPIC).afterMcpToolCall(this@mcpToolToRegisteredTool.descriptor, sideEffectEvents, null, additionalData)
            result
          }
          catch (ce: CancellationException) {
            val message = "MCP tool call has been cancelled: ${ce.message}"
            logger.traceThrowable { CancellationException(message, ce) }
            application.messageBus.syncPublisher(ToolCallListener.TOPIC).afterMcpToolCall(this@mcpToolToRegisteredTool.descriptor, sideEffectEvents, ce, additionalData)
            McpToolCallResult.error(message)
          }
          catch (mcpException: McpExpectedError) {
            logger.traceThrowable { mcpException }
            application.messageBus.syncPublisher(ToolCallListener.TOPIC).afterMcpToolCall(this@mcpToolToRegisteredTool.descriptor, sideEffectEvents, mcpException, additionalData)
            McpToolCallResult.error(mcpException.mcpErrorText)
          }
          catch (t: Throwable) {
            val errorMessage = "MCP tool call has been failed: ${t.message}"
            logger.error(t)
            application.messageBus.syncPublisher(ToolCallListener.TOPIC).afterMcpToolCall(this@mcpToolToRegisteredTool.descriptor, sideEffectEvents, t, additionalData)
            McpToolCallResult.error(errorMessage)
          }
          finally {
            McpServerCounterUsagesCollector.reportMcpCall(descriptor)
          }
        }
    }

    val contents = callResult.content.map { content ->
      when (content) {
        is McpToolCallResultContent.Text -> TextContent(content.text)
      }
    }
    val structuredContent = if (structuredToolOutputEnabled) callResult.structuredContent else null
    return@RegisteredTool CallToolResult(content = contents, structuredContent = structuredContent, callResult.isError)}
  }
}

private fun McpTool.toSdkTool(): Tool {
  val outputSchema = if (structuredToolOutputEnabled) {
    descriptor.outputSchema?.let {
      Tool.Output(
        it.propertiesSchema,
        it.requiredProperties.toList())
    }
  }
  else null
  val tool = Tool(name = descriptor.name,
                  description = descriptor.description,
                  inputSchema = Tool.Input(
                    properties = descriptor.inputSchema.propertiesSchema,
                    required = descriptor.inputSchema.requiredProperties.toList()),
                  outputSchema = outputSchema,
                  annotations = null)
  return tool
}