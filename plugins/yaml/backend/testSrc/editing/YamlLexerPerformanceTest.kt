// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.text.ByteArrayCharSequence
import com.intellij.util.text.ImmutableText
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLParserDefinition
import org.junit.jupiter.api.Test

/**
 * Measures a full lex of a large OpenAPI file with each kind of buffer that the IDE gives to a lexer, see IJPL-63225.
 * A loaded document holds a [String] or a [ByteArrayCharSequence].
 * After the first edit, the document holds an [ImmutableText] rope of small leaves.
 */
@StressTestApplication
@TestApplication
@PerformanceUnitTest
class YamlLexerPerformanceTest : YamlPerformanceTestCase() {
  @Test
  fun testLexString() = doLexTest(text)

  @Test
  fun testLexByteArray() = doLexTest(ByteArrayCharSequence(text.toByteArray(Charsets.ISO_8859_1)))

  @Test
  fun testLexEditedRope() = doLexTest(editedRope())

  @Test
  fun testHighlighterSetTextEditedRope() {
    val rope = editedRope()
    val scheme = EditorColorsManager.getInstance().globalScheme
    benchmark {
      val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(YAMLFileType.YML, scheme, null)
      check(highlighter is LexerEditorHighlighter)
      highlighter.setText(rope)
      sink += highlighter.createIterator(rope.length / 2).start
    }.warmupIterations(WARMUPS).attempts(ATTEMPTS).startForTest()
  }

  private fun doLexTest(buffer: CharSequence) {
    benchmark {
      sink += lexAll(YAMLParserDefinition.createLexer(), buffer)
    }.warmupIterations(WARMUPS).attempts(ATTEMPTS).startForTest()
  }

  private fun lexAll(lexer: Lexer, buffer: CharSequence): Int {
    var sum = 0
    lexer.start(buffer)
    while (lexer.tokenType != null) {
      sum += lexer.state + lexer.tokenEnd
      lexer.advance()
    }
    return sum
  }

  /** Returns the text as a rope after one edit in the middle, as a document holds it after the first keystroke. */
  private fun editedRope(): ImmutableText {
    val middle = text.indexOf('\n', text.length / 2) + 1
    return ImmutableText.valueOf(text).insert(middle, "\n")
  }

  private companion object {
    const val WARMUPS = 5
    const val ATTEMPTS = 10

    /** The OpenAPI file of about 8.6 MB, the size of the file of IJPL-63225. The text is ASCII, so a byte array holds it. */
    val text: String by lazy { openApiText(items = 20_000) }

    /** Keeps the result of each attempt, so that the JIT does not drop the lex. */
    @Volatile
    var sink: Int = 0
  }
}
