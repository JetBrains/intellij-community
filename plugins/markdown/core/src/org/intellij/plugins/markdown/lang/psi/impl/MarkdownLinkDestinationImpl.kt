package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement

@Deprecated("Please use {@link MarkdownLinkDestination} instead.", ReplaceWith("MarkdownLinkDestination"))
abstract class MarkdownLinkDestinationImpl(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement
