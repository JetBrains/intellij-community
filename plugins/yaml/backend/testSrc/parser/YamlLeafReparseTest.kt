// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Checks the reparse of one YAML leaf.
 * Each case has the old text with `<caret>`, or with `<selection>` and `</selection>`, and the text that replaces it.
 * Each case also commits the change and compares the tree with a parse of the new text.
 */
@TestApplication
class YamlLeafReparseTest {
  private val project = projectFixture(openAfterCreation = true)

  @Test
  fun `typing in a double-quoted scalar reparses the leaf`() = check("a: \"some<caret> text\"\n", "x", leaf = true)

  @Test
  fun `typing in a single-quoted scalar reparses the leaf`() = check("a: 'it''s<caret>'\n", "x", leaf = true)

  @Test
  fun `typing in a plain scalar reparses the leaf`() = check("a: some<caret> text\nb: c\n", "x", leaf = true)

  @Test
  fun `typing in a comment reparses the leaf`() = check("a: b # some<caret> text\n", ": x", leaf = true)

  @Test
  fun `typing in a plain scalar before a comment reparses the leaf`() = check("a: so<caret>me # text\n", "x", leaf = true)

  @Test
  fun `typing in a continuation line of a plain scalar reparses the leaf`() = check("a: first\n  sec<caret>ond\nb: c\n", "x", leaf = true)

  @Test
  fun `typing in a flow sequence item reparses the leaf`() = check("a: [fi<caret>rst, second]\n", "x", leaf = true)

  @Test
  fun `typing in a top-level plain scalar reparses the leaf`() = check("so<caret>me text\n", "x", leaf = true)

  @Test
  fun `a deletion in a double-quoted scalar reparses the leaf`() = check("a: \"some<selection> te</selection>xt\"\n", "", leaf = true)

  @Test
  fun `a colon and a space in a plain scalar make a full reparse`() = check("a: some<caret> text\n", ": ", leaf = false)

  @Test
  fun `a quote in a double-quoted scalar makes a full reparse`() = check("a: \"some<caret> text\"\nb: \"c\"\n", "\"", leaf = false)

  @Test
  fun `a line break in a double-quoted scalar makes a full reparse`() = check("a: \"some<caret> text\"\n", "\n", leaf = false)

  @Test
  fun `typing in a value with a colon after it makes a full reparse`() = check("a: {b: fi<caret>rst, c: second}\n", "x", leaf = false)

  @Test
  fun `typing in a key makes a full reparse`() = check("ke<caret>y: first\n  second\n", "xxx", leaf = false)

  @Test
  fun `a document marker at a line start makes a full reparse`() = check("a: b\n--<caret>x\n", "-", leaf = false)

  @Test
  fun `a hash after a space in a plain scalar makes a full reparse`() = check("a: some<caret>text\n", " #", leaf = false)

  @Test
  fun `a plain scalar becomes a sequence marker makes a full reparse`() = check("a:\n  - b\n  -<selection>x</selection>\n", "", leaf = false)

  @Test
  fun `a space at the end of a plain scalar makes a full reparse`() = check("a: some<caret>\nb: c\n", " ", leaf = false)

  @Test
  fun `a letter after a space at the end of a plain scalar makes a full reparse`() = check("a: some <caret>\nb: c\n", "w", leaf = false)

  @Test
  fun `a second hash after a plain scalar makes a full reparse`() = check("a: text #<caret> comment\n", "#", leaf = false)

  @Test
  fun `a backslash in a plain scalar reparses the leaf`() = check("a: some<caret> text\nb: c\n", "\\", leaf = true)

  @Test
  fun `a backslash in a comment reparses the leaf`() = check("a: b # some<caret> text\n", "\\", leaf = true)

  @Test
  fun `a backslash in a double-quoted scalar reparses the leaf`() = check("a: \"some<caret> text\"\n", "\\", leaf = true)

  @Test
  fun `a char after a backslash in a plain scalar reparses the leaf`() = check("a: C:\\<caret>\nb: c\n", "x", leaf = true)

  @Test
  fun `a backslash after a quote without a closing quote reparses the leaf`() =
    check("a: \"open\nb: some<caret> text\n", "\\", leaf = true)

  @Test
  fun `a quote in a comment of a line with a quote reparses the leaf`() = check("a: \"b\" # some<caret> text\n", "\"", leaf = true)

  @Test
  fun `a backslash before a quote in a double-quoted scalar makes a full reparse`() =
    check("a: \"some<caret>\"\nb: \"c\"\n", "\\", leaf = false)

  @Test
  fun `a quote after a quote without a closing quote makes a full reparse`() =
    check("a: \"open\nb: some<caret> text\n", "\"", leaf = false)

  @Test
  fun `a char between a backslash and a quote makes a full reparse`() =
    check("a: \"open\nb: some\\<caret>\" text\n", "x", leaf = false)

  @Test
  fun `typing in a word of a long plain scalar line makes a full reparse`() {
    val words = (0 until 30_000).joinToString(" ") { if (it == 15_000) "word1<caret>5000" else "word$it" }
    check("a: $words\nb: c\n", "x", leaf = false)
  }

  @Test
  fun `typing in a long one-token plain scalar makes a full reparse`() =
    check("a: ${"x".repeat(150_000)}<caret>${"x".repeat(150_000)}\nb: c\n", "y", leaf = false)

