/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.toml.lang.psi.*

class TomlFoldingBuilder : CustomFoldingBuilder(), DumbAware {
    override fun buildLanguageFoldRegions(
        descriptors: MutableList<FoldingDescriptor>,
        root: PsiElement,
        document: Document,
        quick: Boolean
    ) {
        if (root !is TomlFile) return

        val visitor = TomlFoldingVisitor(descriptors)
        root.accept(visitor)
    }

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String =
        when (node.elementType) {
            TomlElementTypes.ARRAY -> "[...]"
            else -> "{...}"
        }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean = false
}

private class TomlFoldingVisitor(private val descriptors: MutableList<FoldingDescriptor>): TomlRecursiveVisitor() {
    override fun visitTable(element: TomlTable) {
        if (element.entries.isNotEmpty()) {
            foldChildren(element, element.header.nextSibling, element.lastChild)
            super.visitTable(element)
        }
    }

    override fun visitArrayTable(element: TomlArrayTable) {
        if (element.entries.isNotEmpty()) {
            foldChildren(element, element.header.nextSibling, element.lastChild)
            super.visitArrayTable(element)
        }
    }

    override fun visitInlineTable(element: TomlInlineTable) {
        if (element.entries.isNotEmpty()) {
            fold(element)
            super.visitInlineTable(element)
        }
    }

    override fun visitArray(element: TomlArray) {
        if (element.elements.isNotEmpty()) {
            fold(element)
            super.visitArray(element)
        }
    }

    private fun fold(element: PsiElement) {
        descriptors += FoldingDescriptor(element.node, element.textRange)
    }

    private fun foldChildren(element: PsiElement, firstChild: PsiElement, lastChild: PsiElement) {
        val start = firstChild.textRange.startOffset
        val end = lastChild.textRange.endOffset
        descriptors += FoldingDescriptor(element.node, TextRange(start, end))
    }
}
