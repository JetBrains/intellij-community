// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.ChangedPsiRangeUtil
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YamlBlockCollectionType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * Makes random sequences of edits in the YAML files of the test data.
 * After each edit, the committed tree must be equal to a parse of the new text,
 * and the tokens of the editor highlighter must be equal to the tokens of a new highlighter.
 * A failure that also occurs with the leaf reparse and the block reparse off is not counted.
 */
@TestApplication
class YamlReparseFuzzTest {
  private val project = projectFixture(openAfterCreation = true)

  /**
   * The normal run makes [SEQUENCES_PER_FILE] sequences per file.
   * A hunt with `yaml.reparse.hunt.minutes` runs rounds of new seeds until the time is over, see [YamlReparseHunt].
   * Each round takes the next seed of each file, so parallel runs with other first seeds make other sequences.
   */
  @Test
  fun `random edits keep the tree of a full parse and the tokens of the highlighter`() = timeoutRunBlocking((YamlReparseHunt.minutes + 20).minutes) {
    withContext(Dispatchers.EDT) {
      val files = yamlReparseTexts(EXTRA_SAMPLES)
      val failures = LinkedHashMap<String, String>()
      var edits = 0
      var restarts = 0
      val stats = HashMap<String, Int>()
      val firstSeed = YamlReparseHunt.seed ?: if (YamlReparseHunt.minutes > 0) Random.nextLong(1L shl 40) else 0L
      val deadline = System.nanoTime() + YamlReparseHunt.minutes * 60_000_000_000L
      var round = 0L
      while (if (YamlReparseHunt.minutes > 0) System.nanoTime() < deadline else round < SEQUENCES_PER_FILE) {
        val seed = firstSeed + round
        for ((name, original) in files) {
          val random = Random(name.hashCode() * 31L + seed)
          val failure = runSequence(original, random, stats, onRestart = { restarts++ }) { edits++ }
          if (failure != null) {
            failures.putIfAbsent(failure.first, "$name seed $seed: ${failure.second}")
          }
        }
        round++
      }
      println("Fuzz made $edits edits in ${files.size} files, seeds $firstSeed until ${firstSeed + round}, reparse kinds: $stats, " +
              "$restarts restarts after a tree that is also wrong without the incremental reparse, ${failures.size} distinct failures")
      for (message in failures.values) println("FAILURE: $message\n")
      assertTrue(failures.isEmpty()) { failures.values.take(5).joinToString("\n\n") }
    }
  }

  /**
   * Returns a key and a message of the first failure, or null.
   * A tree that is also wrong without the incremental reparse is not a failure.
   * Then the sequence calls [onRestart] and goes on in a new file of the current text.
   */
  private fun runSequence(
    original: String,
    random: Random,
    stats: MutableMap<String, Int>,
    onRestart: () -> Unit,
    onEdit: () -> Unit,
  ): Pair<String, String>? {
    val documentManager = PsiDocumentManager.getInstance(project.get())
    var edited = EditedFile(original)
    try {
      for (step in 0 until EDITS_PER_SEQUENCE) {
        val file = edited.file
        val document = edited.document
        val highlighter = edited.highlighter
        val before = document.text
        val (range, replacement) = randomEdit(before, random)
        stats.merge(reparseKind(before, range, replacement), 1, Int::plus)
        onEdit()
        CommandProcessor.getInstance().executeCommand(null, {
          WriteAction.run<Throwable> { document.replaceString(range.startOffset, range.endOffset, replacement) }
        }, null, null)
        documentManager.commitDocument(document)
        val after = document.text

        val expectedTree = DebugUtil.psiToString(createFile(after), true)
        val actualTree = DebugUtil.psiToString(file, true)
        val treeIsWrong = expectedTree != actualTree
        if (treeIsWrong) {
          val freshKind = reparseKind(before, range, replacement)
          val fresh = isWrongAfterOneEdit(before, range, replacement)
          val withoutIncremental = withReparseOff { isWrongAfterOneEdit(before, range, replacement) }
          if (!withoutIncremental) {
            val minimized = if (fresh) minimize(before, range, replacement) else null
            val message = (minimized?.let { (text, r) -> "MINIMIZED: change $r '${escape(text.substring(r.startOffset, r.endOffset))}' to '${escape(replacement)}' in '${escape(text)}'\n" } ?: "") +
                          "tree after step $step ($freshKind, wrong from a fresh tree: $fresh), " +
                          "change $range '${escape(before.substring(range.startOffset, range.endOffset))}' to '${escape(replacement)}' in:\n" +
                          "<<<\n$before\n>>>\n" + firstDifference(expectedTree, actualTree)
            return "tree:$freshKind:$fresh:${minimized?.first?.let(::escape) ?: escape(replacement)}" to message
          }
        }

        val expectedTokens = tokens(createHighlighter(file, after))
        val actualTokens = tokens(highlighter)
        if (expectedTokens != actualTokens) {
          val index = expectedTokens.indices.firstOrNull { expectedTokens.getOrNull(it) != actualTokens.getOrNull(it) } ?: expectedTokens.size
          val message = "highlighter after step $step, change $range to '${escape(replacement)}' in:\n<<<\n$before\n>>>\n" +
                        "expected ${expectedTokens.getOrNull(index)}, actual ${actualTokens.getOrNull(index)}"
          return "highlighter:${escape(replacement)}" to message
        }

        if (treeIsWrong) {
          onRestart()
          Disposer.dispose(edited)
          edited = EditedFile(after)
        }
      }
      return null
    }
    finally {
      Disposer.dispose(edited)
    }
  }

