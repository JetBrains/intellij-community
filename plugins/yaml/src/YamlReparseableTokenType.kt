// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IReparseableLeafElementType
import com.intellij.yaml.syntax.lexer.canReplaceYamlToken
import org.jetbrains.annotations.ApiStatus

/**
 * A YAML token whose leaf gets a new text without a reparse of the file, if the lexer reads the new text as the same token.
 * The leaf gets the new text only when its line is short against the file, see [MIN_LEAF_LINE_LIMIT].
 * See [canReplaceYamlToken] for the check.
 */
@ApiStatus.Internal
class YamlReparseableTokenType(debugName: String, private val syntaxType: SyntaxElementType) :
  YAMLElementType(debugName), IReparseableLeafElementType<ASTNode> {

  override fun reparseLeaf(leaf: ASTNode, newText: CharSequence): ASTNode? {
    if (!RegistryManager.getInstance().`is`("yaml.reparse.leaves")) return null
    if (!isInPlainYamlFile(leaf)) return null

    val fileLength = TreeUtil.getFileElement(leaf)?.textLength ?: return null
    val limit = maxOf(MIN_LEAF_LINE_LIMIT, fileLength / 16)
    val leafLength = maxOf(leaf.textLength, newText.length)
    if (leafLength > limit) return null

    val linePrefix = linePrefix(leaf, limit - leafLength) ?: return null
    val lineTail = lineTail(leaf, limit - leafLength - linePrefix.length) ?: return null
    if (!canReplaceYamlToken(syntaxType, linePrefix, leaf.chars, newText, lineTail)) return null

    // A copy, because the new text can be a view of the document, and the leaf must not keep the document text reachable.
    val newLeaf = ASTFactory.leaf(this, newText.toString())
    return if (newLeaf.javaClass == leaf.javaClass) newLeaf else null
  }

  /**
   * Returns the text from the line start up to [leaf].
   * Returns null if the line starts in a token of the previous line, or if the text is longer than [budget].
   */
  private fun linePrefix(leaf: ASTNode, budget: Int): CharSequence? {
    val prefix = ArrayList<CharSequence>()
    var length = 0
    var previous = TreeUtil.prevLeaf(leaf)
    while (previous != null) {
      val chars = previous.chars
      if (chars.endsWith('\n')) break
      length += chars.length
      if (length > budget || chars.contains('\n')) return null
      prefix.add(chars)
      previous = TreeUtil.prevLeaf(previous)
    }
    return prefix.asReversed().joinToString("")
  }

  /**
   * Returns the text after [leaf] up to and including the line break.
   * Returns null if the line has no line break, or if the text is longer than [budget].
   */
  private fun lineTail(leaf: ASTNode, budget: Int): CharSequence? {
    val tail = StringBuilder()
    var next = TreeUtil.nextLeaf(leaf)
    while (next != null) {
      val chars = next.chars
      val rest = budget - tail.length
      val lineBreak = StringUtil.indexOf(chars, '\n', 0, rest)
      if (lineBreak >= 0) {
        return tail.append(chars, 0, lineBreak + 1)
      }
      if (chars.length > rest) return null
      tail.append(chars)
      next = TreeUtil.nextLeaf(next)
    }
    return null
  }
}

/**
 * The least value of the line limit of the leaf check.
 * The check lexes the line up to 2 × (closing braces + 4) times, so a long line costs more than a full reparse of the file.
 * So [YamlReparseableTokenType] refuses a line longer than the larger of this value and 1/16 of the file length.
 * The divisor 16 keeps the check below about half of the cost of a full reparse.
 * The walks of the line prefix and the line tail stop at the limit, so a refused line costs at most the limit.
 * The leaf length is the larger of the old and the new leaf length, so the limit also refuses a long paste into a short leaf.
 * The file length is the length before the change.
 */
private const val MIN_LEAF_LINE_LIMIT = 4 * 1024

/**
 * Checks that [node] is in a file with only the YAML language.
 * A template file lexes its YAML text without the template parts, so the text of the tree is not the text of the lexer.
 */
internal fun isInPlainYamlFile(node: ASTNode): Boolean {
  val file = node.psi.containingFile ?: return false
  return file.node.elementType === YAML_FILE && file.viewProvider.languages.size == 1
}
