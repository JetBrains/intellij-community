// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.jetbrains.annotations.ApiStatus

/**
 * Returns `true` if [text] is a well-formed footnote label bracket (e.g. `[^note]`).
 * The minimum valid form is `[^x]` (length 4).
 */
@ApiStatus.Internal
fun isFootnoteLabelText(text: String): Boolean =
  text.length > 3 && text.startsWith("[^") && text.endsWith("]") && !text.contains(Regex("[ \t]"))

@ApiStatus.Internal
fun isFootnoteContinuationBlock(codeBlock: PsiElement): Boolean {
  for (sibling in codeBlock.siblings(forward = false, withSelf = false)) {
    when {
      sibling.hasType(MarkdownElementTypes.PARAGRAPH) -> return isFootnoteDefinitionParagraph(sibling)
      sibling.hasType(MarkdownElementTypes.LINK_DEFINITION) -> return isFootnoteDefinitionLinkDef(sibling)
      sibling.hasType(MarkdownElementTypes.CODE_BLOCK) -> continue
      sibling.firstChild == null -> continue  // skip leaf tokens (EOL, whitespace)
      else -> return false
    }
  }
  return false
}

@ApiStatus.Internal
fun isFootnoteDefinitionParagraph(paragraph: PsiElement): Boolean {
  val firstChild = paragraph.firstChild ?: return false
  val linkLabel: PsiElement? = when (firstChild.elementType) {
    MarkdownElementTypes.SHORT_REFERENCE_LINK -> firstChild.firstChild
    MarkdownElementTypes.LINK_LABEL -> firstChild
    else -> null
  }
  if (linkLabel == null || !linkLabel.hasType(MarkdownElementTypes.LINK_LABEL)) return false
  return isFootnoteLabelText(linkLabel.text)
}

@ApiStatus.Internal
fun isFootnoteDefinitionLinkDef(linkDef: PsiElement): Boolean {
  val linkLabel = linkDef.firstChild ?: return false
  if (!linkLabel.hasType(MarkdownElementTypes.LINK_LABEL)) return false
  return isFootnoteLabelText(linkLabel.text)
}
