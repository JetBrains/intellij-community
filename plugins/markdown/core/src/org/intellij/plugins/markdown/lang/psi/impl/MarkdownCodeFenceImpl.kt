package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement

/**
 * Please use [MarkdownCodeFence] instead.
 */
@Deprecated("Please use {@link MarkdownCodeFence} instead.", ReplaceWith("MarkdownCodeFence"))
abstract class MarkdownCodeFenceImpl(type: IElementType): CompositePsiElement(type), PsiLanguageInjectionHost, MarkdownPsiElement {
  val fenceLanguage: String?
    get() = findPsiChildByType(MarkdownTokenTypes.FENCE_LANG)?.text
}
