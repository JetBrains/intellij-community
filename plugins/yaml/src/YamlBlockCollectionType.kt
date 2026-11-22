// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.lexer.tokenListLexer
import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory
import com.intellij.platform.syntax.psi.registerParse
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.tree.ICustomParsingType
import com.intellij.psi.tree.IReparseableElementTypeBase
import com.intellij.util.CharTable
import com.intellij.yaml.syntax.YamlBlockReparse
import com.intellij.yaml.syntax.YamlSyntaxTokenTypes
import com.intellij.yaml.syntax.parseYamlBlock
import com.intellij.yaml.syntax.prepareYamlBlockReparse
import com.intellij.yaml.syntax.recreateYamlBlockReparse
import org.jetbrains.annotations.ApiStatus

/**
 * A block mapping or a block sequence that gets a new text without a reparse of the file.
 * See [prepareYamlBlockReparse] for the check.
 *
 * A full parse makes a usual composite node of this type.
 * Only a reparse makes a [LazyParseableElement], which parses the new text in the merge of the trees.
 */
@ApiStatus.Internal
class YamlBlockCollectionType(debugName: String, private val syntaxType: SyntaxElementType) :
  YAMLElementType(debugName), IReparseableElementTypeBase, ICustomParsingType {

  /** The result of the last successful [isReparseable] in this thread, for [parse] of the same text. */
  private val preparedReparse = ThreadLocal<PreparedReparse?>()

  override fun isReparseable(currentNode: ASTNode, newText: CharSequence, fileLanguage: Language, project: Project): Boolean {
    preparedReparse.remove()
    if (!RegistryManager.getInstance().`is`("yaml.reparse.collections")) return false
    if (!isInPlainYamlFile(currentNode)) return false

    val cancellation = CancellationProvider { ProgressManager.checkCanceled() }
    val node = AstSyntaxNode(currentNode, getYamlElementTypeConverter())
    val reparse = prepareYamlBlockReparse(node, newText, cancellation) ?: return false
    preparedReparse.set(PreparedReparse(newText, reparse))
    return true
  }

  override fun parse(text: CharSequence, table: CharTable): ASTNode {
    val prepared = preparedReparse.get()
    preparedReparse.remove()
    val chameleon = LazyParseableElement(this, text)
    if (prepared != null && prepared.text === text) {
      chameleon.putUserData(BLOCK_REPARSE_KEY, prepared.reparse)
    }
    return chameleon
  }

  /**
   * Refuses a chameleon without the data of its check.
   * [parse] makes one if the text is not the text of the last [isReparseable] of the thread.
   * Then the platform makes a full reparse.
   */
  override fun isValidReparse(oldNode: ASTNode, newNode: ASTNode): Boolean = newNode.getUserData(BLOCK_REPARSE_KEY) != null

  override fun parseContents(chameleon: ASTNode): ASTNode? {
    // The data stays on the chameleon: the update of the injected fragments parses the chameleon again after the merge.
    val reparse = chameleon.getUserData(BLOCK_REPARSE_KEY) ?: recreateReparse(chameleon)
    val language = YAMLLanguage.INSTANCE
    val psiBuilder = PsiSyntaxBuilderFactory.getInstance().createBuilder(chameleon, tokenListLexer(reparse.tokens), language, chameleon.chars)
    val builder = psiBuilder.getSyntaxTreeBuilder()
    return registerParse(builder, language) {
      parseYamlBlock(builder, syntaxType, reparse)
      psiBuilder.getTreeBuilt().firstChildNode
    }
  }

  /** Makes the data of a chameleon that has no data of its check, see [recreateYamlBlockReparse]. */
  private fun recreateReparse(chameleon: ASTNode): YamlBlockReparse {
    LOG.error("A YAML block collection is parsed without the data of its reparse check")
    val cancellation = CancellationProvider { ProgressManager.checkCanceled() }
    val reparse = recreateYamlBlockReparse(AstSyntaxNode(chameleon, getYamlElementTypeConverter()), cancellation)
    chameleon.putUserData(BLOCK_REPARSE_KEY, reparse)
    return reparse
  }

  private class PreparedReparse(val text: CharSequence, val reparse: YamlBlockReparse)
}

private val BLOCK_REPARSE_KEY = Key.create<YamlBlockReparse>("YAML_BLOCK_REPARSE")

private val LOG = logger<YamlBlockCollectionType>()

/**
 * A [SyntaxNode] over a node of the YAML tree, for the check of [prepareYamlBlockReparse].
 * A whitespace leaf has the platform type in the tree, so it gets the whitespace type of the YAML lexer.
 */
private class AstSyntaxNode(private val node: ASTNode, private val converter: ElementTypeConverter) : SyntaxNode {
  override val text: CharSequence get() = node.chars

  override val type: SyntaxElementType
    get() = when (val type = node.elementType) {
      TokenType.WHITE_SPACE -> YamlSyntaxTokenTypes.WHITESPACE
      else -> converter.convert(type) ?: UNKNOWN_TYPE
    }

  override val startOffset: Int get() = node.startOffset

  override val endOffset: Int get() = node.startOffset + node.textLength

  override val errorMessage: String? get() = null

  override val language: SyntaxLanguage? get() = null

  override fun parent(): SyntaxNode? = node.treeParent?.let(::of)

  override fun prevSibling(): SyntaxNode? = node.treePrev?.let(::of)

  override fun nextSibling(): SyntaxNode? = node.treeNext?.let(::of)

  override fun firstChild(): SyntaxNode? = node.firstChildNode?.let(::of)

  override fun lastChild(): SyntaxNode? = node.lastChildNode?.let(::of)

  override fun childByOffset(offset: Int): SyntaxNode? {
    var child = node.firstChildNode
    while (child != null) {
      if (offset >= child.startOffset && offset < child.startOffset + child.textLength) return of(child)
      child = child.treeNext
    }
    return null
  }

  private fun of(other: ASTNode): AstSyntaxNode = AstSyntaxNode(other, converter)
}

private val UNKNOWN_TYPE = SyntaxElementType("YAML_UNKNOWN")
