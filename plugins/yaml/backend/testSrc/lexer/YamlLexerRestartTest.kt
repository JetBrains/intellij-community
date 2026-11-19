// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.lexer

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.RestartableLexer
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.lexer.FlexAdapter
import com.intellij.platform.syntax.util.lexer.FlexLexer
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapter
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.syntaxLexerFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.yaml.syntax.YamlSyntaxDefinition
import com.intellij.yaml.syntax.YamlSyntaxTokenTypes
import com.intellij.yaml.syntax.lexer.YamlLexer
import com.intellij.yaml.syntax.lexer._YamlLexer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Checks the restart of the YAML lexer at a line start.
 * Each restart test calls `checkCorrectRestart`, which restarts the lexer at each restartable state.
 */
@TestFixtures
class YamlLexerRestartTest {
  private val lexer = syntaxLexerFixture(DATA_PATH) { YamlSyntaxDefinition.createLexer() }

  @Test
  fun `each line start in a block mapping is restartable`() {
    val text = BLOCK_MAPPING_TEXT
    val lineStarts = lineStartTokenStates(text)
    assertEquals(text.lines().count { it.isNotEmpty() }, lineStarts.size)
    for ((offset, state) in lineStarts) {
      assertTrue(isRestartable(state)) { "no restart at offset $offset, state $state" }
    }
    lexer.get().checkCorrectRestart(text)
  }

  @Test
  fun `block scalar`() {
    lexer.get().checkCorrectRestart(BLOCK_SCALAR_TEXT)
  }

  @Test
  fun `block scalar in a flow collection`() {
    lexer.get().checkCorrectRestart(BLOCK_SCALAR_IN_FLOW_TEXT)
  }

  @Test
  fun `nested flow collections`() {
    lexer.get().checkCorrectRestart(NESTED_FLOW_TEXT)
  }

  @Test
  fun `multi-line plain scalar`() {
    lexer.get().checkCorrectRestart(MULTI_LINE_PLAIN_TEXT)
  }

  @Test
  fun `key after a multi-line plain scalar`() {
    lexer.get().checkCorrectRestart(KEY_AFTER_PLAIN_TEXT)
  }

  @Test
  fun `multi-line quoted scalars`() {
    lexer.get().checkCorrectRestart(MULTI_LINE_QUOTED_TEXT)
  }

  @Test
  fun `no restart after an unclosed quote`() {
    val text = UNCLOSED_QUOTE_TEXT
    for ((offset, state) in lineStartTokenStates(text)) {
      if (offset > text.indexOf('"')) {
        assertFalse(isRestartable(state)) { "restart at offset $offset, state $state" }
      }
    }
    lexer.get().checkCorrectRestart(text)
  }

  /** A line break before `---` in a flow collection resets the state for the next document, but the quote stays unclosed. */
  @Test
  fun `no restart after an unclosed quote in a flow collection before a document marker`() {
    val text = UNCLOSED_QUOTE_IN_FLOW_TEXT
    for ((offset, state) in lineStartTokenStates(text)) {
      if (offset > text.indexOf('"')) {
        assertFalse(isRestartable(state)) { "restart at offset $offset, state $state" }
      }
    }
    lexer.get().checkCorrectRestart(text)
  }

  @Test
  fun `deep indent`() {
    lexer.get().checkCorrectRestart(DEEP_INDENT_TEXT)
  }

  @Test
  fun `document markers`() {
    lexer.get().checkCorrectRestart(DOCUMENT_MARKERS_TEXT)
  }

