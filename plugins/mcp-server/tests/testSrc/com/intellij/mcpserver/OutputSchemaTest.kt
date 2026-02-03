package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.impl.util.asTool
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KFunction

@Serializable
class Object2(
  @property:McpDescription("Int field") val intField: Int,
  @property:McpDescription("Int nullable") val intNullable: Int? = null,
  @property:McpDescription("String field") val stringField: String,
  @property:McpDescription("String nullable") val stringNullable: String? = null,
  @property:McpDescription("Boolean field") val booleanField: Boolean,
  @property:McpDescription("Boolean nullable") val booleanNullable: Boolean? = null,
  @property:McpDescription("Double field") val doubleField: Double,
  @property:McpDescription("Double nullable") val doubleNullable: Double? = null,
  @property:McpDescription("List field") val listField: List<String>,
  @property:McpDescription("List nullable") val listNullable: List<String>? = null,
  @property:McpDescription("Nested field") val nestedField: Inner,
  @property:McpDescription("Nested nullable") val nestedNullable: Inner? = null,
  @property:McpDescription("Enum field") val enumField: Kind,
  @property:McpDescription("Enum nullable") val enumNullable: Kind? = null,
  @property:McpDescription("EncodeDefault ALWAYS") @EncodeDefault(EncodeDefault.Mode.ALWAYS) val encodeDefaultAlways: String = "default",
  @property:McpDescription("EncodeDefault NEVER") @EncodeDefault(EncodeDefault.Mode.NEVER) val encodeDefaultNever: String = "default",
  @property:McpDescription("EncodeDefault ALWAYS nullable") @EncodeDefault(EncodeDefault.Mode.ALWAYS) val encodeDefaultAlwaysNullable: String? = null,
  @property:McpDescription("EncodeDefault NEVER nullable") @EncodeDefault(EncodeDefault.Mode.NEVER) val encodeDefaultNeverNullable: String? = null,
  @property:McpDescription("Default value") val defaultValue: Int = 42,
  @property:McpDescription("Bool with default false") val boolDefaultFalse: Boolean = false,
  @property:McpDescription("Bool with default true") val boolDefaultTrue: Boolean = true,
  @property:McpDescription("Bool EncodeDefault NEVER with default false") @EncodeDefault(EncodeDefault.Mode.NEVER) val boolEncodeNeverDefaultFalse: Boolean = false,
  @property:McpDescription("Bool EncodeDefault NEVER with default true") @EncodeDefault(EncodeDefault.Mode.NEVER) val boolEncodeNeverDefaultTrue: Boolean = true,
  @property:McpDescription("Bool EncodeDefault ALWAYS with default false") @EncodeDefault(EncodeDefault.Mode.ALWAYS) val boolEncodeAlwaysDefaultFalse: Boolean = false,
  @property:McpDescription("Bool EncodeDefault ALWAYS with default true") @EncodeDefault(EncodeDefault.Mode.ALWAYS) val boolEncodeAlwaysDefaultTrue: Boolean = true,
  @property:McpDescription("Bool nullable EncodeDefault NEVER") @EncodeDefault(EncodeDefault.Mode.NEVER) val boolNullableEncodeNever: Boolean? = false,
  @property:McpDescription("Bool nullable EncodeDefault ALWAYS") @EncodeDefault(EncodeDefault.Mode.ALWAYS) val boolNullableEncodeAlways: Boolean? = null,
)

fun output_fun1(): Object { TODO() }
fun output_fun2(): String { TODO() }
fun output_fun3(): Unit { TODO() }
fun output_fun4(): String { TODO() }
fun output_fun5(): Int { TODO() }
fun output_fun6(): Number { TODO() }
fun output_fun7(): Double { TODO() }
fun output_fun8(): Char { TODO() }
fun output_fun9(): McpToolCallResult { TODO() }
fun output_fun10(): McpToolCallResultContent { TODO() }
fun output_fun11(): Kind { TODO() }
fun output_fun12(): Object2 { TODO() }

