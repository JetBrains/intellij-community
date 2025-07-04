// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.ssl

import com.intellij.maven.testFramework.assertNormalizedEquals
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ResourceUtil
import java.io.ByteArrayOutputStream
import java.util.*

class SslDelegateHandlerStateMachineTest : UsefulTestCase() {

  fun testResultOk() {
    val data = fromFile("ssl_remote_query.txt");
    val machine = SslDelegateHandlerStateMachine { _, _ -> true }
    val out = ByteArrayOutputStream()
    machine.output = out
    data.forEach { machine.addLine(it) }
    val expected = fromFile("ssl_remote_query_response_ok.txt").joinToString("")
    val actual = out.toString()
    assertNormalizedEquals(expected, actual)
  }

  fun testResultFail() {
    val data = fromFile("ssl_remote_query.txt");
    val machine = SslDelegateHandlerStateMachine { _, _ -> false }
    val out = ByteArrayOutputStream()
    machine.output = out
    data.forEach { machine.addLine(it) }
    val expected = fromFile("ssl_remote_query_response_error.txt").joinToString("")
    val actual = out.toString()
    assertNormalizedEquals(expected, actual)
  }

  private fun fromFile(file: String): Array<String> {
    ResourceUtil.getResourceAsStream(SslDelegateHandlerStateMachineTest::class.java.classLoader, "org/jetbrains/maven/server/ssl", file).use { stream ->
      Scanner(stream).use { scanner ->
        val result: MutableList<String> = ArrayList()
        while (scanner.hasNextLine()) {
          result.add(scanner.nextLine() + "\n")
        }
        return ArrayUtilRt.toStringArray(result)
      }
    }
  }
}