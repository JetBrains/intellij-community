package org.jetbrains.ide.mcp

import com.google.gson.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import org.jetbrains.mcpserverplugin.*
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

class McpToolManager {
    companion object {
        private val EP_NAME = ExtensionPointName<McpTool<*>>("com.intellij.mcpServer.mcpTool")

        fun getAllTools(): List<McpTool<*>> {
            return buildList {
                // Add built-in tools
                addAll(getBuiltInTools())
                // Add extension-provided tools
                addAll(EP_NAME.extensionList)
            }
        }

        private fun getBuiltInTools(): List<McpTool<*>> = listOf(
            GetCurrentFileTextTool(),
            GetCurrentFilePathTool(),
            GetSelectedTextTool(),
            ReplaceSelectedTextTool(),
            ReplaceCurrentFileTextTool(),
            CreateNewFileWithTextTool(),
            FindFilesByNameSubstring(),
            GetFileTextByPathTool(),
            GetVcsStatusTool(),
            ToggleBreakpointTool()
        )
    }
}

data class Response(val status: String? = null, val error: String? = null)

internal class MCPService : RestService() {
    private val serviceName = "mcp"
    override fun getServiceName(): String = serviceName

    override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
        val path = urlDecoder.path().split(serviceName).last().trimStart('/')
        val project = getLastFocusedOrOpenedProject() ?: return null

        val tools = McpToolManager.getAllTools()

        if (path == "list_tools") {
            val json = generateToolsJson(tools)
            val outputStream = BufferExposingByteArrayOutputStream()
            outputStream.write(gson.toJson(json).toByteArray(StandardCharsets.UTF_8))
            send(outputStream, request, context)
            return null
        }

        val tool = tools.find { it.name == path } ?: run {
            sendText("error", "Unknown tool: $path", request, context)
            return null
        }

        val body = request.content().toString(StandardCharsets.UTF_8)
        val args = parseArgs(body, tool.argKlass) // Implement this according to schema
        val result = toolHandle(tool, project, args)
        sendResultAsJson(result, request, context)
        return null
    }

    override fun isMethodSupported(method: HttpMethod): Boolean = method === HttpMethod.GET || method === HttpMethod.POST

    private fun <Args : Any> toolHandle(tool: McpTool<Args>, project: Project, args: Any): Response {
        @Suppress("UNCHECKED_CAST")
        return tool.handle(project, args as Args)
    }

    private fun <Result> sendResultAsJson(result: Result, request: FullHttpRequest, context: ChannelHandlerContext) {
        val out = BufferExposingByteArrayOutputStream()
        createJsonWriter(out).use {
            it.beginObject()
            // Here you can customize how result is serialized; if result is a data class, you could use Gson directly.
            val gson = Gson()
            // We'll just dump the entire object as JSON directly:
            val jsonElement = gson.toJsonTree(result)
            for ((key, value) in jsonElement.asJsonObject.entrySet()) {
                it.name(key).jsonValue(value.toString())
            }
            it.endObject()
        }
        send(out, request, context)
    }

    private fun sendText(name: String, value: String?, request: FullHttpRequest, context: ChannelHandlerContext) {
        val out = BufferExposingByteArrayOutputStream()
        createJsonWriter(out).use {
            it.beginObject()
            it.name(name).value(value)
            it.endObject()
        }
        send(out, request, context)
    }

    private fun parseArgs(body: String, schema: KClass<*>): Any {
        if (body == "") return NoArgs
        // Implement argument parsing from body to the Args data class.
        // For example:
        return Gson().fromJson(body, schema.javaObjectType)
    }
}

sealed class JsonType {
    object StringType : JsonType()
    object NumberType : JsonType()
    object BooleanType : JsonType()
    data class ObjectType(
        val properties: Map<String, JsonType>,
        val required: List<String> = emptyList(),
    ) : JsonType()

    data class ArrayType(val items: JsonType) : JsonType()
}

interface McpTool<Args : Any> {
    val name: String
    val description: String
    val argKlass: KClass<*>

    // Modified to accept project context
    fun handle(project: Project, args: Args): Response
}

object NoArgs

// Utility: Infer JsonType from a Kotlin type
fun jsonTypeFromKType(kType: KType): JsonType {
    return when (val classifier = kType.classifier) {
        String::class -> JsonType.StringType
        Int::class, Long::class, Double::class, Float::class -> JsonType.NumberType
        Boolean::class -> JsonType.BooleanType
        else -> {
            if (classifier is KClass<*> && classifier.isData) {
                schemaFromDataClass(classifier)
            }
            else {
                JsonType.StringType
            }
        }
    }
}

// Build a schema from a data class using reflection
fun schemaFromDataClass(kClass: KClass<*>): JsonType.ObjectType {
    if (kClass == NoArgs::class) {
        return JsonType.ObjectType(emptyMap())
    }

    val primaryConstructor = kClass.primaryConstructor ?: error("Class ${kClass.simpleName} must have a primary constructor.")

    val props = mutableMapOf<String, JsonType>()
    val requiredParams = mutableListOf<String>()

    for (param in primaryConstructor.parameters) {
        val name = param.name ?: continue
        val jsonType = jsonTypeFromKType(param.type)
        props[name] = jsonType
        // Non-nullable fields are required
        if (!param.type.isMarkedNullable) {
            requiredParams += name
        }
    }

    return JsonType.ObjectType(properties = props, required = requiredParams)
}

// Convert JsonType to JsonObject with 'required'
fun JsonType.toJsonElementWithRequired(): JsonElement {
    return when (this) {
        is JsonType.StringType -> JsonObject().apply { addProperty("type", "string") }
        is JsonType.NumberType -> JsonObject().apply { addProperty("type", "number") }
        is JsonType.BooleanType -> JsonObject().apply { addProperty("type", "boolean") }
        is JsonType.ObjectType -> {
            val obj = JsonObject()
            obj.addProperty("type", "object")
            val props = JsonObject()
            properties.forEach { (key, value) ->
                props.add(key, value.toJsonElementWithRequired())
            }
            obj.add("properties", props)
            if (required.isNotEmpty()) {
                val arr = JsonArray()
                required.forEach { arr.add(it) }
                obj.add("required", arr)
            }
            obj
        }
        is JsonType.ArrayType -> {
            val obj = JsonObject()
            obj.addProperty("type", "array")
            obj.add("items", items.toJsonElementWithRequired())
            obj
        }
    }
}

fun generateToolsJson(tools: List<McpTool<*>>): JsonElement {
    val toolsArray = JsonArray()
    for (tool in tools) {
        val toolObj = JsonObject()
        toolObj.addProperty("name", tool.name)
        toolObj.addProperty("description", tool.description)
        toolObj.add("inputSchema", schemaFromDataClass(tool.argKlass).toJsonElementWithRequired())
        toolsArray.add(toolObj)
    }

    return toolsArray
}