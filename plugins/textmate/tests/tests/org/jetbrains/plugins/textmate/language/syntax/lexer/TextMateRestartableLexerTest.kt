package org.jetbrains.plugins.textmate.language.syntax.lexer

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.TestUtil.findScopeByFileName
import org.jetbrains.plugins.textmate.TestUtil.loadBundle
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableBuilder
import org.jetbrains.plugins.textmate.language.syntax.assertRestartMakesTheSameTokens
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks the properties [TextMateHighlightingLexer] must hold as a [com.intellij.lexer.RestartableLexer],
 * so that `LexerEditorHighlighter` re-lexes only the edited line.
 *
 * The token sequence after a restart is checked separately, by every golden test through
 * `TextMateLexerTestCase`.
 */
class TextMateRestartableLexerTest {
  @Test
  fun everyLineStartIsRestartable() {
    withLexer { lexer, text ->
      val restartOffsets = HashSet<Int>()
      lexer.start(text)
      while (lexer.tokenType != null) {
        if (lexer.isRestartableState(lexer.state)) restartOffsets.add(lexer.tokenStart)
        lexer.advance()
      }

      val lineStarts = lineStartsOf(text)
      val missed = lineStarts.filterNot { it in restartOffsets }
      assertTrue(missed.isEmpty(), "these line starts are not restartable: ${missed.take(10)}")
    }
  }

  /**
   * The states of two runs over the same text must agree. Without this the editor highlighter can never
   * re-synchronise its segments after an edit, and every keystroke re-lexes to the end of the file.
   */
  @Test
  fun stateIsStableAcrossRuns() {
    withLexer { lexer, text ->
      assertEquals(lineStartStates(lexer, text), lineStartStates(lexer, text))
    }
  }

  /**
   * An edit that does not change the block structure must leave the states of the lines below it intact.
   * This is exactly what `LexerEditorHighlighter.incrementalUpdate` needs to stop re-lexing early.
   */
  @Test
  fun statesBelowAnEditDoNotChange() {
    withLexer { lexer, text ->
      // one lexer instance, as in production: a state is an id in the table of the lexer that made it
      val before = lineStartStates(lexer, text)
      val editOffset = text.length / 2
      val editedText = text.substring(0, editOffset) + "x" + text.substring(editOffset)
      val after = lineStartStates(lexer, editedText)

      val editedLine = text.substring(0, editOffset).count { it == '\n' }
      assertEquals(before.size, after.size, "the edit must not add or remove a line")
      for (line in editedLine + 1..<before.size) {
        assertEquals(before[line], after[line], "state changed on line $line, below the edit on line $editedLine")
      }
    }
  }

  /**
   * States must merge across lines. One state per line would mean the intern table grows with the file
   * and no two runs ever agree, which is the failure [stateIsStableAcrossRuns] guards against.
   */
  @Test
  fun statesMergeAcrossLines() {
    withLexer { lexer, text ->
      val states = lineStartStates(lexer, text)
      val distinct = states.toSet().size
      assertTrue(distinct * 2 < states.size, "$distinct distinct states for ${states.size} lines, they barely merge")
    }
  }

  /**
   * The `end` pattern of a heredoc has a back-reference, so the continuation inside the heredoc holds the
   * text of the label. An edit makes a new continuation, so the lexer must give it no state.
   */
  @Test
  fun aHeredocLineIsNotRestartable() {
    withLexer(TestUtil.PHP_VSC, PHP_FILE_NAME, heredocText(HEREDOC_LABEL)) { lexer, text ->
      val states = statePerLine(lexer, text)
      assertNotNull(states[0], "the first line must be restartable")
      assertNull(states[lineOf(text, text.indexOf(HEREDOC_BODY))], "a line inside the heredoc must not be restartable")
    }
  }

  /**
   * The state table only grows, because the highlighter keeps an old id in its segment data. So a state that
   * an edit can make new must get no id at all. Every keystroke in the label of a heredoc makes a new label.
   * Without this the table grows with the edits, until it fills up and no line is restartable any more.
   */
  @Test
  fun theStateTableStopsGrowing() {
    withLexer(TestUtil.PHP_VSC, PHP_FILE_NAME, heredocText(HEREDOC_LABEL)) { lexer, text ->
      val first = maxState(lexer, text)
      for (label in listOf("A", "AB", "ABC", "ABCD")) {
        assertEquals(first, maxState(lexer, heredocText(label)), "the label $label got a new state")
      }
    }
  }