  /**
   * The lexer reads the char before a token to find a line start.
   * A restartable state has one of the lexical states of [LINE_START_LEXICAL_STATES].
   * So the lexer reads the char only when `yystate` runs in such a lexical state.
   * The bound counts these calls on a chain of the same shape that reads no char.
   */
  @Test
  fun `the lexer reads the char before a token only in a lexical state of a line start`() {
    val text = MIXED_TEXT
    val yamlReads = countReads(text, YamlLexer())

    val rawFlex = StateCountingFlexLexer(_YamlLexer())
    val rawReads = countReads(text, MergingLexerAdapter(FlexAdapter(rawFlex), syntaxElementTypeSetOf(YamlSyntaxTokenTypes.TEXT)))

    val extraReads = yamlReads - rawReads
    assertTrue(extraReads <= rawFlex.lineStartStateCalls) {
      "$extraReads extra reads for ${rawFlex.calls} state calls, ${rawFlex.lineStartStateCalls} of them in a lexical state of a line start"
    }
  }

  /** The lexer gives each token the same state as the old lexer, which read the char before each token. */
  @Test
  fun `each state equals the state of packState`() {
    val dataFiles = Path.of(DATA_PATH).listDirectoryEntries().filter { it.extension == "yml" }.sortedBy { it.name }
    assertTrue(dataFiles.isNotEmpty()) { "no test data in $DATA_PATH" }
    for (file in dataFiles) {
      assertSameTokens(file.name, file.readText())
    }
    for ((index, text) in CASE_TEXTS.withIndex()) {
      assertSameTokens("case text $index", text)
    }
  }

  private fun assertSameTokens(name: String, text: String) {
    val yamlLexer = YamlLexer()
    val oldLexer = oldYamlLexer()
    yamlLexer.start(text)
    oldLexer.start(text)
    while (true) {
      val type = oldLexer.getTokenType()
      val start = oldLexer.getTokenStart()
      assertEquals(type, yamlLexer.getTokenType()) { "$name: the token type at offset $start" }
      if (type == null) break
      assertEquals(start, yamlLexer.getTokenStart()) { "$name: the token start" }
      assertEquals(oldLexer.getTokenEnd(), yamlLexer.getTokenEnd()) { "$name: the token end at offset $start" }
      assertEquals(oldLexer.getState(), yamlLexer.getState()) { "$name: the state at offset $start" }
      oldLexer.advance()
      yamlLexer.advance()
    }
  }

  private fun countReads(text: String, lexer: Lexer): Int {
    val chars = CountingChars(text)
    lexer.start(chars)
    while (lexer.getTokenType() != null) {
      lexer.getState()
      lexer.advance()
    }
    return chars.reads
  }

  private fun isRestartable(state: Int): Boolean {
    val lexer = YamlSyntaxDefinition.createLexer()
    return if (lexer is RestartableLexer) lexer.isRestartableState(state) else state == 0
  }

  /** Returns the start offset and the state of each first token on a line. */
  private fun lineStartTokenStates(text: String): List<Pair<Int, Int>> {
    val lexer = YamlSyntaxDefinition.createLexer()
    lexer.start(text)
    val result = ArrayList<Pair<Int, Int>>()
    while (lexer.getTokenType() != null) {
      val start = lexer.getTokenStart()
      if (start == 0 || text[start - 1] == '\n') {
        result.add(start to lexer.getState())
      }
      lexer.advance()
    }
    return result
  }
}

/** Counts the calls of [get]. */
private class CountingChars(private val text: String) : CharSequence {
  var reads = 0
    private set

  override val length: Int get() = text.length

  override fun get(index: Int): Char {
    reads++
    return text[index]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)

  override fun toString(): String = text
}

/** Reports the state of [_YamlLexer.packState] for each token, as the lexer did before it skipped the char read. */
private class PackStateFlexLexer(private val lexer: _YamlLexer) : FlexLexer by lexer {
  override fun yystate(): Int = lexer.packState()
}

/** Reports the lexical state, and counts the calls of [yystate] in each lexical state of a line start. */
private class StateCountingFlexLexer(private val lexer: _YamlLexer) : FlexLexer by lexer {
  var calls = 0
    private set
  var lineStartStateCalls = 0
    private set

  override fun yystate(): Int {
    val state = lexer.yystate()
    calls++
    if (state in LINE_START_LEXICAL_STATES) lineStartStateCalls++
    return state
  }
}

