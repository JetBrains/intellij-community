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
enum class Kind {
  @McpDescription("Enum item description K1") K1,
  @McpDescription("Enum item description K2") K2,
  @McpDescription("Enum item description K3") K3
}

@Serializable
class Node(val inner: Node? = null) {

}

@Serializable
class Root(val node: Node)

@Serializable
class Inner(
  val e: Double
)

@Serializable
class Object(
  @property:McpDescription("Field description a") val a: Int,
  @property:McpDescription("Field description b") val b: String,
  @property:McpDescription("Field description b1") val b1: Char,
  @property:McpDescription("Field description c") val c: Kind,
  @property:McpDescription("Field description d") val d: Inner,
  @property:McpDescription("Field description dOptional") val dOptional: Inner? = null,
  @property:McpDescription("Field description eEncodeDefault") @EncodeDefault(EncodeDefault.Mode.NEVER) val eEncodeDefault: Boolean = true,
  @property:McpDescription("Field description fEncodeDefaultNullable") @EncodeDefault(EncodeDefault.Mode.NEVER) val fEncodeDefaultNullable: Boolean? = null) {
}

fun fun1(
  @McpDescription("int description on parameter") intArg: Int
) {}

fun fun2(
  @McpDescription("string description on parameter") stringArg: String
) {}

fun fun3(
  @McpDescription("enum description on parameter") enumArg: Kind
) {}

fun fun4(
  @McpDescription("object description on parameter") objArg: Object
) {}

fun fun5(
  @McpDescription("object optional description on parameter") objArgOptional: Object? = null
) {}

fun fun6(
  @McpDescription("Node description on parameter") nodeArg: Node
) {}

fun fun7(
  @McpDescription("Root description on parameter") rootArg: Root
) {}

fun fun8(
  @McpDescription("Root nullable description on parameter") rootArgNullable: Root?
) {}

class InputSchemaTest {

  companion object {
    @JvmStatic
    fun testVariants(): Array<Arguments> {
      return arrayOf(
        Arguments.of(::fun1, /*language=JSON*/ """{
    "type": "object",
    "properties": {
        "intArg": {
            "type": "integer",
            "description": "int description on parameter"
        }
    },
    "required": [
        "intArg"
    ],
    "additionalProperties": false
}"""),
        Arguments.of(::fun2, /*language=JSON*/ """{
    "type": "object",
    "properties": {
        "stringArg": {
            "type": "string",
            "description": "string description on parameter"
        }
    },
    "required": [
        "stringArg"
    ],
    "additionalProperties": false
}"""),
        Arguments.of(::fun3, /*language=JSON*/ """{
    "type": "object",
    "properties": {
        "enumArg": {
            "enum": [
                "K1",
                "K2",
                "K3"
            ],
            "description": "enum description on parameter"
        }
    },
    "required": [
        "enumArg"
    ],
    "additionalProperties": false
}"""),
        Arguments.of(::fun4, /*language=JSON*/ """{
    "type": "object",
    "properties": {
        "objArg": {
            "type": "object",
            "required": [
                "a",
                "b",
                "b1",
                "c",
                "d",
                "eEncodeDefault"
            ],
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
            "description": "object description on parameter"
        }
    },
    "required": [
        "objArg"
    ],
    "additionalProperties": false
}"""),
        Arguments.of(::fun5, /*language=JSON*/ """{
    "type": "object",
    "properties": {
        "objArgOptional": {
            "type": "object",
            "required": [
                "a",
                "b",
                "b1",
                "c",
                "d",
                "eEncodeDefault"
            ],
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
            "description": "object optional description on parameter"
        }
    },
    "required": [],
    "additionalProperties": false
}"""),
        // recursive types are rendered incorrectly now
        //Arguments.of(::fun6, /*language=JSON*/ """{"field1":1,"field2":"10"}"""),
        //Arguments.of(::fun7, /*language=JSON*/ """"tool 7 called""""),
        //Arguments.of(::fun8, /*language=JSON*/ """"tool 7 called""""),
      )
    }

  }


  @ParameterizedTest
  @MethodSource("testVariants")
  fun testSchema(callable: KFunction<*>, expected: String) {
    val actualSchema = callable.asTool().descriptor.inputSchema.prettyPrint()
    assertEquals(expected, actualSchema, "Unexpected schema for $callable")
  }
}