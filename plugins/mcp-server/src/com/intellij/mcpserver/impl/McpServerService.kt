package com.intellij.mcpserver.impl

import com.intellij.mcpserver.*
import com.intellij.mcpserver.impl.util.network.findFirstFreePort
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
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException


private val logger = logger<McpServerService>()

@Service(Service.Level.APP)
class McpServerService(val cs: CoroutineScope) {
  companion object {
    fun getInstance(): McpServerService = service()
    suspend fun getInstanceAsync(): McpServerService = serviceAsync()
  }

  private val server = MutableStateFlow(startServerIfEnabled())

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

private fun McpTool.mcpToolToRegisteredTool(): RegisteredTool {
  val tool = Tool(name = descriptor.name,
                  description = descriptor.description,
                  inputSchema = Tool.Input(
                    properties = descriptor.inputSchema.properties,
                    required = descriptor.inputSchema.requiredParameters.toList()))
  return RegisteredTool(tool) { request ->
    val projectPath = (request._meta[IJ_MCP_SERVER_PROJECT_PATH] as? JsonPrimitive)?.content
    val project = if (!projectPath.isNullOrBlank()) {
      ProjectManager.getInstance().openProjects.find { it.basePath == projectPath }
      }
      else {
        null
      }

      val vfsEvent = CopyOnWriteArrayList<VFileEvent>()
      val initialDocumentContents = ConcurrentHashMap<Document, String>()

      val callResult = coroutineScope {

        VirtualFileManager.getInstance().addAsyncFileListener(this, AsyncFileListener { events ->
          vfsEvent.addAll(events)
          // probably we have to read initial contents here
          // see comment below near `is VFileContentChangeEvent`
          return@AsyncFileListener object : AsyncFileListener.ChangeApplier {}
        })

        val documentListener = object : DocumentListener {
          // record content before any change
          override fun beforeDocumentChange(event: DocumentEvent) {
            initialDocumentContents.computeIfAbsent(event.document) { event.document.text }
          }
        }

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this.asDisposable())

        @Suppress("IncorrectCancellationExceptionHandling")
        try {
          application.messageBus.syncPublisher(ToolCallListener.TOPIC).beforeMcpToolCall(this@mcpToolToRegisteredTool.descriptor)

          logger.trace { "Start calling tool '${this@mcpToolToRegisteredTool.descriptor.name}'. Arguments: ${request.arguments}" }
          val result = withContext(ProjectContextElement(project)) {
            this@mcpToolToRegisteredTool.call(request.arguments)
          }

          logger.trace { "Tool call successful '${this@mcpToolToRegisteredTool.descriptor.name}'. Result: ${result.content.joinToString("\n") { it.toString() }}" }
          try {
            val processedChangedFiles = mutableSetOf<VirtualFile>()
            val events = mutableListOf<McpToolSideEffectEvent>()

            for ((doc, oldContent) in initialDocumentContents) {
              val virtualFile = FileDocumentManager.getInstance().getFile(doc) ?: continue
              val newContent = readAction { doc.text }
              events.add(FileContentChangeEvent(virtualFile, oldContent, newContent))
              processedChangedFiles.add(virtualFile)
            }

            for (event in vfsEvent) {
              when (event) {
                is VFileMoveEvent -> {
                  events.add(FileMovedEvent(event.file, event.oldParent, event.newParent))
                }
                is VFileCreateEvent -> {
                  val virtualFile = event.file ?: continue
                  val newContent = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                  events.add(FileCreatedEvent(virtualFile, newContent))
                }
                is VFileDeleteEvent -> {
                  val virtualFile = event.file
                  val document = readAction { FileDocumentManager.getInstance().getDocument(virtualFile) } ?: continue
                  val oldContent = initialDocumentContents[document]
                  events.add(FileDeletedEvent(virtualFile, oldContent))
                }
                is VFileCopyEvent -> {
                  val createdFile = event.findCreatedFile() ?: continue
                  val newContent = readAction { FileDocumentManager.getInstance().getDocument(createdFile)?.text } ?: continue
                  events.add(FileCreatedEvent(createdFile, newContent))
                }
                is VFileContentChangeEvent -> {
                  // reported in documents loop
                  if (processedChangedFiles.contains(event.file)) continue
                  val virtualFile = event.file
                  val newContent = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                  // Important: there may be a case when file is changed via low level change (like File.replaceText).
                  // in this case we don't track the old content, because it may be heavy, it requires loading the file in
                  // AsyncFileListener above and decoding with encoding etc. The file can be binary etc.
                  events.add(FileContentChangeEvent(virtualFile, oldContent = null, newContent = newContent))
                }
              }
            }

            application.messageBus.syncPublisher(ToolCallListener.TOPIC).afterMcpToolCall(this@mcpToolToRegisteredTool.descriptor, events)
          }
          catch (ce: CancellationException) {
            throw ce
          }
          catch (t: Throwable) {
            logger.error("Failed to process changed documents after calling MCP tool ${this@mcpToolToRegisteredTool.descriptor.name}", t)
          }
          result
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
          logger.error(t)
          McpToolCallResult.error(errorMessage)
        }
        finally {
          McpServerCounterUsagesCollector.reportMcpCall(descriptor)
        }
    }

    val contents = callResult.content.map { content ->
      when (content) {
        is McpToolCallResultContent.Text -> TextContent(content.text)
      }
    }
    return@RegisteredTool CallToolResult(content = contents, callResult.isError)}
  }
}

