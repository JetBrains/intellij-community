package org.intellij.plugins.markdown.model

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination

/**
 * Shows completion popup for header anchors inside [MarkdownLinkDestination].
 */
internal class HeaderAnchorCompletionPopupTypedHandler: TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (charTyped == '#' && file.fileType == MarkdownFileType.INSTANCE) {
      if (isInsideLinkDestination(editor, file)) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
      }
    }
    return super.checkAutoPopup(charTyped, project, editor, file)
  }

  private fun isInsideLinkDestination(editor: Editor, file: PsiFile): Boolean {
    val caretOffset = editor.caretModel.currentCaret.offset
    val elementUnderCaret = file.findElementAt(caretOffset) ?: return false
    val parent = when (elementUnderCaret.elementType) {
      // Workaround for empty link destination - "[...]()"
      MarkdownTokenTypes.LPAREN, MarkdownTokenTypes.RPAREN -> elementUnderCaret.parentOfType<MarkdownLink>()
      else -> elementUnderCaret.parentOfType<MarkdownLinkDestination>()
    }
    return parent != null
  }
}
