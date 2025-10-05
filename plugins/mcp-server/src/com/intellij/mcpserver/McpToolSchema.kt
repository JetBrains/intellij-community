package com.intellij.mcpserver

import kotlinx.serialization.json.*

/**
 * Schema of the input or output of an MCP tool.
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
class McpToolSchema(
  val propertiesSchema: JsonObject,
  /**
   * Names of required properties
   */
  val requiredProperties: Set<String>,
  /**
   * Type definitions that can be referred in the schema
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
    private val json = Json { prettyPrint = true }

    fun ofPropertiesMap(
      properties: Map<String, JsonElement>,
      requiredProperties: Set<String>,
      definitions: Map<String, JsonElement>,
      definitionsPath: String = DEFAULT_DEFINITIONS_PATH,
    ): McpToolSchema {
      val propertiesJson = buildJsonObject {
        for ((name, type) in properties) {
          put(name, type)
        }
      }
      return ofPropertiesSchema(propertiesJson, requiredProperties, definitions, definitionsPath)
    }

    fun ofPropertiesSchema(
      properties: JsonObject,
      requiredProperties: Set<String>,
      definitions: Map<String, JsonElement>,
      definitionsPath: String = DEFAULT_DEFINITIONS_PATH,
    ): McpToolSchema {
      return McpToolSchema(propertiesSchema = properties, requiredProperties = requiredProperties, definitions = definitions, definitionsPath = definitionsPath)
    }
  }

  private val schemaObject = buildJsonObject {
    put("type", "object")
    put("properties", propertiesSchema)
    put("required", buildJsonArray {
      for (requiredParameter in requiredProperties) {
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

  fun prettyPrint(): String {
    return json.encodeToString(schemaObject)
  }
}