private fun oldYamlLexer(): Lexer =
  MergingLexerAdapter(FlexAdapter(PackStateFlexLexer(_YamlLexer())), syntaxElementTypeSetOf(YamlSyntaxTokenTypes.TEXT))

private val DATA_PATH = PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/lexer/data"

/** The lexical states of [_YamlLexer] that a restartable state can hold. */
private val LINE_START_LEXICAL_STATES = setOf(
  _YamlLexer.YYINITIAL,
  _YamlLexer.LINE_START_STATE,
  _YamlLexer.FLOW_STATE,
  _YamlLexer.BS_BODY_STATE,
)

private const val BLOCK_MAPPING_TEXT = """
  |a:
  |  b:
  |    c: d
  |    e: f
  |  g:
  |  - h: i
  |    j: k
  |""".trimMargin()

private const val BLOCK_SCALAR_TEXT = """
  |a:
  |  literal: |
  |    line 1
  |
  |    line 2
  |  folded: >-
  |    line 3
  |    line 4
  |  b: c
  |""".trimMargin()

private const val BLOCK_SCALAR_IN_FLOW_TEXT = """
  |a: [ b, {
  |  c: |
  |    text
  |  , d: e },
  |  f ]
  |g: h
  |""".trimMargin()

private const val NESTED_FLOW_TEXT = """
  |a: [ [ [ [ [ [ [ [ [
  |  b,
  |  c ] ] ] ] ] ] ] ] ]
  |d: e
  |""".trimMargin()

private const val MULTI_LINE_PLAIN_TEXT = """
  |a:
  |  b: plain text
  |    continues here
  |    c: still text
  |  d: e
  |""".trimMargin()

private const val KEY_AFTER_PLAIN_TEXT = """
  |- plain text
  |  key: |
  |    block
  |- next
  |""".trimMargin()

private const val MULTI_LINE_QUOTED_TEXT = """
  |a: "line 1
  |  line 2"
  |b: 'line 3
  |  line 4'
  |c: d
  |""".trimMargin()

private const val UNCLOSED_QUOTE_TEXT = "a: \"b\nc: d\ne: f\n"

private const val UNCLOSED_QUOTE_IN_FLOW_TEXT = "a: {\n  b: \"c\n---\nd: e\n"

private val DEEP_INDENT_TEXT = buildString {
  for (depth in 0..140) {
    append(" ".repeat(depth)).append("k").append(depth).append(":\n")
  }
  append(" ".repeat(141)).append("v\n")
  append("top: x\n")
}

private const val DOCUMENT_MARKERS_TEXT = """
  |---
  |a: b
  |...
  |---
  |- c
  |--- d
  |e: [ f,
  |---
  |g: h
  |""".trimMargin()

/** About 200 lines with a block mapping, a flow mapping, a block scalar and quoted scalars. */
private val MIXED_TEXT = buildString {
  append("paths:\n")
  for (i in 0 until 25) {
    append("  /items/$i:\n")
    append("    summary: \"Get the item $i\"\n")
    append("    title: 'The item $i'\n")
    append("    x-flow: {first: one, second: [two, three]}\n")
    append("    x-text: |\n")
    append("      block text of the item $i\n")
    append("    # The comment of the item $i\n")
    append("    x-note: plain text of the item $i\n")
  }
}

private val CASE_TEXTS = listOf(
  BLOCK_MAPPING_TEXT,
  BLOCK_SCALAR_TEXT,
  BLOCK_SCALAR_IN_FLOW_TEXT,
  NESTED_FLOW_TEXT,
  MULTI_LINE_PLAIN_TEXT,
  KEY_AFTER_PLAIN_TEXT,
  MULTI_LINE_QUOTED_TEXT,
  UNCLOSED_QUOTE_TEXT,
  UNCLOSED_QUOTE_IN_FLOW_TEXT,
  DEEP_INDENT_TEXT,
  DOCUMENT_MARKERS_TEXT,
  MIXED_TEXT,
)
