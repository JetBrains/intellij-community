package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import org.jetbrains.annotations.ApiStatus

@Deprecated("Please use {@link MarkdownParagraph} instead.", ReplaceWith("MarkdownParagraph"))
@ApiStatus.ScheduledForRemoval
abstract class MarkdownParagraphImpl(node: ASTNode): MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost
