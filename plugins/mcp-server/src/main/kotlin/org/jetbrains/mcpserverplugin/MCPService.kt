package org.jetbrains.ide.mcp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import org.jetbrains.ide.RestService
import org.jetbrains.mcpserverplugin.*
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType

interface McpTool<Args : Any> {
    val name: String
    val description: String
    fun handle(project: Project, args: Args): Response
}

abstract class AbstractMcpTool<Args : Any> : McpTool<Args> {
    val argKlass: KClass<Args> by lazy {
        val supertype = this::class.supertypes.find {
            it.classifier == AbstractMcpTool::class
        } ?: error("Cannot find McpTool supertype")

        val typeArgument = supertype.arguments.first().type
            ?: error("Cannot find type argument for McpTool")

        @Suppress("UNCHECKED_CAST")
        typeArgument.classifier as KClass<Args>
    }
}

class McpToolManager {
    companion object {
        private val EP_NAME = ExtensionPointName<AbstractMcpTool<*>>("com.intellij.mcpServer.mcpTool")

        fun getAllTools(): List<AbstractMcpTool<*>> {
            return buildList {
                // Add built-in tools
                addAll(getBuiltInTools())
                // Add extension-provided tools
                addAll(EP_NAME.extensionList)
            }
        }

        private fun getBuiltInTools(): List<AbstractMcpTool<*>> = listOf(
            GetCurrentFileTextTool(),
            GetCurrentFilePathTool(),
            GetSelectedTextTool(),
            ReplaceSelectedTextTool(),
            ReplaceCurrentFileTextTool(),
            CreateNewFileWithTextTool(),
            FindFilesByNameSubstring(),
            GetFileTextByPathTool(),
            GetVcsStatusTool(),
            ToggleBreakpointTool(),
            GetBreakpointsTool()
        )
    }
}

class MCPService : RestService() {
    private val serviceName = "mcp"
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "schemaType"
    }

    override fun getServiceName(): String = serviceName

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        val path = urlDecoder.path().split(serviceName).last().trimStart('/')
        val project = getLastFocusedOrOpenedProject() ?: return null
        val tools = McpToolManager.getAllTools()

        when (path) {
            "list_tools" -> handleListTools(tools, request, context)
            else -> handleToolExecution(path, tools, request, context, project)
        }
        return null
    }

    private fun handleListTools(
        tools: List<AbstractMcpTool<*>>,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ) {
        val toolsList = tools.map { tool ->
            ToolInfo(
                name = tool.name,
                description = tool.description,
                inputSchema = schemaFromDataClass(tool.argKlass)
            )
        }
        sendJson(toolsList, request, context)
    }

    private fun handleToolExecution(
        path: String,
        tools: List<AbstractMcpTool<*>>,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
        project: Project
    ) {
        val tool = tools.find { it.name == path } ?: run {
            sendJson(Response(error = "Unknown tool: $path"), request, context)
            return
        }
        val args = parseArgs(request, tool.argKlass)
        val result = toolHandle(tool, project, args)
        sendJson(result, request, context)
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
    private fun <T : Any> parseArgs(request: FullHttpRequest, klass: KClass<T>): T {
        val body = request.content().toString(StandardCharsets.UTF_8)
        if (body.isEmpty()) {
            return NoArgs as T
        }
        return when (klass) {
            NoArgs::class -> NoArgs as T
            else -> {
                json.decodeFromString(serializer(klass.starProjectedType), body) as T
            }
        }
    }

    private fun <Args : Any> toolHandle(tool: McpTool<Args>, project: Project, args: Any): Response {
        @Suppress("UNCHECKED_CAST")
        return tool.handle(project, args as Args)
    }

    override fun isMethodSupported(method: HttpMethod): Boolean =
        method === HttpMethod.GET || method === HttpMethod.POST
}

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

@Serializable
object NoArgs

// Type conversion helper
fun schemaFromDataClass(kClass: KClass<*>): JsonSchemaObject {
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