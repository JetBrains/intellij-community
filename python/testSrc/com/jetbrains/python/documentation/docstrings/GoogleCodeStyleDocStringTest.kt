// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstrings

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.toolbox.Substring
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration

@TestFor(classes = [GoogleCodeStyleDocString::class])
@Subsystems.QuickDocumentation
@Layers.Functional
class GoogleCodeStyleDocStringTest {
  @Test
  fun `parameter name and type`() {
    val docString = parse("""
      Summary

      Args:
          x (int): first parameter
          y ( Optional[str] ) : second parameter
    """.trimIndent())

    val fields = docString.parameterFields
    assertEquals(listOf("x", "y"), fields.map { it.name })
    assertEquals(listOf("int", "Optional[str]"), fields.map { it.type })
  }

  @Test
  fun `long run of blanks in a parameter line does not make the parser backtrack`() {
    // `.` in the field pattern does not match U+0085, so the match fails after the blanks.
    val longLine = "    x (" + " ".repeat(1000) + "\u0085y"
    val text = buildString {
      appendLine("Args:")
      appendLine("    a (int): first parameter")
      appendLine(longLine)
    }

    val docString = assertTimeoutPreemptively(Duration.ofSeconds(10)) { parse(text) }

    assertEquals("a", docString.parameterFields.first().name)
  }

  @Test
  fun `long indentation of a parameter line without a parenthesis does not make the parser backtrack`() {
    val text = buildString {
      appendLine("Args:")
      appendLine(" ".repeat(3000) + "x\u0085y")
    }

    assertTimeoutPreemptively(Duration.ofSeconds(10)) { parse(text).parameterFields }
  }

  private fun parse(text: String): GoogleCodeStyleDocString = GoogleCodeStyleDocString(Substring(text))
}
