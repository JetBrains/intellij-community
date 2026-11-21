// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.ChangedPsiRangeUtil
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YamlBlockCollectionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Checks the reparse of one YAML block mapping or block sequence.
 * Each case has the old text with `<caret>`, or with `<selection>` and `</selection>`, and the text that replaces it.
 * Each case also commits the change and compares the tree with a parse of the new text.
 */
@TestApplication
class YamlBlockReparseTest {
  private val project = projectFixture(openAfterCreation = true)

  @Test
  fun `typing in a key reparses the mapping`() = check("a:\n  b: 1\n  c<caret>: 2\nd: 3\n", "x", block = true)

  @Test
  fun `a new line in a mapping reparses the mapping`() = check("a:\n  b: 1<caret>\n  c: 2\nd: 3\n", "\n  ", block = true)

  @Test
  fun `a new key reparses the mapping`() = check("a:\n  b: 1<caret>\n  c: 2\nd: 3\n", "\n  e: 5", block = true)

  @Test
  fun `a new sequence item reparses the sequence`() = check("a:\n  - x<caret>\n  - y\nb: c\n", "\n  - z", block = true)

  @Test
  fun `a sequence at the column of its key reparses the sequence`() = check("a:\n- x<caret>\n- y\nb: c\n", "\n- z", block = true)

  @Test
  fun `typing in a block scalar reparses the mapping`() = check("a:\n  b: |\n    te<caret>xt\n  c: d\n", "x", block = true)

  @Test
  fun `typing in the value of a sequence item reparses the mapping`() = check("a:\n  -\n    b: 1<caret>\n    c: 2\nd: e\n", "\n    f: 3", block = true)

  @Test
  fun `a mapping after a comment line reparses the mapping`() = check("a:\n  # comment\n  b: 1\n  c<caret>: 2\nd: 3\n", "x", block = true)

  @Test
  fun `a mapping after a blank line reparses the mapping`() = check("a:\n\n  b: 1\n  c<caret>: 2\nd: 3\n", "x", block = true)

  @Test
  fun `a key that leaves the inner mapping reparses the outer mapping`() =
    check("a:\n  b:\n    c: 1<caret>\n    d: 2\n  e: 3\nf: 4\n", "\n  g: 5", block = true)

  @Test
  fun `a plain text from an earlier line keeps the tree of a full parse`() =
    check("x: foo\n  bar:\n         baz: 1<caret>\n         q: 2\nz: 1\n", "\n         - y", block = null)

