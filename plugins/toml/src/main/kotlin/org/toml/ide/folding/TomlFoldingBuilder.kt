package org.toml.ide.folding;

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.*

class TomlFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root !is TomlFile) return emptyArray()

        val visitor = TomlFoldingVisitor()
        root.accept(visitor)
        return visitor.folds.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = when (node.elementType) {
        TomlElementTypes.ARRAY -> "[...]"
        else -> "{...}"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}

private class TomlFoldingVisitor: TomlRecursiveVisitor() {
    val folds: List<FoldingDescriptor>
        get() = descriptors
    private val descriptors: MutableList<FoldingDescriptor> = mutableListOf()

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
