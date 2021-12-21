// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
