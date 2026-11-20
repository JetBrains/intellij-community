// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.lexer

import com.intellij.yaml.syntax.lexer.YamlLexer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Checks the column that the YAML lexer counts for the indent of a key, a `-` and a block scalar. */
class YamlLexerColumnTest {
  /**
   * U+2000A and U+20000 are both letters of two chars.
   * The low 16 bits of U+2000A are `\n`, so a column count that tests these bits starts a new line after it.
   */
  @Test
  fun `a supplementary char with the low bits of a line break does not reset the column`() {
    val text = "&a@ k: |\n   w: x\n"
    assertEquals(dump(text.replace("@", "𠀀")), dump(text.replace("@", "𠀊")))
  }

  private fun dump(text: String): String {
    val lexer = YamlLexer()
    lexer.start(text)
    val result = StringBuilder()
    while (true) {
      val type = lexer.getTokenType() ?: break
      result.append(type).append(" [").append(lexer.getTokenStart()).append(", ").append(lexer.getTokenEnd()).append("]\n")
      lexer.advance()
    }
    return result.toString()
  }
}
