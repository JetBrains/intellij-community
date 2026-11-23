// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.parser

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.LazyLexingContext
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.canLazyNodeBeReparsedIncrementally
import com.intellij.platform.syntax.createLexer
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parseLazyNode
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.tree.SyntaxBuilderFactory
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.platform.syntax.tree.parse
import com.intellij.platform.syntax.util.language.SyntaxElementLanguageProvider
import com.intellij.yaml.syntax.YamlSyntaxDefinition
import com.intellij.yaml.syntax.YamlSyntaxElementTypes
import com.intellij.yaml.syntax.lexer.YamlLexer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks the lazy parser of [YamlSyntaxElementTypes.MAPPING] and [YamlSyntaxElementTypes.SEQUENCE] on a tree of the syntax library.
 * The client of the check gives the node with the new text and the parent chain of the old tree.
 * It lexes the new text alone.
 * Then it parses the node with the lexer of the lazy parser.
 */
class YamlSyntaxBlockReparseTest {
  @Test
  fun `a new key reparses the mapping`() {
    assertTrue(isReparsed("a:\n  b: 1\n  c: 2\nd: 3\n", offset = 9, deleted = 0, inserted = "\n  e: 5"))
  }

  @Test
  fun `a dedent to the parent key is refused`() {
    assertTrue(!isReparsed("a:\n  b:\n    c: 1\n    d: 2\n", offset = 16, deleted = 0, inserted = "\nf: 4"))
  }

  /**
   * Each accepted reparse must give the subtree of a full parse of the new text.
   * Makes the changes at each offset of the parser and lexer test data and of [SAMPLES], in parallel.
   * A hunt adds the other test data and more edits, see [YamlReparseHunt].
   */
  @Test
  fun `each accepted reparse gives the tree of a full parse`() {
    val base = listOf("x", " ", "\n", "\n  ", "\n    ", "\n- ", "- ", ": ", "#", "\"", "|", "-", "{", "&", "\t")
    val insertions = if (YamlReparseHunt.wide) (base + YAML_HUNT_INSERTIONS).distinct() else base
    val deletions = if (YamlReparseHunt.wide) listOf(1, 2, 3, 4, 8) else listOf(1)
    val texts = yamlReparseTexts(SAMPLES)
    val accepted = AtomicInteger()
    val total = AtomicInteger()
    val failures = ConcurrentHashMap<String, String>()
    texts.flatMap { (name, text) -> (0..text.length step 200).map { Triple(name, text, it until minOf(text.length + 1, it + 200)) } }
      .parallelStream()
      .forEach { (name, text, offsets) ->
        val oldTree = parseYaml(text)
        for (offset in offsets) {
          for (change in yamlBruteForceChanges(text, offset, insertions, deletions)) {
            total.incrementAndGet()
            try {
              if (isReparsed(text, change.range.startOffset, change.range.length, change.replacement, oldTree)) accepted.incrementAndGet()
            }
            catch (e: Throwable) {
              failures.putIfAbsent("${escapeYaml(change.replacement)}:${change.range.length}", "$name, change $change: ${e.message}")
            }
          }
        }
      }
    println("Syntax block reparse: ${texts.size} texts, accepted $accepted of $total changes, ${failures.size} distinct failures")
    for (failure in failures.values) println("FAILURE: $failure\n")
    assertTrue(failures.isEmpty()) { failures.values.take(5).joinToString("\n\n") }
    assertTrue(accepted.get() > total.get() / 50, "accepted $accepted of $total")
  }

  /**
   * Tries each block node around the change, from the innermost one.
   * If the lazy parser accepts a node, checks the reparsed node against a full parse and returns true.
   */
  private fun isReparsed(text: String, offset: Int, deleted: Int, inserted: String, oldTree: SyntaxNode = parseYaml(text)): Boolean {
    val newText = text.substring(0, offset) + inserted + text.substring(offset + deleted)
    val delta = inserted.length - deleted
    var node: SyntaxNode? = deepestNode(oldTree, maxOf(0, offset - 1), minOf(offset + deleted, text.length - 1))
    while (node != null) {
      if (node.type === YamlSyntaxElementTypes.MAPPING || node.type === YamlSyntaxElementTypes.SEQUENCE) {
        val detached = DetachedNode(node, newText.substring(node.startOffset, node.endOffset + delta))
        if (canReparse(detached)) {
          checkReparse(detached, newText, "change at $offset, deleted $deleted, inserted '$inserted' in:\n$text")
          return true
        }
      }
      node = node.parent()
    }
    return false
  }

