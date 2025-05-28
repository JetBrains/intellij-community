// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.ide.RestService
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class MCPService : RestService() {
    @Service
    class ScopeHolder(val cs: CoroutineScope)

    private val serviceName = "mcp"
    private val json = Json {
      prettyPrint = true
      ignoreUnknownKeys = true
      classDiscriminator = "schemaType"
    }

    override fun getServiceName(): String = serviceName

    override fun getMaxRequestsPerMinute(): Int = Int.MAX_VALUE

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        val path = urlDecoder.path().split(serviceName).last().trimStart('/')
        val project = getLastFocusedOrOpenedProject() ?: return null
        val tools = McpToolManager.getAllTools()

        val result = when (path) {
          "list_tools" -> handleListTools(tools)
          else -> handleToolExecution(path, tools, request, project)
        }
        sendJson(result, request, context)

        return null
    }

    private fun handleListTools(
        tools: List<AbstractMcpTool<*>>
    ): List<ToolInfo> {
        return tools.map { tool ->
            ToolInfo(
                name = tool.name,
                description = tool.description,
                inputSchema = schemaFromDataClass(tool.argKlass)
            )
        }
    }

    private fun handleToolExecution(
        path: String,
        tools: List<AbstractMcpTool<*>>,
        request: FullHttpRequest,
        project: Project
    ): Response {
        val tool = tools.find { it.name == path } ?: return Response(error = "Unknown tool: $path")

        val args = try {
          parseArgs(request, tool.serializer)
        } catch (e: Throwable) {
          val message = "Failed to parse arguments for tool $path: ${e.message}"
          logger<MCPService>().warn(message, e)
          return Response(error = message)
        }
        @Suppress("RAW_RUN_BLOCKING") val result = try {
          // TODO consider async request handling
          runBlocking {
            toolHandle(tool, project, args)
          }
        }
        catch (e: CancellationException) {
          Response(error = "Execution was cancelled by IDE: ${e.message}")
        }
        catch (e: Throwable) {
          logger<MCPService>().warn("Failed to execute tool $path", e)
          Response(error = "Failed to execute tool $path, message ${e.message}")
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendJson(data: Any, request: FullHttpRequest, context: ChannelHandlerContext) {
        val jsonString = when (data) {
            is List<*> -> json.encodeToString<List<ToolInfo>>(ListSerializer(ToolInfo.serializer()), data as List<ToolInfo>)
            is Response -> json.encodeToString<Response>(Response.serializer(), data)
            else -> throw IllegalArgumentException("Unsupported type for serialization")
        }
        val outputStream = BufferExposingByteArrayOutputStream()
        outputStream.write(jsonString.toByteArray(StandardCharsets.UTF_8))
        send(outputStream, request, context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parseArgs(request: FullHttpRequest, serializer: KSerializer<T>): T {
        val body = request.content().toString(StandardCharsets.UTF_8)
        if (body.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return NoArgs as T
        }
        return json.decodeFromString(serializer, body)
    }

    private suspend fun <Args : Any> toolHandle(tool: McpTool<Args>, project: Project, args: Any): Response {
        @Suppress("UNCHECKED_CAST")
        return tool.handle(project, args as Args)
    }

    override fun isMethodSupported(method: HttpMethod): Boolean =
        method === HttpMethod.GET || method === HttpMethod.POST

    private fun schemaFromDataClass(kClass: KClass<*>): JsonSchemaObject {
        if (kClass == NoArgs::class) return JsonSchemaObject(type = "object")

        val constructor = kClass.primaryConstructor
            ?: error("Class ${kClass.simpleName} must have a primary constructor")

        val properties = constructor.parameters.mapNotNull { param ->
            param.name?.let { name ->
                name to when (param.type.classifier) {
                    String::class -> PropertySchema("string")
                    Int::class, Long::class, Double::class, Float::class -> PropertySchema("number")
                    Boolean::class -> PropertySchema("boolean")
                    List::class -> PropertySchema("array")
                    else -> PropertySchema("object")
                }
            }
        }.toMap()

        val required = constructor.parameters
            .filter { !it.type.isMarkedNullable }
            .mapNotNull { it.name }

        return JsonSchemaObject(
            type = "object",
            properties = properties,
            required = required
        )
    }
}

@Serializable
object NoArgs

@Serializable
data class ToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonSchemaObject
)

@Serializable
data class Response(
    val status: String? = null,
    val error: String? = null
)

@Serializable
data class JsonSchemaObject(
    val type: String,
    val properties: Map<String, PropertySchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val items: PropertySchema? = null
)

@Serializable
data class PropertySchema(
    val type: String
)
