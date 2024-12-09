package org.jetbrains.ide.mcp

import com.google.gson.*
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.io.createParentDirectories
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import java.nio.charset.StandardCharsets
import kotlin.io.path.createFile
import kotlin.io.path.writeText
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
            CreateNewFileWithTextTool()
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

// tools

class GetCurrentFileTextTool : McpTool<NoArgs> {
    override val name: String = "get_current_file_text"
    override val description: String = "Get the current contents of the file in JetBrains IDE"
    override val argKlass: KClass<NoArgs> = NoArgs::class

    override fun handle(project: Project, args: NoArgs): Response {
        val text = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.document?.text
        }
        return Response(text)
    }
}

class GetCurrentFilePathTool : McpTool<NoArgs> {
    override val name: String = "get_current_file_path"
    override val description: String = "Get the current file path in JetBrains IDE"
    override val argKlass: KClass<NoArgs> = NoArgs::class

    override fun handle(project: Project, args: NoArgs): Response {
        val path = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.virtualFile?.path
        }
        return Response(path)
    }
}

class GetSelectedTextTool : McpTool<NoArgs> {
    override val name: String = "get_selected_text"
    override val description: String = "Get the currently selected text in the JetBrains IDE"
    override val argKlass: KClass<NoArgs> = NoArgs::class

    override fun handle(project: Project, args: NoArgs): Response {
        val text = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.selectionModel?.selectedText
        }
        return Response(text)
    }
}

data class ReplaceSelectedTextArgs(val text: String)
class ReplaceSelectedTextTool : McpTool<ReplaceSelectedTextArgs> {
    override val name: String = "replace_selected_text"
    override val description: String = "Replace the currently selected text in the JetBrains IDE with new text"
    override val argKlass: KClass<ReplaceSelectedTextArgs> = ReplaceSelectedTextArgs::class

    override fun handle(project: Project, args: ReplaceSelectedTextArgs): Response {
        runInEdt {
            runWriteCommandAction(project, "Replace Selected Text", null, {
                val editor = getInstance(project).selectedTextEditor
                val document = editor?.document
                val selectionModel = editor?.selectionModel
                if (document != null && selectionModel != null && selectionModel.hasSelection()) {
                    document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, args.text)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            })
        }
        return Response("ok")
    }
}

data class ReplaceCurrentFileTextArgs(val text: String)
class ReplaceCurrentFileTextTool : McpTool<ReplaceCurrentFileTextArgs> {
    override val name: String = "replace_current_file_text"
    override val description: String = "Replace the entire contents of the current file in JetBrains IDE with new text"
    override val argKlass: KClass<ReplaceCurrentFileTextArgs> = ReplaceCurrentFileTextArgs::class

    override fun handle(project: Project, args: ReplaceCurrentFileTextArgs): Response {
        runInEdt {
            runWriteCommandAction(project, "Replace File Text", null, {
                val editor = getInstance(project).selectedTextEditor
                val document = editor?.document
                document?.setText(args.text)
            })
        }
        return Response("ok")
    }
}

data class CreateNewFileWithTextArgs(val absolutePath: String, val text: String)

class CreateNewFileWithTextTool : McpTool<CreateNewFileWithTextArgs> {
    override val name: String = "create_new_file_with_text"
    override val description: String = "Create a new file inside the project with specified text in JetBrains IDE"
    override val argKlass: KClass<CreateNewFileWithTextArgs> = CreateNewFileWithTextArgs::class

    override fun handle(project: Project, args: CreateNewFileWithTextArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response("can't find project dir")

        val path = kotlin.io.path.Path(args.absolutePath)
        return if (path.startsWith(projectDir)) {
            path.createParentDirectories().createFile().writeText(args.text)
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            Response("ok")
        }
        else {
            Response(error = "file is outside of the project")
        }
    }
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