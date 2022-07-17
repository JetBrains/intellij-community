package org.intellij.plugins.markdown.lang.psi.impl

import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.util.children
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface MarkdownLink: MarkdownPsiElement {
  val linkText: MarkdownLinkText?
    get() = children().filterIsInstance<MarkdownLinkText>().firstOrNull()

  val linkDestination: MarkdownLinkDestination?
    get() = children().filterIsInstance<MarkdownLinkDestination>().firstOrNull()
}
