package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost

@Deprecated("Please use {@link MarkdownParagraph} instead.", ReplaceWith("MarkdownParagraph"))
abstract class MarkdownParagraphImpl(node: ASTNode): MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost
