// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.run.extractCodepageFromChcp
import org.jetbrains.idea.maven.execution.run.extractCodepageFromLocale
import org.jetbrains.idea.maven.execution.run.getAllWindowsCodePages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException


class CharsetParseTest {

  @Test
  fun testLinuxLocaleUtf8() = runBlocking {
    val executeResult = withOutput("""
      LANG=""
      LC_COLLATE="C"
      LC_CTYPE="UTF-8"
      LC_MESSAGES="C"
      LC_MONETARY="C"
      LC_NUMERIC="C"
      LC_TIME="C"
      LC_ALL=
    """.trimIndent())

    assertEquals("UTF-8", extractCodepageFromLocale(executeResult))
  }


  @Test
  fun testLinuxLocaleRuUtf8() = runBlocking {
    val executeResult = withOutput("""
      LANG=""
      LC_COLLATE="C"
      LC_CTYPE="ru_RU.UTF-8"
      LC_MESSAGES="C"
      LC_MONETARY="C"
      LC_NUMERIC="C"
      LC_TIME="C"
      LC_ALL=
    """.trimIndent())

    assertEquals("UTF-8", extractCodepageFromLocale(executeResult))
  }

  @Test
  fun testLinuxLocaleC() = runBlocking {
    val executeResult = withOutput("""
      LANG=""
      LC_COLLATE="C"
      LC_CTYPE="C"
      LC_MESSAGES="C"
      LC_MONETARY="C"
      LC_NUMERIC="C"
      LC_TIME="C"
      LC_ALL=
    """.trimIndent())

    assertNull(extractCodepageFromLocale(executeResult))
  }

  @Test
  fun testWindowsChineseCharsetEngLanguage() = runBlocking {
    val executeResult = withOutput("Active code page: 936")
    assertEquals("GBK", extractCodepageFromChcp(executeResult))
  }

  @Test
  fun testWindowsChineseCharsetChineseLanguage() = runBlocking {
    val executeResult = withOutput("活动代码页: 936")
    assertEquals("GBK", extractCodepageFromChcp(executeResult))
  }


  @Test
  fun testAllCharsetsAreValid() {
    val invalid = ArrayList<String>()
    for ((codepage, name) in getAllWindowsCodePages()) {
      try {
        val executeResult = withOutput("Active code page: $codepage")
        Charset.forName(name)
        assertEquals(name.lowercase(), extractCodepageFromChcp(executeResult)?.lowercase(), "Returned another charset")
      }
      catch (uce: UnsupportedCharsetException) {
        invalid.add("Entry $codepage=$name is invalid")
      }

    }
    if (invalid.isNotEmpty()) {
      fail { invalid.joinToString("\n") }
    }
  }

  private fun withOutput(stdout: String): EelProcessExecutionResult {
    return EelProcessExecutionResult(0, stdout.toByteArray(), ByteArray(0))
  }
}