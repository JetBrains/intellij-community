package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement

/**
 * Please use [MarkdownCodeBlock] instead.
 */
@Deprecated("Please use {@link MarkdownCodeBlock} instead.", ReplaceWith("MarkdownCodeBlock"))
abstract class MarkdownCodeBlockImpl(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement
