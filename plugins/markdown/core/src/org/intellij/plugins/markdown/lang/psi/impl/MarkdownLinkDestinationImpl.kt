package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.jetbrains.annotations.ApiStatus

@Deprecated("Please use {@link MarkdownLinkDestination} instead.", ReplaceWith("MarkdownLinkDestination"))
@ApiStatus.ScheduledForRemoval
abstract class MarkdownLinkDestinationImpl(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement
