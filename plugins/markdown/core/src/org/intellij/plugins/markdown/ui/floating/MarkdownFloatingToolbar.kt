package org.intellij.plugins.markdown.ui.floating

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import kotlinx.coroutines.CoroutineScope
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class MarkdownFloatingToolbar(
  editor: Editor,
  coroutineScope: CoroutineScope
): FloatingToolbar(editor, coroutineScope) {
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

  override fun isEnabled(): Boolean {
    return !AdvancedSettings.getBoolean("markdown.hide.floating.toolbar")
  }

  override fun createActionGroup(): ActionGroup? {
    return CustomActionsSchema.getInstance().getCorrectedAction("Markdown.Toolbar.Floating") as? ActionGroup
  }
}
