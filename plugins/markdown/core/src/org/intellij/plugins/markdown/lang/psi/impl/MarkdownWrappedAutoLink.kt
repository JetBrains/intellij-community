package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement

class MarkdownWrappedAutoLink(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement
