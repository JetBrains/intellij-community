/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.FormatterUtil
import org.toml.ide.formatter.impl.computeIndent
import org.toml.ide.formatter.impl.computeSpacing
import org.toml.ide.formatter.impl.isWhitespaceOrEmpty
import org.toml.lang.psi.TomlElementTypes.ARRAY

class TomlFmtBlock(
    private val node: ASTNode,
    private val alignment: Alignment?,
    private val indent: Indent?,
    private val wrap: Wrap?,
    private val ctx: TomlFmtContext
) : ASTBlock {
    override fun getNode(): ASTNode = node
    override fun getTextRange(): TextRange = node.textRange
    override fun getAlignment(): Alignment? = alignment
    override fun getIndent(): Indent? = indent
    override fun getWrap(): Wrap? = wrap

    override fun getSubBlocks(): List<Block> = mySubBlocks
    private val mySubBlocks: List<Block> by lazy { buildChildren() }

    private fun buildChildren(): List<Block> {
        return node.getChildren(null)
            .filter { !it.isWhitespaceOrEmpty() }
            .map { childNode: ASTNode ->
                TomlFormattingModelBuilder.createBlock(
                    node = childNode,
                    alignment = null,
                    indent = computeIndent(childNode),
                    wrap = null,
                    ctx = ctx
                )
            }
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = computeSpacing(child1, child2, ctx)

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val indent = when (node.elementType) {
            ARRAY -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
        return ChildAttributes(indent, null)
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun isIncomplete(): Boolean = myIsIncomplete
    private val myIsIncomplete: Boolean by lazy { FormatterUtil.isIncomplete(node) }

    override fun toString() = "${node.text} $textRange"
}