class OutputSchemaTest {
  companion object {
    @JvmStatic
    fun testVariants(): Array<Arguments> {
      return arrayOf(
        Arguments.of(::output_fun1, /**language=JSON*/ """{
    "type": "object",
    "properties": {
        "a": {
            "type": "integer",
            "description": "Field description a"
        },
        "b": {
            "type": "string",
            "description": "Field description b"
        },
        "b1": {
            "type": "string",
            "minLength": 1,
            "maxLength": 1,
            "description": "Field description b1"
        },
        "c": {
            "enum": [
                "K1",
                "K2",
                "K3"
            ],
            "description": "Field description c"
        },
        "d": {
            "type": "object",
            "required": [
                "e"
            ],
            "properties": {
                "e": {
                    "type": "number"
                }
            },
            "description": "Field description d"
        },
        "dOptional": {
            "type": [
                "object",
                "null"
            ],
            "required": [
                "e"
            ],
            "properties": {
                "e": {
                    "type": "number"
                }
            },
            "description": "Field description dOptional"
        },
        "eEncodeDefault": {
            "type": "boolean",
            "description": "Field description eEncodeDefault"
        },
        "fEncodeDefaultNullable": {
            "type": [
                "boolean",
                "null"
            ],
            "description": "Field description fEncodeDefaultNullable"
        }
    },
    "required": [
        "a",
        "b",
        "b1",
        "c",
        "d"
    ],
    "additionalProperties": false
}"""),
        Arguments.of(::output_fun2, null),
        Arguments.of(::output_fun3, null),
        Arguments.of(::output_fun4, null),
        Arguments.of(::output_fun5, null),
        Arguments.of(::output_fun6, null),
        Arguments.of(::output_fun7, null),
        Arguments.of(::output_fun8, null),
        Arguments.of(::output_fun9, null),
        Arguments.of(::output_fun10, null),
        Arguments.of(::output_fun11, null),
        Arguments.of(::output_fun12, /**language=JSON*/ """{
    "type": "object",
    "properties": {
        "intField": {
            "type": "integer",
            "description": "Int field"
        },
        "intNullable": {
            "type": [
                "integer",
                "null"
            ],
            "description": "Int nullable"
        },
        "stringField": {
            "type": "string",
            "description": "String field"
        },
        "stringNullable": {
            "type": [
                "string",
                "null"
            ],
            "description": "String nullable"
        },
        "booleanField": {
            "type": "boolean",
            "description": "Boolean field"
        },
        "booleanNullable": {
            "type": [
                "boolean",
                "null"
            ],
            "description": "Boolean nullable"
        },
        "doubleField": {
            "type": "number",
            "description": "Double field"
        },
        "doubleNullable": {
            "type": [
                "number",
                "null"
            ],
            "description": "Double nullable"
        },
        "listField": {
            "type": "array",
            "items": {
                "type": "string"
            },
            "description": "List field"
        },
        "listNullable": {
            "type": [
                "array",
                "null"
            ],
            "items": {
                "type": "string"
            },
            "description": "List nullable"
        },
        "nestedField": {
            "type": "object",
            "required": [
                "e"
            ],
            "properties": {
                "e": {
                    "type": "number"
                }
            },
            "description": "Nested field"
        },
        "nestedNullable": {
            "type": [
                "object",
                "null"
            ],
            "required": [
                "e"
            ],
            "properties": {
                "e": {
                    "type": "number"
                }
            },
            "description": "Nested nullable"
        },
        "enumField": {
            "enum": [
                "K1",
                "K2",
                "K3"
            ],
            "description": "Enum field"
        },
        "enumNullable": {
            "enum": [
                "K1",
                "K2",
                "K3"
            ],
            "description": "Enum nullable"
        },
        "encodeDefaultAlways": {
            "type": "string",
            "description": "EncodeDefault ALWAYS"
        },
        "encodeDefaultNever": {
            "type": "string",
            "description": "EncodeDefault NEVER"
        },
        "encodeDefaultAlwaysNullable": {
            "type": [
                "string",
                "null"
            ],
            "description": "EncodeDefault ALWAYS nullable"
        },
        "encodeDefaultNeverNullable": {
            "type": [
                "string",
                "null"
            ],
            "description": "EncodeDefault NEVER nullable"
        },
        "defaultValue": {
            "type": "integer",
            "description": "Default value"
        },
        "boolDefaultFalse": {
            "type": "boolean",
            "description": "Bool with default false"
        },
        "boolDefaultTrue": {
            "type": "boolean",
            "description": "Bool with default true"
        },
        "boolEncodeNeverDefaultFalse": {
            "type": "boolean",
            "description": "Bool EncodeDefault NEVER with default false"
        },
        "boolEncodeNeverDefaultTrue": {
            "type": "boolean",
            "description": "Bool EncodeDefault NEVER with default true"
        },
        "boolEncodeAlwaysDefaultFalse": {
            "type": "boolean",
            "description": "Bool EncodeDefault ALWAYS with default false"
        },
        "boolEncodeAlwaysDefaultTrue": {
            "type": "boolean",
            "description": "Bool EncodeDefault ALWAYS with default true"
        },
        "boolNullableEncodeNever": {
            "type": [
                "boolean",
                "null"
            ],
            "description": "Bool nullable EncodeDefault NEVER"
        },
        "boolNullableEncodeAlways": {
            "type": [
                "boolean",
                "null"
            ],
            "description": "Bool nullable EncodeDefault ALWAYS"
        }
    },
    "required": [
        "intField",
        "stringField",
        "booleanField",
        "doubleField",
        "listField",
        "nestedField",
        "enumField"
    ],
    "additionalProperties": false
}"""),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("testVariants")
  fun testSchema(callable: KFunction<*>, expected: String?) {
    val actualSchema = callable.asTool().descriptor.outputSchema?.prettyPrint()
    assertEquals(expected, actualSchema, "Unexpected schema for $callable")
  }
}