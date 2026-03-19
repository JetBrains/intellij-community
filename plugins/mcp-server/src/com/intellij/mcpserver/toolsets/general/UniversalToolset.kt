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
  @McpDescription("""
        |Universal tool executor that can invoke any IDE MCP tool dynamically.
        |Use this tool to execute IDE tools by passing the tool name and arguments as a command-line string.
        |Format: "tool_name --param1 value1 --param2 value2"
        |Example: "reformat_file --path ./myfile.kt"
        |
        |Help:
        |  tool_name --help Show details for specific tool
  """)
  //         |  --help           Show list of all available tools
  suspend fun execute_tool(
    @McpDescription("Command-line string with tool name and arguments, e.g., 'reformat_file --path ./myfile.kt', or '--help' for help")
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

    // Check for --help flag
    //if (parts[0] == "--help") {
    //  return formatAllToolsHelp(allTools)
    //}

    val toolName = parts[0]
    val args = parts.drop(1)

    // Check for tool-specific help
    if (args.isNotEmpty() && args[0] == "--help") {
      return formatToolHelp(allTools, toolName)
    }


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

  private fun formatAllToolsHelp(allTools: List<com.intellij.mcpserver.McpTool>): String {
    val sortedTools = allTools.sortedBy { it.descriptor.name }
    
    val result = buildString {
      appendLine("Available MCP Tools (${sortedTools.size} total):")
      appendLine()
      appendLine("Usage: execute_tool --command \"tool_name --param1 value1 --param2 value2\"")
      appendLine("       execute_tool --command \"tool_name --help\"  (show tool details)")
      appendLine()
      appendLine("Tools:")
      
      for (tool in sortedTools) {
        val name = tool.descriptor.name
        val description = tool.descriptor.description.lines().firstOrNull()?.trim() ?: ""
        val shortDesc = if (description.length > 80) description.take(77) + "..." else description
        appendLine("  • $name")
        if (shortDesc.isNotEmpty()) {
          appendLine("    $shortDesc")
        }
      }
    }
    
    return result
  }

  private fun formatToolHelp(allTools: List<com.intellij.mcpserver.McpTool>, toolName: String): String {
    val tool = allTools.find { it.descriptor.name == toolName }
                ?: mcpFail("Tool '$toolName' not found. Use '--help' to see all available tools.")
    
    val descriptor = tool.descriptor
    val schema = descriptor.inputSchema
    
    val result = buildString {
      appendLine("Tool: ${descriptor.name}")
      appendLine()
      appendLine("Description:")
      val descLines = descriptor.description.lines().map { it.trim() }.filter { it.isNotEmpty() }
      for (line in descLines) {
        appendLine("  $line")
      }
      appendLine()
      
      if (schema.propertiesSchema.isEmpty()) {
        appendLine("Parameters: none")
      } else {
        appendLine("Parameters:")
        
        for ((paramName, paramSchemaElement) in schema.propertiesSchema) {
          val paramSchema = paramSchemaElement as? JsonObject ?: continue
          val type = (paramSchema["type"] as? JsonPrimitive)?.content ?: "string"
          val description = (paramSchema["description"] as? JsonPrimitive)?.content ?: ""
          val isRequired = schema.requiredProperties.contains(paramName)
          val requiredMark = if (isRequired) " [required]" else " [optional]"
          
          appendLine("  --$paramName <$type>$requiredMark")
          if (description.isNotEmpty()) {
            val descLines = description.lines().map { it.trim() }.filter { it.isNotEmpty() }
            for (line in descLines) {
              appendLine("      $line")
            }
          }
          
          // Show enum values if available
          val enumValues = paramSchema["enum"]
          if (enumValues != null) {
            appendLine("      Allowed values: $enumValues")
          }
        }
      }
      
      appendLine()
      appendLine("Example:")
      val exampleParams = schema.propertiesSchema.keys.take(2).joinToString(" ") { "--$it <value>" }
      appendLine("  execute_tool --command \"${descriptor.name} $exampleParams\"")
    }
    
    return result
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