  /**
   * `KmpSyntaxNode.tryReparse` walks down from the root and stops at the first lazy node that the check refuses.
   * The first lazy node is the block node of the document, and the check refuses it, because it has no parent key.
   * So the walk does not get the lexer of the lazy parser, which lexes only the old text of the node.
   */
  @Test
  fun `the reparse of a syntax tree refuses a change in an inner mapping`() {
    val text = "a:\n  b: 1\n  c: 2\nd: 3\n"
    val offset = text.indexOf('1')
    // A replacement of the same length keeps the offsets of the old tree.
    val newText = text.replaceRange(offset, offset + 1, "2")
    val inner = findNode(parseYaml(text), 5, YamlSyntaxElementTypes.MAPPING)
    assertTrue(canReparse(DetachedNode(inner, newText.substring(inner.startOffset, inner.endOffset))), "the inner mapping alone")

    val newTokens = performLexing(newText, YamlLexer(), CANCELLATION, null)
    val builderFactory = SyntaxBuilderFactory { builderText, tokens, startOffset ->
      SyntaxTreeBuilderFactory.builder(builderText, tokens, YamlSyntaxDefinition.whitespaces, YamlSyntaxDefinition.comments)
        .withStartOffset(startOffset)
        .build()
    }
    val root = parse(text, YamlSyntaxDefinition, YAML, LANGUAGES)
    val suffixLength = newText.length - offset - 1
    assertNull(root.tryReparse(builderFactory, YamlLexer(), newTokens, newText, offset, suffixLength, CANCELLATION))
  }

  /** Two nodes with the same text at other columns: the check of one node gives no parse data to the other node. */
  @Test
  fun `the parse takes no check result of a node at another offset`() {
    val text = "a:\n  b: 1\nc:\n    b: 1\n"
    val root = parseYaml(text)
    val first = findNode(root, 5, YamlSyntaxElementTypes.MAPPING)
    val second = findNode(root, 17, YamlSyntaxElementTypes.MAPPING)
    assertEquals(first.text.toString(), second.text.toString())
    val newText = first.text.toString().replace("1", "12")
    assertTrue(canReparse(DetachedNode(first, newText)))
    checkReparse(DetachedNode(second, newText), text.substring(0, 17) + newText + "\n", "the second node")
  }

  /** A client can check a node and then not parse it. Then nothing must keep the new text of the node. */
  @Test
  fun `a check without a parse keeps no new text`() {
    val node = findNode(parseYaml("a:\n  b: 1\n  c: 2\nd: 3\n"), 5, YamlSyntaxElementTypes.MAPPING)
    val newText = WeakReference(checkNewText(node, node.text.toString().replace("1", "12")))
    repeat(100) {
      if (newText.get() == null) return
      System.gc()
      Thread.sleep(10)
    }
    fail<Unit>("the new text of the node is not collected")
  }

  /** Checks [node] with [newText] and returns the text, so that no local variable of the test keeps it. */
  private fun checkNewText(node: SyntaxNode, newText: CharSequence): CharSequence {
    assertTrue(canReparse(DetachedNode(node, newText)))
    return newText
  }

  /**
   * A client can parse a lazy node again, as the update of the injected fragments does after the merge of the trees.
   * The second parse has no check result in the thread, so it must still give the tree of a full parse.
   */
  @Test
  fun `a second parse of the node gives the tree of a full parse`() {
    val text = "a:\n  b: |\n    x\n  c: d\n"
    val node = findNode(parseYaml(text), 5, YamlSyntaxElementTypes.MAPPING)
    val newText = node.text.toString().replace("x", "xy")
    val detached = DetachedNode(node, newText)
    assertTrue(canReparse(detached))
    val fullText = text.replace("x", "xy")
    checkReparse(detached, fullText, "first parse")

    val lexer = createLexer(LazyLexingContext(detached, CANCELLATION)) ?: YamlLexer()
    val reparsed = parse(
      text = detached.text,
      lexerFactory = { lexer },
      parser = { builder -> parseLazyNode(LazyParsingContext(detached, builder.tokens, builder, CANCELLATION)) },
      whitespaces = YamlSyntaxDefinition.whitespaces,
      comments = YamlSyntaxDefinition.comments,
      documentLanguage = YAML,
      languageProvider = LANGUAGES,
    )
    val expected = findNode(parseYaml(fullText), detached.startOffset, detached.type, detached.text.length)!!
    assertEquals(dump(expected, expected.startOffset), dump(reparsed, 0), "second parse")
  }

