package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownListNumber(type: IElementType, text: CharSequence): MarkdownLeafPsiElement(type, text) {
  val delimiter: Char
    get() = text.trim().last()

  val number: Int?
    get() = text.trim().dropLast(1).toIntOrNull()
}