  @Test
  fun `typing in a 3 KB line of a small file reparses the leaf`() =
    check("a: ${"x".repeat(1_500)}<caret>${"x".repeat(1_500)}\nb: c\n", "y", leaf = true)

  @Test
  fun `a paste of a long text into a short plain scalar of a small file makes a full reparse`() =
    check("a: so<caret>me\nb: c\n", "x".repeat(10_000), leaf = false)

  @Test
  fun `a reparsed leaf keeps a copy of its text, not a view of the document`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val (text, range) = parseMarkers("a: so<caret>me\nb: c\n")
      val file = commit(text, range, "x")
      val leaf = file.node.findLeafElementAt(range.startOffset)!!
      assertTrue(leaf.chars is String) { "the leaf text is a ${leaf.chars.javaClass.name}" }
    }
  }

  /**
   * Makes each one-char insertion and each one-char deletion at each offset of the parser and lexer test data and of [SAMPLES].
   * If the leaf reparse accepts the change, the tree must be equal to a parse of the new text.
   * A hunt adds the other test data and more edits, see [YamlReparseHunt].
   */
  @Test
  fun `each accepted leaf reparse gives the tree of a full parse`() = timeoutRunBlocking(if (YamlReparseHunt.wide) 60.minutes else 5.minutes) {
    withContext(Dispatchers.EDT) {
      val chars = listOf("x", " ", ":", "#", "\"", "'", "-", ",", "[", "]", "{", "}", "\\", "!", "&", "*", "|", ">", "?", "%", "\t")
      val insertions = if (YamlReparseHunt.wide) (chars + YAML_HUNT_INSERTIONS).distinct() else chars
      val deletions = if (YamlReparseHunt.wide) listOf(1, 2, 3, 4) else listOf(1)
      val result = YamlReparseOracle(project.get()).bruteForce("Leaf brute force", yamlReparseTexts(SAMPLES), setOf(YamlReparseKind.LEAF)) { text, offset ->
        yamlBruteForceChanges(text, offset, insertions, deletions)
      }
      assertTrue(result.failures.isEmpty()) { result.failures.values.take(5).joinToString("\n\n") }
      assertTrue(result.accepted > result.total / 10, "accepted ${result.accepted} of ${result.total}")
    }
  }

  private fun check(textWithMarkers: String, replacement: String, leaf: Boolean) = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val (text, range) = parseMarkers(textWithMarkers)
      assertEquals(leaf, isLeafReparse(text, range, replacement), "leaf reparse")
      checkCommit(text, range, replacement)
    }
  }

  private fun isLeafReparse(text: String, range: TextRange, replacement: String): Boolean {
    val file = createFile(text) as PsiFileImpl
    val newText = range.replace(text, replacement)
    val changedRange = ChangedPsiRangeUtil.getChangedPsiRange(file, file.treeElement!!, newText) ?: return false
    val result = BlockSupportImpl.findReparseableNodeAndReparseIt(file, file.node, changedRange, newText)
    return result?.first is LeafElement
  }

  private fun checkCommit(text: String, range: TextRange, replacement: String) {
    val file = commit(text, range, replacement)
    val newText = range.replace(text, replacement)
    assertEquals(DebugUtil.psiToString(createFile(newText), true), DebugUtil.psiToString(file, true),
                 "change $range to '$replacement' in:\n$text")
  }

  /** Makes a file of [text], replaces [range] with [replacement] in its document, and commits the document. */
  private fun commit(text: String, range: TextRange, replacement: String): PsiFile {
    val file = createFile(text)
    val documentManager = PsiDocumentManager.getInstance(project.get())
    val document = documentManager.getDocument(file)!!
    CommandProcessor.getInstance().executeCommand(null, {
      WriteAction.run<Throwable> { document.replaceString(range.startOffset, range.endOffset, replacement) }
    }, null, null)
    documentManager.commitDocument(document)
    return file
  }

  private fun createFile(text: String): PsiFile = createFile(project.get(), text)

  private fun createFile(project: Project, text: String): PsiFile {
    val file = LightVirtualFile("test.yaml", YAMLLanguage.INSTANCE, text)
    return PsiManager.getInstance(project).findFile(file)!!
  }

  private fun parseMarkers(text: String): Pair<String, TextRange> {
    val caret = text.indexOf("<caret>")
    if (caret >= 0) return text.replace("<caret>", "") to TextRange(caret, caret)
    val start = text.indexOf("<selection>")
    val end = text.indexOf("</selection>") - "<selection>".length
    return text.replace("<selection>", "").replace("</selection>", "") to TextRange(start, end)
  }
}

private val SAMPLES = listOf(
  """
  |# A comment
  |openapi: "3.0.3"
  |info:
  |  title: 'It''s an API'
  |  description: some plain text # a comment
  |    with a second line
  |  x-tags: [one, two, "three"]
  |  x-map: {a: b, c: "d"}
  |paths:
  |  /items:
  |    get:
  |      - first
  |      - &anchor second
  |      - *anchor
  |      - !tag third
  |      - ? complex
  |        : value
  |    summary: |
  |      block text
  |      # not a comment
  |    other: > # header comment
  |      folded
  |text at line start
  |---
  |second: document
  |...
  |""".trimMargin(),
  """
  |a: "multi
  |  line" b
  |c: 'single
  |  line' 'x''y'
  |d: [one,
  |  two, {x: y}, [[[z]]]]
  |e: it's a "quoted" \" text # it's a comment
  |f: "open
  |g: it's \" text 'x # c
  |h: value
  |""".trimMargin(),
)
