@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpCallInfo
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.reportToolActivity
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class UniversalToolset : McpToolset {
  @McpTool
  @McpDescription("""Universal tool executor that can invoke specific IDE MCP tool dynamically.""")
  suspend fun execute_tool(
    @McpDescription("Command-line string with tool name and arguments")
    command: String,
  ): String {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.executing.universal.tool", command))

    // Parse command line
    val parts = ParametersListUtil.parse(command, false, true)
    if (parts.isEmpty()) {
      mcpFail("Command is empty")
    }

    val sessionHandler = currentCoroutineContext().mcpCallInfo.sessionHandler ?: mcpFail("Session handler not available")
    val routerToolsProvider = sessionHandler.routerToolsProvider ?: mcpFail("Router tools provider not available")

    val allTools = routerToolsProvider.mcpTools.value

    val toolName = parts[0]
    val args = parts.drop(1)

    val tool = allTools.find { it.descriptor.name == toolName }
                ?: mcpFail("Tool '$toolName' not found. Available tools: ${allTools.map { it.descriptor.name }.sorted().joinToString(", ")}")

    // Parse arguments into JSON object
    val jsonArgs = parseArgsToJson(args, tool.descriptor.inputSchema.propertiesSchema)

    // Validate required parameters
    val missingRequired = tool.descriptor.inputSchema.requiredProperties
      .filter { !jsonArgs.containsKey(it) }
    if (missingRequired.isNotEmpty()) {
      mcpFail("Missing required parameters: ${missingRequired.joinToString(", ")}")
    }

    // Call the tool
    val result = tool.call(jsonArgs)
    
    if (result.isError) {
      mcpFail("Tool execution failed: ${result}")
    }
    
    return result.toString()
  }

  private fun parseArgsToJson(args: List<String>, propertiesSchema: JsonObject): JsonObject {
    val result = mutableMapOf<String, JsonPrimitive>()
    var i = 0
    
    while (i < args.size) {
      val arg = args[i]
      
      if (arg.startsWith("--")) {
        val paramName = arg.substring(2)
        
        if (i + 1 >= args.size) {
          mcpFail("Parameter '$paramName' requires a value")
        }
        
        val value = args[i + 1]
        
        // Convert value to appropriate JSON type based on schema
        val jsonValue = convertToJsonValue(paramName, value, propertiesSchema)
        result[paramName] = jsonValue
        
        i += 2
      } else {
        mcpFail("Invalid argument format: '$arg'. Expected '--paramName value' format")
      }
    }
    
    return buildJsonObject {
      result.forEach { (key, value) ->
        put(key, value)
      }
    }
  }

  private fun convertToJsonValue(paramName: String, value: String, propertiesSchema: JsonObject): JsonPrimitive {
    val paramSchema = propertiesSchema[paramName] as? JsonObject
    val type = (paramSchema?.get("type") as? JsonPrimitive)?.content ?: "string"
    
    return when (type) {
      "boolean" -> JsonPrimitive(value.toBoolean())
      "integer", "number" -> {
        val numValue = value.toLongOrNull()
        if (numValue != null) {
          JsonPrimitive(numValue)
        } else {
          JsonPrimitive(value.toDouble())
        }
      }
      else -> JsonPrimitive(value)
    }
  }
}
