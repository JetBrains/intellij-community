package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownListNumber(type: IElementType, text: CharSequence): LeafPsiElement(type, text), MarkdownPsiElement {
  val delimiter: Char
    get() = text.trim().last()

  val number: Int?
    get() = text.trim().dropLast(1).toIntOrNull()
}