  /**
   * A restart must make the same tokens as a full run, on any text.
   *
   * The per-line loop protection in `TextMateLexerCore.parseLine` reads the byte offsets of a stack frame.
   * The offsets belong to the line that pushed the frame, and a restart restores a frame that another run
   * pushed on another line. A read of such an offset makes the restarted run disagree with the full run.
   * The random edits look for a text where this, or another difference between the two runs, shows up.
   * The seed is fixed, so a failure repeats.
   */
  @Test
  fun aRestartMakesTheSameTokensAfterRandomEdits() {
    val random = Random(EDIT_SEED)
    val editAndCompare = { lexer: TextMateHighlightingLexer, text: String ->
      var edited = text
      repeat(EDIT_COUNT) {
        edited = randomEdit(edited, random)
        assertRestartMakesTheSameTokens(lexer, edited)
      }
    }
    withLexer(editAndCompare)
    withLexer(TestUtil.PHP_VSC, PHP_FILE_NAME, heredocText(HEREDOC_LABEL), editAndCompare)
  }

  /** Inserts or removes one character. [EDIT_CHARS] holds the characters that open or close a block. */
  private fun randomEdit(text: String, random: Random): String {
    val offset = random.nextInt(text.length)
    if (random.nextBoolean()) {
      return text.substring(0, offset) + EDIT_CHARS[random.nextInt(EDIT_CHARS.length)] + text.substring(offset)
    }
    return text.substring(0, offset) + text.substring(offset + 1)
  }

  private fun maxState(lexer: TextMateHighlightingLexer, text: String): Int {
    return statePerLine(lexer, text).filterNotNull().max()
  }

  private fun lineStartStates(lexer: TextMateHighlightingLexer, text: String): List<Int> {
    val lineStarts = lineStartsOf(text)
    val states = ArrayList<Int>()
    lexer.start(text)
    while (lexer.tokenType != null) {
      if (lexer.tokenStart in lineStarts && lexer.isRestartableState(lexer.state)) states.add(lexer.state)
      lexer.advance()
    }
    return states
  }

  /**
   * The state of every line start, in the order of the lines, or `null` where the lexer cannot restart.
   */
  private fun statePerLine(lexer: TextMateHighlightingLexer, text: String): List<Int?> {
    val lineStarts = lineStartsOf(text)
    val states = HashMap<Int, Int?>()
    lexer.start(text)
    while (lexer.tokenType != null) {
      val tokenStart = lexer.tokenStart
      if (tokenStart in lineStarts && !states.containsKey(tokenStart)) {
        states[tokenStart] = if (lexer.isRestartableState(lexer.state)) lexer.state else null
      }
      lexer.advance()
    }
    return lineStarts.sorted().map { states[it] }
  }

  private fun lineOf(text: String, offset: Int): Int {
    return text.substring(0, offset).count { it == '\n' }
  }

  private fun lineStartsOf(text: String): Set<Int> {
    val result = HashSet<Int>()
    if (text.isNotEmpty()) result.add(0)
    for (i in text.indices) {
      if (text[i] == '\n' && i + 1 < text.length) result.add(i + 1)
    }
    return result
  }

  private fun withLexer(body: (TextMateHighlightingLexer, String) -> Unit) {
    val file = File("${PlatformTestUtil.getCommunityPath()}/plugins/textmate/tests/testData/lexer/java/java.java")
    val text = StringUtil.convertLineSeparators(FileUtil.loadFile(file, StandardCharsets.UTF_8))
    withLexer(TestUtil.JAVA, file.name, text, body)
  }

  private fun withLexer(bundleName: String, fileName: String, text: String, body: (TextMateHighlightingLexer, String) -> Unit) {
    val builder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
    val matchers = builder.loadBundle(bundleName)
    val syntaxTable = builder.build()
    val rootScope = findScopeByFileName(matchers, fileName)

    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    TextMateSelectorWeigherImpl().caching().use { weigher ->
      val syntaxMatcher = TextMateSyntaxMatcherImpl(regexProvider, weigher)
      body(TextMateHighlightingLexer(syntaxTable.getLanguageDescriptor(rootScope), syntaxMatcher, -1), text)
    }
  }

  companion object {
    private const val EDIT_SEED = 20260911L
    private const val EDIT_COUNT = 20
    private const val EDIT_CHARS = "\"'`{}()[]/*\\ #<>;\n"
    private const val PHP_FILE_NAME = "heredoc.php_vsc"
    private const val HEREDOC_LABEL = "MYDOC"
    private const val HEREDOC_BODY = "<div>one</div>"

    /** The `heredoc_interior` rule of the php bundle closes on a back-reference to the [label]. */
    private fun heredocText(label: String): String {
      return """
        <?php
        echo 1;
        echo <<<$label
          $HEREDOC_BODY
        $label;
        echo 2;
        """.trimIndent() + "\n"
    }
  }
}
