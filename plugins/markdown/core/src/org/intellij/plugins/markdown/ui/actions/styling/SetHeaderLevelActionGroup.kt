package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.MarkdownBundle.messagePointer
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory.createHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.util.MarkdownPsiUtil.WhiteSpaces.isNewLine
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

internal class SetHeaderLevelActionGroup: DefaultActionGroup(
  SetHeaderLevelAction(level = 0, messagePointer("markdown.header.level.popup.normal.action.text")),
  SetHeaderLevelAction(level = 1, messagePointer("markdown.header.level.popup.header.action.text", 1)),
  SetHeaderLevelAction(level = 2, messagePointer("markdown.header.level.popup.header.action.text", 2)),
  SetHeaderLevelAction(level = 3, messagePointer("markdown.header.level.popup.header.action.text", 3)),
  SetHeaderLevelAction(level = 4, messagePointer("markdown.header.level.popup.header.action.text", 4)),
  SetHeaderLevelAction(level = 5, messagePointer("markdown.header.level.popup.header.action.text", 5)),
  SetHeaderLevelAction(level = 6, messagePointer("markdown.header.level.popup.header.action.text", 6))
) {
  override fun displayTextInToolbar(): Boolean = true

  override fun update(event: AnActionEvent) {
    super.update(event)
    val children = getChildren(event)
    val child = children.find { (it as ToggleAction).isSelected(event) }
    if (child == null) {
      event.presentation.text = children.first().templateText
      event.presentation.isEnabled = false
      return
    }
    event.presentation.text = child.templatePresentation.text
    child.update(event)
  }

  private class SetHeaderLevelAction(
    private val level: Int,
    text: Supplier<@Nls String>,
    description: Supplier<@Nls String> = text,
    icon: Icon? = null
  ): ToggleAction(text, description, icon) {
    override fun isSelected(event: AnActionEvent): Boolean {
      val file = event.getData(CommonDataKeys.PSI_FILE) ?: return false
      val caret = event.getData(CommonDataKeys.CARET) ?: return false
      val element = findParent(file, caret) ?: return false
      val header = element.parentOfType<MarkdownHeader>(withSelf = true)
      return when {
        header == null && level == 0 -> true
        header != null -> header.level == level
        else -> false
      }
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (!state) {
        return
      }
      val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
      val caret = event.getData(CommonDataKeys.CARET) ?: return
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return
      val element = findParent(file, caret) ?: return
      val header = PsiTreeUtil.getParentOfType(element, MarkdownHeader::class.java, false)
      val project = file.project
      runWriteAction {
        executeCommand(project) {
          when {
            header != null -> handleExistingHeader(header, editor)
            level != 0 -> element.replace(createHeader(project, level, element.text))
          }
        }
      }
    }

    private fun handleExistingHeader(header: MarkdownHeader, editor: Editor) {
      when {
        level == 0 -> editor.document.replaceString(header.startOffset, header.endOffset, header.name ?: return)
        header.level != level -> header.replace(createHeader(header.project, header.name ?: return, level))
      }
    }

    companion object {
      private val inlineElements = TokenSet.orSet(
        MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES,
        MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_PARENTS_TYPES
      )

      @JvmStatic
      internal fun findParent(psiFile: PsiFile, caret: Caret): PsiElement? {
        val (left, right) = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret) ?: return null
        val startElement = when {
          isNewLine(left) -> PsiTreeUtil.nextVisibleLeaf(left)
          else -> left
        }
        val endElement = when {
          isNewLine(right) -> PsiTreeUtil.prevVisibleLeaf(right)
          else -> right
        }
        if (startElement == null || endElement == null || startElement.textOffset > endElement.textOffset) {
          return null
        }
        val parent = MarkdownActionUtil.getCommonParentOfTypes(startElement, endElement, inlineElements)
        if (parent?.hasType(MarkdownElementTypes.PARAGRAPH) != true) {
          return parent
        }
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return null
        val startOffset = parent.textRange.startOffset
        val endOffset = parent.textRange.endOffset
        if (startOffset < 0 || endOffset > document.textLength) {
          return null
        }
        return when {
          isSameLine(document, startOffset, endOffset) -> parent
          else -> null
        }
      }

      private fun isSameLine(document: Document, firstOffset: Int, secondOffset: Int): Boolean {
        return document.getLineNumber(firstOffset) == document.getLineNumber(secondOffset)
      }
    }
  }
}
