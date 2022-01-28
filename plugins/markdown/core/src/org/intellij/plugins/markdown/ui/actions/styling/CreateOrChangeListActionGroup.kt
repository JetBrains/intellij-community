package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.MarkdownBundle.messagePointer
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAt
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.ListUtils.list
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.util.hasType
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

/**
 * Not expected to be used directly.
 * Check [CreateOrChangeListPopupAction].
 */
internal class CreateOrChangeListActionGroup: DefaultActionGroup(
  UnorderedList(),
  OrderedList(),
  CheckmarkList()
) {
  override fun isPopup(): Boolean = true

  class OrderedList: CreateListImpl(
    text = messagePointer("markdown.create.list.popup.ordered.action.text"),
    icon = MarkdownIcons.EditorActions.NumberedList
  ) {
    override fun isSameMarker(markerElement: PsiElement): Boolean {
      return !hasCheckbox(markerElement) && obtainMarkerText(markerElement)?.toIntOrNull() != null
    }

    override fun createMarkerText(index: Int): String {
      return "${index + 1}."
    }
  }

  class UnorderedList: CreateListImpl(
    text = messagePointer("markdown.create.list.popup.unordered.action.text"),
    icon = MarkdownIcons.EditorActions.BulletList
  ) {
    override fun isSameMarker(markerElement: PsiElement): Boolean {
      return !hasCheckbox(markerElement) && obtainMarkerText(markerElement) == "*"
    }

    override fun createMarkerText(index: Int): String {
      return "*"
    }
  }

  class CheckmarkList: CreateListImpl(
    text = messagePointer("markdown.create.list.popup.checkmark.action.text"),
    icon = MarkdownIcons.EditorActions.CheckmarkList
  ) {
    override fun isSameMarker(markerElement: PsiElement): Boolean {
      return hasCheckbox(markerElement)
    }

    override fun createMarkerText(index: Int): String {
      return "${index + 1}. [ ]"
    }

    override fun processListElement(originalChild: MarkdownListItem, index: Int): PsiElement {
      val (marker, checkbox) = MarkdownPsiElementFactory.createListMarkerWithCheckbox(
        originalChild.project,
        "${index + 1}.",
        true
      )
      val addedMarker = originalChild.markerElement!!.replace(marker)
      originalChild.addAfter(checkbox, addedMarker)
      return originalChild
    }
  }

  abstract class CreateListImpl(
    text: Supplier<@Nls String>,
    description: Supplier<@Nls String> = text,
    icon: Icon
  ): ToggleAction(text, description, icon) {
    override fun setSelected(event: AnActionEvent, state: Boolean) {
      val project = event.project ?: return
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return
      val caret = event.getData(CommonDataKeys.CARET) ?: return
      val file = event.getData(CommonDataKeys.PSI_FILE) as? MarkdownFile ?: return
      val caretOffset = caret.selectionStart
      val document = editor.document
      val list = findList(file, document, caretOffset)
      runWriteAction {
        executeCommand(project) {
          when {
            state && list == null -> createListFromText(project, document, caret)
            state && list != null -> replaceList(list)
            !state && list != null -> replaceListWithText(document, list)
          }
        }
      }
    }

    override fun isSelected(event: AnActionEvent): Boolean {
      val file = event.getData(CommonDataKeys.PSI_FILE) as? MarkdownFile ?: return false
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return false
      val caretOffset = editor.caretModel.currentCaret.offset
      val document = editor.document
      val list = findList(file, document, caretOffset) ?: return false
      val marker = list.items.firstOrNull()?.markerElement ?: return false
      return isSameMarker(marker)
    }

    abstract fun isSameMarker(markerElement: PsiElement): Boolean

    abstract fun createMarkerText(index: Int): String

    open fun processListElement(originalChild: MarkdownListItem, index: Int): PsiElement {
      val marker = MarkdownPsiElementFactory.createListMarker(originalChild.project, createMarkerText(index))
      val originalMarker = originalChild.markerElement ?: return originalChild
      if (hasCheckbox(originalMarker)) {
        originalMarker.nextSibling?.delete()
      }
      originalMarker.replace(marker)
      return originalChild
    }

    private fun replaceList(list: MarkdownList): MarkdownList {
      val resultList = MarkdownPsiElementFactory.createEmptyList(list.project, true)
      val children = list.firstChild?.siblings(forward = true, withSelf = true) ?: return list
      var itemIndex = 0
      for (child in children) {
        when (child) {
          is MarkdownListItem -> {
            resultList.add(processListElement(child, itemIndex))
            itemIndex += 1
          }
          else -> resultList.add(child)
        }
      }
      return list.replace(resultList) as MarkdownList
    }

    private fun createListFromText(project: Project, document: Document, caret: Caret) {
      val startLine = document.getLineNumber(caret.selectionStart)
      val endLine = document.getLineNumber(caret.selectionEnd)
      val text = document.charsSequence
      val lines = (startLine..endLine).asSequence().map {
        text.substring(document.getLineStartOffset(it), document.getLineEndOffset(it))
      }
      val list = MarkdownPsiElementFactory.createList(project, lines.asIterable(), ::createMarkerText)
      document.replaceString(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine), list.text)
    }

    companion object {
      private fun findList(file: MarkdownFile, document: Document, offset: Int): MarkdownList? {
        return file.getListItemAt(offset, document)?.list
      }

      private fun replaceListWithText(document: Document, list: MarkdownList) {
        val firstItem = list.items.firstOrNull() ?: return
        val builder = StringBuilder()
        for (element in firstItem.siblings(forward = true)) {
          val text = when (element) {
            is MarkdownListItem -> element.itemText ?: ""
            else -> element.text
          }
          builder.append(text)
        }
        val range = list.textRange
        document.replaceString(range.startOffset, range.endOffset, builder.toString())
      }
    }
  }

  companion object {
    private fun obtainMarkerText(markerElement: PsiElement): String? {
      return markerElement.text?.trimEnd('.', ')', ' ')
    }

    private fun hasCheckbox(element: PsiElement): Boolean {
      return element.nextSibling?.hasType(MarkdownTokenTypes.CHECK_BOX) == true
    }
  }
}
