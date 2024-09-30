package com.jetbrains.performancePlugin.commands

import com.sampullara.cli.Args
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource


class OpenFileCommandOptionsTest {

  @ParameterizedTest
  @ValueSource(strings = [
    "-file src/Main.java -suppressErrors -timeout 99 -disableCodeAnalysis",
    "-file src/Main.java -suppressErrors -timeout 99 -dsa",
  ])
  fun testThat_Options_Successfully_Parsed(commandLine: String) {
    //Related to OpenFileCommand logic
    val myOptions = runCatching {
      OpenFileCommandOptions().apply { Args.parse(this, commandLine.split(" ").toTypedArray(), false) }
    }.getOrNull()

    assertTrue(myOptions!!.suppressErrors)
    assertTrue(myOptions.disableCodeAnalysis)
    assertEquals(99, myOptions.timeout)
    assertEquals("src/Main.java", myOptions.file)
  }

  @Test
  fun testThat_DisableCodeAnalysis_is_False_ByDefault() {
    //Related to OpenFileCommand logic
    val myOptions = runCatching {
      OpenFileCommandOptions().apply { Args.parse(this, "-file src/Main.java -suppressErrors -timeout 99".split(" ").toTypedArray(), false) }
    }.getOrNull()

    assertFalse(myOptions!!.disableCodeAnalysis)
  }

}