  /** A file of [text], its document, and an editor highlighter that the document updates. */
  private inner class EditedFile(text: String) : Disposable {
    val file: PsiFile = createFile(text)
    val document: Document = PsiDocumentManager.getInstance(project.get()).getDocument(file)!!
    val highlighter: EditorHighlighter = createHighlighter(file, document)

    init {
      document.addDocumentListener(highlighter as LexerEditorHighlighter, this)
    }

    override fun dispose() = Unit
  }

  /** Removes lines and chars outside of the change while a fresh tree still gets a wrong reparse. */
  private fun minimize(text: String, range: TextRange, replacement: String): Pair<String, TextRange> {
    fun fails(t: String, r: TextRange): Boolean = isWrongAfterOneEdit(t, r, replacement) && !withReparseOff { isWrongAfterOneEdit(t, r, replacement) }
    var currentText = text
    var currentRange = range
    var changed = true
    while (changed) {
      changed = false
      for (length in listOf(8, 4, 2, 1)) {
        var start = 0
        while (start + length <= currentText.length) {
          val end = start + length
          val overlaps = start < currentRange.endOffset && end > currentRange.startOffset ||
                         start == currentRange.startOffset && currentRange.isEmpty
          if (!overlaps) {
            val candidate = currentText.removeRange(start, end)
            val candidateRange = if (end <= currentRange.startOffset) currentRange.shiftLeft(length) else currentRange
            if (fails(candidate, candidateRange)) {
              currentText = candidate
              currentRange = candidateRange
              changed = true
              continue
            }
          }
          start++
        }
      }
    }
    return currentText to currentRange
  }

  private fun isWrongAfterOneEdit(before: String, range: TextRange, replacement: String): Boolean {
    val file = createFile(before)
    val documentManager = PsiDocumentManager.getInstance(project.get())
    val document = documentManager.getDocument(file)!!
    CommandProcessor.getInstance().executeCommand(null, {
      WriteAction.run<Throwable> { document.replaceString(range.startOffset, range.endOffset, replacement) }
    }, null, null)
    documentManager.commitDocument(document)
    return DebugUtil.psiToString(createFile(document.text), true) != DebugUtil.psiToString(file, true)
  }

  private fun reparseKind(text: String, range: TextRange, replacement: String): String {
    val file = createFile(text) as PsiFileImpl
    val newText = range.replace(text, replacement)
    val changedRange = ChangedPsiRangeUtil.getChangedPsiRange(file, file.treeElement!!, newText) ?: return "none"
    val result = BlockSupportImpl.reparseIncrementally(file, file.node, changedRange, newText) ?: return "full"
    return when {
      result.oldNode is LeafElement -> "leaf"
      result.oldNode.elementType is YamlBlockCollectionType -> "block"
      else -> "other:" + result.oldNode.elementType
    }
  }

