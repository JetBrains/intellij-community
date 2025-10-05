package com.intellij.mcpserver

import com.intellij.mcpserver.impl.util.asTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KFunction


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
        "d",
        "eEncodeDefault"
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