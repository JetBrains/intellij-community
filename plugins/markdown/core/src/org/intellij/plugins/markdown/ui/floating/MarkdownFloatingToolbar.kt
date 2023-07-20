package org.intellij.plugins.markdown.ui.floating

import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownFloatingToolbar(editor: Editor): FloatingToolbar(editor, "Markdown.Toolbar.Floating") {

    private val elementsToIgnore = listOf(
      MarkdownElementTypes.CODE_FENCE,
      MarkdownElementTypes.CODE_BLOCK,
      MarkdownElementTypes.CODE_SPAN,
      MarkdownElementTypes.HTML_BLOCK,
      MarkdownElementTypes.LINK_DESTINATION
    )

  override fun hasIgnoredParent(element: PsiElement): Boolean {
    if (element.containingFile !is MarkdownFile) {
      return true
    }
    return element.parents(withSelf = true).any { it.elementType in elementsToIgnore }
  }

}