  /** Lexes the text of [detached] alone, as a client does before the check, and runs the check of the lazy parser. */
  private fun canReparse(detached: DetachedNode): Boolean {
    val tokens = performLexing(detached.text, YamlLexer(), CANCELLATION, null)
    val builder = SyntaxTreeBuilderFactory.builder(detached.text, tokens, YamlSyntaxDefinition.whitespaces, YamlSyntaxDefinition.comments).build()
    return canLazyNodeBeReparsedIncrementally(LazyParsingContext(detached, tokens, builder, CANCELLATION))
  }

  /** Returns the node of [type] that starts at [start]. */
  private fun findNode(node: SyntaxNode, start: Int, type: SyntaxElementType): SyntaxNode {
    if (node.startOffset == start && node.type === type) return node
    var child = node.firstChild()
    while (child != null) {
      if (child.startOffset <= start && start < child.endOffset) return findNode(child, start, type)
      child = child.nextSibling()
    }
    throw AssertionError("no $type at $start")
  }

  private fun checkReparse(detached: DetachedNode, newText: String, message: String) {
    val lexer = createLexer(LazyLexingContext(detached, CANCELLATION))
    assertNotNull(lexer, "no lexer of the lazy parser: $message")
    val reparsed = parse(
      text = detached.text,
      lexerFactory = { lexer!! },
      parser = { builder -> parseLazyNode(LazyParsingContext(detached, builder.tokens, builder, CANCELLATION)) },
      whitespaces = YamlSyntaxDefinition.whitespaces,
      comments = YamlSyntaxDefinition.comments,
      documentLanguage = YAML,
      languageProvider = LANGUAGES,
    )
    val expected = findNode(parseYaml(newText), detached.startOffset, detached.type, detached.text.length)
    assertNotNull(expected, "a full parse has no ${detached.type} at ${detached.startOffset}: $message")
    assertEquals(dump(expected!!, expected.startOffset), dump(reparsed, 0), message)
  }

  private fun parseYaml(text: String): SyntaxNode = parse(text, YamlSyntaxDefinition, YAML, LANGUAGES)

  private fun deepestNode(root: SyntaxNode, start: Int, end: Int): SyntaxNode {
    var node = root
    while (true) {
      val child = node.childByOffset(start) ?: return node
      if (child.endOffset <= end || child.firstChild() == null) return node
      node = child
    }
  }

  private fun findNode(node: SyntaxNode, start: Int, type: SyntaxElementType, length: Int): SyntaxNode? {
    if (node.startOffset == start && node.type === type && node.endOffset - node.startOffset == length) return node
    var child = node.firstChild()
    while (child != null) {
      if (child.startOffset <= start && start < child.endOffset) findNode(child, start, type, length)?.let { return it }
      child = child.nextSibling()
    }
    return null
  }

  private fun dump(node: SyntaxNode, base: Int): String = buildString {
    fun visit(current: SyntaxNode, depth: Int) {
      append("  ".repeat(depth)).append(current.type).append(' ').append(current.startOffset - base).append('-').append(current.endOffset - base)
      if (current.firstChild() == null) append(" '").append(current.text.toString().replace("\n", "\\n")).append('\'')
      append('\n')
      var child = current.firstChild()
      while (child != null) {
        visit(child, depth + 1)
        child = child.nextSibling()
      }
    }
    visit(node, 0)
  }

  /** The node of the new text with the parent chain and the siblings of the old tree, as a client of the lazy parser gives it. */
  private class DetachedNode(private val oldNode: SyntaxNode, override val text: CharSequence) : SyntaxNode by oldNode {
    override val endOffset: Int get() = oldNode.startOffset + text.length
    override fun firstChild(): SyntaxNode? = null
    override fun lastChild(): SyntaxNode? = null
    override fun childByOffset(offset: Int): SyntaxNode? = null
  }
}

private val YAML = SyntaxLanguage("yaml")
private val LANGUAGES = SyntaxElementLanguageProvider { sequenceOf(YAML) }
private val CANCELLATION = CancellationProvider { }

private val SAMPLES = listOf(
  """
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
  |      responses:
  |
  |        "200":
  |          description: OK
  |    put:
  |    - x
  |top: value
  |""".trimMargin(),
)
