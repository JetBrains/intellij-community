// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLSyntaxHighlighter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes

/**
 * Makes each change at each offset of the YAML test data in a document with an editor highlighter.
 * The incremental update of the highlighter must give the tokens and the lexer states of a new highlighter of the new text.
 * The update restarts the lexer at a restartable state and stops at a restartable state of an old token.
 * So the check finds a wrong restartable state and a state that does not keep all the data that the next tokens need.
 * A hunt adds the other test data and more edits, see [YamlReparseHunt].
 */
@TestApplication
class YamlIncrementalLexingTest {
  @Test
  fun `each change keeps the tokens and the states of a new lexing`() = timeoutRunBlocking(if (YamlReparseHunt.wide) 60.minutes else 5.minutes) {
    val base = listOf("x", " ", "\n", "\n  ", "\n- ", "- ", ": ", "#", "\"", "'", "|", ">", "-", "?", "{", "}", "[", "]", ",", "\\", "&", "!", "\t", "---")
    val insertions = if (YamlReparseHunt.wide) (base + YAML_HUNT_INSERTIONS).distinct() else base
    val deletions = if (YamlReparseHunt.wide) listOf(1, 2, 3, 4, 8) else listOf(1, 3)
    val texts = yamlReparseTexts()
    val scheme = EditorColorsManager.getInstance().globalScheme
    val total = AtomicInteger()
    val failures = ConcurrentHashMap<String, MutableList<Pair<String, YamlChange>>>()
    LoggedErrorProcessor.executeWith(ErrorRecorder).use { runChanges(texts, insertions, deletions, scheme, total, failures) }
    val distinct = LinkedHashMap<String, String>()
    val deadline = System.nanoTime() + (if (YamlReparseHunt.wide) 10.minutes else 1.minutes).inWholeNanoseconds
    LoggedErrorProcessor.executeWith(ErrorRecorder).use {
      for ((text, change) in failures.values.flatten()) {
        val (minText, minChange) = minimizeYamlCase(text, change, deadline) { t, c -> difference(t, c, scheme) != null }
        distinct.putIfAbsent(escapeYaml(minText) + minChange,
                             "MINIMIZED: change $minChange of '${escapeYaml(minText.substring(minChange.range.startOffset, minChange.range.endOffset))}' " +
                             "in '${escapeYaml(minText)}': ${difference(minText, minChange, scheme)}")
      }
    }
    println("Incremental lexing: ${texts.size} texts, $total changes, ${distinct.size} distinct failures")
    for (failure in distinct.values) println("FAILURE: $failure\n")
    assertTrue(distinct.isEmpty()) { distinct.values.take(5).joinToString("\n\n") }
  }

  private suspend fun runChanges(
    texts: List<Pair<String, String>>,
    insertions: List<String>,
    deletions: List<Int>,
    scheme: EditorColorsScheme,
    total: AtomicInteger,
    failures: ConcurrentHashMap<String, MutableList<Pair<String, YamlChange>>>,
  ) {
    withContext(Dispatchers.Default) {
      coroutineScope {
        texts.flatMap { (name, text) -> (0..text.length step 200).map { Triple(name, text, it until minOf(text.length + 1, it + 200)) } }
          .map { (_, text, offsets) ->
            async {
              for (offset in offsets) {
                for (change in yamlBruteForceChanges(text, offset, insertions, deletions)) {
                  total.incrementAndGet()
                  if (difference(text, change, scheme) != null) {
                    val sameKey = failures.computeIfAbsent("${escapeYaml(change.replacement)}:${change.range.length}") { ArrayList() }
                    synchronized(sameKey) {
                      if (sameKey.size < FAILURES_PER_KEY && sameKey.none { it.first === text }) sameKey.add(text to change)
                    }
                  }
                }
              }
            }
          }.awaitAll()
      }
    }
  }

  /** Returns the first token where the updated highlighter differs from a new highlighter, or null. */
  private fun difference(text: String, change: YamlChange, scheme: EditorColorsScheme): String? {
    val document = DocumentImpl(text, true)
    val highlighter = LexerEditorHighlighter(YAMLSyntaxHighlighter(), scheme)
    highlighter.setEditor(object : HighlighterClient {
      override fun getProject(): Project? = null
      override fun repaint(start: Int, end: Int) = Unit
      override fun getDocument(): Document = document
    })
    highlighter.setText(document.immutableCharSequence)
    document.addDocumentListener(highlighter)
    ErrorRecorder.lastError.remove()
    try {
      document.replaceString(change.range.startOffset, change.range.endOffset, change.replacement)
    }
    catch (e: Throwable) {
      if (e is ProcessCanceledException || e is CancellationException) throw e
      return "exception in the update: $e"
    }
    ErrorRecorder.lastError.get()?.let { return "error in the update: $it" }
    val fresh = LexerEditorHighlighter(YAMLSyntaxHighlighter(), scheme)
    fresh.setText(document.immutableCharSequence)
    val expected = segments(fresh)
    val actual = segments(highlighter)
    val index = expected.indices.firstOrNull { expected[it] != actual.getOrNull(it) } ?: if (actual.size > expected.size) expected.size else return null
    return "token $index: expected ${expected.getOrNull(index)}, actual ${actual.getOrNull(index)}"
  }

  /** Returns the range, the token type and the lexer state of each segment. */
  private fun segments(highlighter: LexerEditorHighlighter): List<String> {
    val segments = highlighter.segments
    return (0 until segments.segmentCount).map { i ->
      val data = segments.getSegmentData(i)
      "${segments.getSegmentStart(i)}-${segments.getSegmentEnd(i)} ${segments.unpackTokenFromData(data)} state ${segments.unpackStateFromData(data)}"
    }
  }

  /**
   * Keeps the logged error of the current thread instead of a test failure.
   * The highlighter logs an error when the lexer gives an invalid token after a restart.
   */
  private object ErrorRecorder : LoggedErrorProcessor() {
    val lastError = ThreadLocal<String>()

    override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
      lastError.set(message + (t?.let { ": " + generateSequence(it) { e -> e.cause }.last() } ?: ""))
      return Action.NONE
    }
  }
}

/** The failures of one replacement that the test minimizes, each from another text. */
private const val FAILURES_PER_KEY = 3