  private fun <T> withReparseOff(action: () -> T): T {
    val disposable = Disposer.newDisposable()
    try {
      Registry.get("yaml.reparse.leaves").setValue(false, disposable)
      Registry.get("yaml.reparse.collections").setValue(false, disposable)
      return action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun randomEdit(text: String, random: Random): Pair<TextRange, String> {
    val offset = random.nextInt(text.length + 1)
    val lineStart = text.lastIndexOf('\n', offset - 1) + 1
    val lineIndent = text.substring(lineStart).takeWhile { it == ' ' }
    return when (random.nextInt(14)) {
      in 0..4 -> TextRange(offset, offset) to INSERTIONS.random(random)
      5 -> TextRange(offset, offset) to "\n" + lineIndent + listOf("k: v", "- x", "k:", "  k: v", "x", "# c", "? k", ": v", "- k: v").random(random)
      6 -> {
        // A copy of a random piece of the text, as a paste.
        val from = random.nextInt(text.length + 1)
        val to = minOf(text.length, from + random.nextInt(40))
        TextRange(offset, offset) to text.substring(from, to)
      }
      7 -> {
        // A deletion of a whole line.
        val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else minOf(text.length, it + 1) }
        TextRange(lineStart, lineEnd) to ""
      }
      in 8..10 -> {
        val end = minOf(text.length, offset + 1 + random.nextInt(4))
        TextRange(offset, end) to ""
      }
      else -> {
        val end = minOf(text.length, offset + 1 + random.nextInt(3))
        TextRange(offset, end) to INSERTIONS.random(random)
      }
    }
  }

  private fun createHighlighter(file: PsiFile, document: Document): EditorHighlighter {
    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project.get(), file.virtualFile)
    highlighter.setEditor(object : HighlighterClient {
      override fun getProject(): Project = this@YamlReparseFuzzTest.project.get()
      override fun repaint(start: Int, end: Int) = Unit
      override fun getDocument(): Document = document
    })
    highlighter.setText(document.immutableCharSequence)
    return highlighter
  }

  private fun createHighlighter(file: PsiFile, text: String): EditorHighlighter {
    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project.get(), file.virtualFile)
    highlighter.setText(text)
    return highlighter
  }

  private fun tokens(highlighter: EditorHighlighter): List<String> {
    val result = ArrayList<String>()
    val iterator = highlighter.createIterator(0)
    while (!iterator.atEnd()) {
      result.add("${iterator.start}-${iterator.end} ${iterator.tokenType}")
      iterator.advance()
    }
    return result
  }

  private fun createFile(text: String): PsiFile {
    val file = LightVirtualFile("test.yaml", YAMLLanguage.INSTANCE, text)
    return PsiManager.getInstance(project.get()).findFile(file)!!
  }

  private fun firstDifference(expected: String, actual: String): String {
    val expectedLines = expected.lines()
    val actualLines = actual.lines()
    val index = expectedLines.indices.firstOrNull { expectedLines[it] != actualLines.getOrNull(it) } ?: expectedLines.size
    val from = maxOf(0, index - 3)
    return "expected:\n" + expectedLines.subList(from, minOf(expectedLines.size, index + 4)).joinToString("\n") +
           "\nactual:\n" + actualLines.subList(minOf(from, actualLines.size), minOf(actualLines.size, index + 4)).joinToString("\n")
  }

  private fun escape(text: String): String = text.replace("\n", "\\n").replace("\t", "\\t")
}

private const val SEQUENCES_PER_FILE = 60
private const val EDITS_PER_SEQUENCE = 40

private val INSERTIONS = listOf(
  "x", "x", "x", "ab", " ", " ", "\n", "\n  ", "\n    ", "\n- ", "\n  - ", "- ", "-", ": ", ":", "a: ", "k: v", "#", "# c",
  "\"", "'", "|", ">", "?", "? ", "{", "}", "[", "]", ",", "&a ", "*a", "!t ", "\t", "---", "...", "\\", "  ", "%", "@",
)

private val EXTRA_SAMPLES = listOf(
  """
  |openapi: "3.0.3"
  |paths:
  |  /items:
  |    get:
  |      summary: Get
  |      # a comment
  |      tags:
  |        - one
  |        - two: 2
  |          three: 3
  |      text: |
  |        block
  |          more
  |      responses:
  |
  |        "200":
  |          description: OK
  |    put:
  |    - x
  |    - ? k
  |      : v
  |top: value
  |""".trimMargin(),
  """
  |a: plain
  |  continued
  |  b:
  |       c: 1
  |       d: 'q'
  |e:
  |  - &anchor
  |    f: *anchor
  |  - !tag
  |    g: [1, 2]
  |  -   h: {i: j}
  |      k: l
  |---
  |m:
  |  n: o
  |""".trimMargin(),
  """
  |x: foo
  |  bar:
  |         baz: 1
  |         q: 2
  |z: 1
  |a:
  |    b: |
  |      text
  |    c: >
  |      folded
  |    d:
  |      - e
  |      - f: g
  |        h: i
  |""".trimMargin(),
)
