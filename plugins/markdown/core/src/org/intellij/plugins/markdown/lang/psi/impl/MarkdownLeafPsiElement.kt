package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class MarkdownLeafPsiElement(type: IElementType, text: CharSequence): LeafPsiElement(type, text), MarkdownPsiElement {
  override fun toString(): String {
    val name = this::class.java.simpleName
    return "$name($elementType)"
  }
}
