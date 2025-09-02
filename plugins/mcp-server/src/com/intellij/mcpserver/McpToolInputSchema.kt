package com.intellij.mcpserver

import kotlinx.serialization.json.*

/**
 * Schema of the input parameter of an MCP tool.
 *
 * Schema may consists of several blocks:
 *  * properties - in fact a map of parameter names to their schemas
 *  * required - a set of names of required parameters
 *  * definitions - a map of type names to their schemas. It may be used when allowing definitions and references in a schema but now it's not supported. All types are inlined.
 *
 * Example:
 *
 * ``` json
 *   inputSchema: {
 *       type: 'object',
 *       properties: {
 *         messageType: {
 *           type: 'string',
 *           enum: messageTypes,
 *           description: 'Type of message to generate',
 *         },
 *         recipient: {
 *           type: 'string',
 *           description: 'Name of the person to address',
 *         },
 *         tone: {
 *           type: 'string',
 *           enum: tones,
 *           description: 'Tone of the message',
 *         },
 *         args: {
 *           type: "array",
 *           items: {
 *              type: "string"
 *           }
 *         }
 *       },
 *       required: ['messageType', 'recipient'],
 *     },
 * ```
 */
class McpToolInputSchema(
  /**
   * Parameters to type schema map
   */
  val parameters: Map<String, JsonElement>,

  /**
   * Names of required parameters
   */
  val requiredParameters: Set<String>,
  /**
   * Type definitions that can be referred in the parameter schema
   *
   * Currently not supported
   */
  val definitions: Map<String, JsonElement>,
  /**
   * Definitions path in the schema
   *
   * Currently not supported
   */
  val definitionsPath: String = DEFAULT_DEFINITIONS_PATH) {

  companion object {
    const val DEFAULT_DEFINITIONS_PATH: String = "definitions"
  }

  val properties: JsonObject = buildJsonObject {
    for ((name, type) in parameters) {
      put(name, type)
    }
  }

  private val schemaObject = buildJsonObject {
    put("type", "object")
    put("properties", properties)
    put("required", buildJsonArray {
      for (requiredParameter in requiredParameters) {
        add(requiredParameter)
      }
    })
    put("additionalProperties", false)

    if (definitions.isNotEmpty()) {
      put(definitionsPath, buildJsonObject {
        for ((name, definition) in definitions) {
          put(name, definition)
        }
      })
    }
  }

  private val json = Json { prettyPrint = true }

  fun prettyPrint(): String {
    return json.encodeToString(schemaObject)
  }
}