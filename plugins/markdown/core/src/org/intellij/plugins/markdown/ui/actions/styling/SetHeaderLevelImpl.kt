package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
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
import com.intellij.util.DocumentUtil
import org.intellij.plugins.markdown.MarkdownBundle.messagePointer
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.intellij.plugins.markdown.util.MarkdownPsiUtil
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

@ApiStatus.Internal
abstract class SetHeaderLevelImpl(
  val level: Int,
  text: Supplier<@Nls String>,
  val secondaryText: Supplier<@Nls String>? = null,
  description: Supplier<@Nls String> = text,
  icon: Icon? = null
): ToggleAction(text, description, icon) {
  class Normal: SetHeaderLevelImpl(level = 0, messagePointer("markdown.header.level.popup.normal.action.text"))

  class Title: SetHeaderLevelImpl(
    level = 1,
    messagePointer("markdown.header.level.popup.title.action.text"),
    messagePointer("markdown.header.level.popup.heading.action.secondary.text", 1)
  )

  class Subtitle: SetHeaderLevelImpl(
    level = 2,
    messagePointer("markdown.header.level.popup.subtitle.action.text"),
    messagePointer("markdown.header.level.popup.heading.action.secondary.text", 2)
  )

  class Heading(level: Int): SetHeaderLevelImpl(
    level,
    messagePointer("markdown.header.level.popup.heading.action.text", level),
    messagePointer("markdown.header.level.popup.heading.action.secondary.text", level)
  )

  override fun isSelected(event: AnActionEvent): Boolean {
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return false
    val caret = event.getData(CommonDataKeys.CARET) ?: return false
    val element = findParent(file, caret)
    val header = element?.parentOfType<MarkdownHeader>(withSelf = true)
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
    val element = findParent(file, caret)
    if (element == null) {
      tryToCreateHeaderFromRawLine(editor, caret)
      return
    }
    val header = PsiTreeUtil.getParentOfType(element, MarkdownHeader::class.java, false)
    val project = file.project
    runWriteAction {
      executeCommand(project) {
        when {
          header != null -> handleExistingHeader(header, editor)
          level != 0 -> element.replace(MarkdownPsiElementFactory.createHeader(project, level, element.text))
        }
      }
    }
  }

  /**
   * Simply adds `#` at the line start. If there are no empty lines around new header, new lines will be added them.
   */
  private fun tryToCreateHeaderFromRawLine(editor: Editor, caret: Caret) {
    val document = editor.document
    val selectionStart = caret.selectionStart
    val selectionEnd = caret.selectionEnd
    val line = document.getLineNumber(selectionStart)
    if (line != document.getLineNumber(selectionEnd)) {
      return
    }
    val lineStartOffset = document.getLineStartOffset(line)
    runWriteAction {
      executeCommand(editor.project) {
        val nextLine = line + 1
        if (nextLine < document.lineCount && !DocumentUtil.isLineEmpty(document, nextLine)) {
          val lineEndOffset = document.getLineEndOffset(line)
          document.insertString(lineEndOffset, "\n")
        }
        document.insertString(lineStartOffset, "${"#".repeat(level)} ")
        val previousLine = line - 1
        if (previousLine >= 0 && !DocumentUtil.isLineEmpty(document, previousLine)) {
          document.insertString(lineStartOffset, "\n")
        }
      }
    }
  }

  private fun handleExistingHeader(header: MarkdownHeader, editor: Editor) {
    when {
      level == 0 -> editor.document.replaceString(header.startOffset, header.endOffset, header.name ?: return)
      header.level != level -> header.replace(MarkdownPsiElementFactory.createHeader(header.project, header.name ?: return, level))
    }
  }

  companion object {
    private val inlineElements = TokenSet.orSet(
      MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES,
      MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_PARENTS_TYPES
    )

    @JvmStatic
    internal fun findParent(psiFile: PsiFile, caret: Caret): PsiElement? {
      val (left, right) = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret)
      val startElement = when {
        MarkdownPsiUtil.WhiteSpaces.isNewLine(left) -> PsiTreeUtil.nextVisibleLeaf(left)
        else -> left
      }
      val endElement = when {
        MarkdownPsiUtil.WhiteSpaces.isNewLine(right) -> PsiTreeUtil.prevVisibleLeaf(right)
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
