package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownInlineLink(node: ASTNode): ASTWrapperPsiElement(node), MarkdownLink
