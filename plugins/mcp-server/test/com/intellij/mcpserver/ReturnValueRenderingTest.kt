package com.intellij.mcpserver

import com.intellij.mcpserver.impl.util.asTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KFunction
import kotlin.test.assertEquals

fun returnLong(): Long { return 123456789123456789 }
fun returnInt(): Int { return 1234567891 }
fun returnShort(): Short { return 12345 }
fun returnByte(): Byte { return 123 }
fun returnBoolean(): Boolean { return true }
fun returnChar(): Char { return 'a' }
fun returnString(): String { return "string" }
fun returnFloat(): Float { return 1.23458f }
fun returnDouble(): Double { return 1.2345 }
fun returnVoid(): Unit {}
fun returnNull(): Any? { return null }
fun returnResult(): McpToolCallResult { return McpToolCallResult(arrayOf(McpToolCallResultContent.Text("text1"), McpToolCallResultContent.Text("text2"))) }
fun returnErrorResult(): McpToolCallResult { return McpToolCallResult(arrayOf(McpToolCallResultContent.Text("text1"), McpToolCallResultContent.Text("text2")), isError = true) }
fun returnResultContent(): McpToolCallResultContent { return McpToolCallResultContent.Text("content") }

class ReturnValueRenderingTest {
  companion object {
    @JvmStatic
    fun functions() = listOf(
      Arguments.of(::returnLong, "123456789123456789"),
      Arguments.of(::returnInt,"1234567891"),
      Arguments.of(::returnShort,"12345"),
      Arguments.of(::returnByte,"123"),
      Arguments.of(::returnBoolean,"true"),
      Arguments.of(::returnChar,"'a'"),
      Arguments.of(::returnString,"string"),
      Arguments.of(::returnFloat,"1.23458"),
      Arguments.of(::returnDouble,"1.2345"),
      Arguments.of(::returnVoid,"[success]"),
      Arguments.of(::returnNull,"[null]"),
      Arguments.of(::returnResult,"text1\ntext2"),
      Arguments.of(::returnErrorResult,"[error]: text1\ntext2"),
      Arguments.of(::returnResultContent,"content"),
    )
  }

  @ParameterizedTest
  @MethodSource("functions")
  fun test(callable: KFunction<*>, expected: String) = runTest {
    val asTool = callable.asTool()
    val actualResult = asTool.call(buildJsonObject {})
    assertEquals(expected, actualResult.toString())
  }
}