  @Test
  fun `a chameleon without the check data is not a valid reparse`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val file = createFile("a:\n  b: 1\nc: 2\n")
      val mapping = findBlockAt(file, 5)
      val type = mapping.elementType as YamlBlockCollectionType
      val chameleon = type.parse("b: 12\n", SharedImplUtil.findCharTableByTree(mapping))
      assertFalse(type.isValidReparse(mapping, chameleon))
    }
  }

  @Test
  fun `a chameleon without the check data in the tree logs an error and gets the tree of a full parse`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val file = createNonPhysicalFile("a:\n  b: 1\n  c: 2\nd: 3\n")
      val mapping = findBlockAt(file, 5)
      val type = mapping.elementType as YamlBlockCollectionType
      val newText = mapping.text.replace("b: 1", "b: 12")
      val expected = mapping.textRange.replace(file.text, newText)
      val chameleon = type.parse(newText, SharedImplUtil.findCharTableByTree(mapping)) as TreeElement
      WriteAction.run<Throwable> {
        DebugUtil.performPsiModification<Throwable>("replace the mapping") { (mapping as TreeElement).rawReplaceWithList(chameleon) }
      }
      assertErrorLogged<Throwable> { chameleon.firstChildNode }
      assertEquals(DebugUtil.psiToString(createNonPhysicalFile(expected), true), DebugUtil.psiToString(file, true))
    }
  }

  @Test
  fun `a key that changes the indent of a block scalar header makes a full reparse`() =
    check("r:\n   a: <caret>b\n   >\n1\n    -\n", "k: v", block = false)

  @Test
  fun `a blank line with an indent after an empty sequence item at the end of the file makes a full reparse`() =
    check("-\n -\n<selection>\n</selection>\n\n", "  ", block = false)

  @Test
  fun `a tab at the line start after an empty sequence item keeps the tree of a full parse`() =
    check(" y:\n  -\n<caret> :", "\t", block = null)

  @Test
  fun `a tab before a plain scalar after an empty sequence item keeps the tree of a full parse`() =
    check(" y:\n  -\n<caret> y", "\t", block = null)

  @Test
  fun `a comment line after an empty sequence item at the end of the file makes a full reparse`() =
    check("r:\n-<caret> \n\n", "\n  # c", block = false)

  @Test
  fun `a tag that ends with a colon at the end of the file makes a full reparse`() =
    check("a:\n- !<selection>!</selection>\n\n", ":", block = false)

  /**
   * The inner mapping refuses the new key before the full check, because the key is below its indent.
   * This refusal reads no leaves of the inner mapping, so it uses no budget, and the outer mapping is checked.
   * The sibling key `g` keeps the outer mapping below half of the file.
   */
  @Test
  fun `a large outer mapping after a cheap refusal of a large inner mapping reparses the outer mapping`() {
    val keys = (1..12_000).joinToString("") { "    c$it: 1\n" }
    val siblingKeys = (1..12_000).joinToString("") { "  c$it: 1\n" }
    check("a:\n  b:\n    c0: 1<caret>\n$keys  e: 3\nf: 4\ng:\n$siblingKeys", "\n  g: 5", block = true, blockStart = 5)
  }

  /** The first key of the inner mapping becomes a plain scalar, so the full check refuses the inner mapping. */
  @Test
  fun `a text after the colon of the first inner key reparses the outer mapping`() =
    check("a:\n  b:\n    c0:<caret> 1\n    c1: 1\n  e: 3\nf: 4\n", "x", block = true, blockStart = 5)

  /**
   * The full check of the inner mapping reads all of its node, and it refuses the change.
   * The full checks of one change read at most half of the file and 64 KB, so the outer mapping is not checked.
   */
  @Test
  fun `a large outer mapping after a refused full check of a large inner mapping makes a full reparse`() {
    val keys = (1..12_000).joinToString("") { "    c$it: 1\n" }
    check("a:\n  b:\n    c0:<caret> 1\n$keys  e: 3\nf: 4\n", "x", block = false)
  }

  /**
   * The mapping under `en:` is almost all of the file.
   * Its full check reads more than half of the file and 64 KB, so it is not checked, also as the first full check of the change.
   */
  @Test
  fun `a key in a mapping of almost all of a big file makes a full reparse`() {
    val keys = bigMappingKeys().replace("  key_10000:", "  key_10<caret>000:")
    check("en:\n${keys}b: c\n", "x", block = false)
  }

  /** The pasted keys are longer than half of the file and 64 KB, so the full check does not run on the new text. */
  @Test
  fun `a paste of many keys into a small mapping of a small file makes a full reparse`() {
    val keys = (0 until 10_000).joinToString("") { "\n  key_$it: v" }
    check("a:\n  b: 1<caret>\n  c: 2\nd: 3\n", keys, block = false)
  }

  @Test
  fun `a key in a small inner mapping of a big file reparses the mapping`() =
    check("en:\n${bigMappingKeys()}  inner:\n    a: 1\n    b<caret>: 2\nz: c\n", "x", block = true)

  @Test
  fun `a key in the explicit key of a pair with an anchor keeps the tree of a full parse`() =
    check("&a ?\n k<caret>:", "a: ", block = null)

  @Test
  fun `a key in the explicit key of a pair with a tag keeps the tree of a full parse`() =
    check("! ?\n k<caret>:", ": ", block = null)

  @Test
  fun `a key in a flow sequence at the indent of a block keeps the tree of a full parse`() =
    check("-\n  -\n  [<caret> ]e: t\n  ", "a: ", block = null)

  @Test
  fun `a deleted blank line before a sequence item at the end of the file keeps the tree of a full parse`() =
    check("x\n t:\n  \n <selection>\n </selection> - ", "", block = null)

  @Test
  fun `a quote makes a full reparse`() = check("a:\n  b: 1\n  c<caret>: 2\nd: 3\n", "\"", block = false)

  /**
   * The line after the mapping has a line break only after a quoted text of 100 KB.
   * The text after the mapping up to this line break is longer than 64 KB, so the check refuses the mapping.
   */
  @Test
  fun `a key before a long quoted line makes a full reparse`() =
    check("a:\n  b: 1\n  c<caret>: 2\nd: \"${"x".repeat(100_000)}\n  y\"\n", "x", block = false)

  @Test
  fun `a backslash in a key reparses the mapping`() = check("a:\n  b: 1\n  c<caret>: 2\nd: 3\n", "\\", block = true)

  @Test
  fun `a quote in a mapping with a quote reparses the mapping`() = check("a:\n  b: \"x\"\n  c<caret>: 2\nd: 3\n", "\"", block = true)

  @Test
  fun `a quote after a quote without a closing quote makes a full reparse`() =
    check("x: \"open\na:\n  b: 1\n  c<caret>: 2\nd: 3\n", "\"", block = false)

  @Test
  fun `a backslash before an escaped quote after a quote without a closing quote makes a full reparse`() =
    check("x: \"open\na:\n  b: 1\n  c: \\<caret>\"\nd: 3\n", "\\", block = false)

  @Test
  fun `a new top-level key makes a full reparse`() = check("a: 1<caret>\nb: 2\n", "\nc: 3", block = false)

  @Test
  fun `a change in a flow mapping makes a full reparse`() = check("a: {b: [x, y<caret>]}\n", "\n  - z", block = false)

  @Test
  fun `a dedent to the parent key makes a full reparse`() = check("a:\n  b:\n    c: 1<caret>\n    d: 2\n", "\nf: 4", block = false)

  /**
   * Makes many insertions and deletions at each offset of the parser and lexer test data and of [SAMPLES].
   * If the block reparse or the reparse of another node accepts the change, the tree must be equal to a parse of the new text.
   * The test does not check a full reparse, because it is the baseline of the incremental reparse.
   * A hunt adds the other test data and more edits, see [YamlReparseHunt].
   */
  @Test
  fun `each accepted block reparse gives the tree of a full parse`() = timeoutRunBlocking(if (YamlReparseHunt.wide) 60.minutes else 5.minutes) {
    withContext(Dispatchers.EDT) {
      val base = listOf("x", " ", "\n", "\n  ", "\n    ", "\n- ", "\n  - ", "- ", ": ", "a: ", "#", "\"", "'", "|", "-", "?", "{", "[", "&", "!", "\t")
      val insertions = if (YamlReparseHunt.wide) (base + YAML_HUNT_INSERTIONS).distinct() else base
      val deletions = if (YamlReparseHunt.wide) listOf(1, 2, 3, 4, 8) else listOf(1, 3)
      val result = YamlReparseOracle(project.get()).bruteForce("Block brute force", yamlReparseTexts(SAMPLES), setOf(YamlReparseKind.BLOCK, YamlReparseKind.OTHER)) { text, offset ->
        yamlBruteForceChanges(text, offset, insertions, deletions)
      }
      assertTrue(result.failures.isEmpty()) { result.failures.values.take(5).joinToString("\n\n") }
      assertTrue(result.accepted > result.total / 50, "accepted ${result.accepted} of ${result.total}")
    }
  }

  /**
   * @param block the expected kind of the reparse: true for a block reparse, false for no block reparse, null for any
   * @param blockStart the expected start offset of the reparsed block in the old text, or null for any block
   */
  private fun check(textWithMarkers: String, replacement: String, block: Boolean?, blockStart: Int? = null) = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val (text, range) = parseMarkers(textWithMarkers)
      val reparsedBlock = findReparsedBlock(text, range, replacement)
      if (block != null) assertEquals(block, reparsedBlock != null, "block reparse")
      if (blockStart != null) assertEquals(blockStart, reparsedBlock?.startOffset, "start of the reparsed block")
      checkCommit(text, range, replacement)
    }
  }

  /** Returns the old node of the block reparse, or null if the reparse is not a block reparse. */
  private fun findReparsedBlock(text: String, range: TextRange, replacement: String): ASTNode? {
    val file = createFile(text) as PsiFileImpl
    val newText = range.replace(text, replacement)
    val changedRange = ChangedPsiRangeUtil.getChangedPsiRange(file, file.treeElement!!, newText) ?: return null
    val oldNode = BlockSupportImpl.findReparseableNodeAndReparseIt(file, file.node, changedRange, newText)?.first ?: return null
    return oldNode.takeIf { it.elementType is YamlBlockCollectionType }
  }

  private fun checkCommit(text: String, range: TextRange, replacement: String) {
    val file = createFile(text)
    val documentManager = PsiDocumentManager.getInstance(project.get())
    val document = documentManager.getDocument(file)!!
    CommandProcessor.getInstance().executeCommand(null, {
      WriteAction.run<Throwable> { document.replaceString(range.startOffset, range.endOffset, replacement) }
    }, null, null)
    documentManager.commitDocument(document)
    val newText = range.replace(text, replacement)
    assertEquals(DebugUtil.psiToString(createFile(newText), true), DebugUtil.psiToString(file, true),
                 "change $range to '$replacement' in:\n$text")
  }

  private fun createFile(text: String): PsiFile = createFile(project.get(), text)

  private fun createFile(project: Project, text: String): PsiFile {
    val file = LightVirtualFile("test.yaml", YAMLLanguage.INSTANCE, text)
    return PsiManager.getInstance(project).findFile(file)!!
  }

  private fun createNonPhysicalFile(text: String): PsiFile =
    PsiFileFactory.getInstance(project.get()).createFileFromText("test.yaml", YAMLLanguage.INSTANCE, text)

  /** Returns the innermost block mapping or block sequence at [offset] of [file]. */
  private fun findBlockAt(file: PsiFile, offset: Int): ASTNode =
    generateSequence(file.node.findLeafElementAt(offset)) { it.treeParent }.first { it.elementType is YamlBlockCollectionType }

  private fun parseMarkers(text: String): Pair<String, TextRange> {
    val caret = text.indexOf("<caret>")
    if (caret >= 0) return text.replace("<caret>", "") to TextRange(caret, caret)
    val start = text.indexOf("<selection>")
    val end = text.indexOf("</selection>") - "<selection>".length
    return text.replace("<selection>", "").replace("</selection>", "") to TextRange(start, end)
  }
}

/** 20,000 lines of keys with the indent of a mapping under a root key, about 280 KB. */
private fun bigMappingKeys(): String = (0 until 20_000).joinToString("") { "  key_$it: v\n" }

private val SAMPLES = listOf(
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
)
