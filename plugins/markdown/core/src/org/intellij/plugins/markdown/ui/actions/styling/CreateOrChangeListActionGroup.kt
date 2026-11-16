package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.client.currentSessionOrNull
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.siblings
import com.intellij.util.application
import org.intellij.plugins.markdown.MarkdownBundle.messagePointer
import org.intellij.plugins.markdown.MarkdownIcons
import org.intellij.plugins.markdown.editor.runForEachCaret
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAt
import org.intellij.plugins.markdown.editor.lists.ListUtils.items
import org.intellij.plugins.markdown.editor.lists.ListUtils.list
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.util.hasType
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
  init {
    templatePresentation.isPopupGroup = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val session = application.currentSessionOrNull
    if (session?.isRemote == true) {
      event.presentation.isEnabledAndVisible = false
      return
    }
  }

  class OrderedList: CreateListImpl(
    text = messagePointer("markdown.create.list.popup.ordered.action.text"),
    icon = MarkdownIcons.EditorActions.NumberedList
  ) {
    override fun isSameMarker(markerElement: PsiElement, style: MarkdownCustomCodeStyleSettings): Boolean {
      return !hasCheckbox(markerElement) && obtainMarkerText(markerElement)?.toIntOrNull() != null &&
             hasForcedDelimiter(markerElement, style)
    }

    override fun createMarkerText(index: Int, style: MarkdownCustomCodeStyleSettings): String {
      return orderedMarkerText(index, style)
    }
  }

  class UnorderedList: CreateListImpl(
    text = messagePointer("markdown.create.list.popup.unordered.action.text"),
    icon = MarkdownIcons.EditorActions.BulletList
  ) {
    override fun isSameMarker(markerElement: PsiElement, style: MarkdownCustomCodeStyleSettings): Boolean {
      val bullet = obtainMarkerText(markerElement)?.singleOrNull() ?: return false
      if (hasCheckbox(markerElement) || bullet !in BULLETS) {
        return false
      }
      val forcedBullet = style.unorderedListMarker.bullet
      return forcedBullet == null || bullet == forcedBullet
    }

    override fun createMarkerText(index: Int, style: MarkdownCustomCodeStyleSettings): String {
      return (style.unorderedListMarker.bullet ?: DEFAULT_BULLET).toString()
    }
  }

  class CheckmarkList: CreateListImpl(
    text = messagePointer("markdown.create.list.popup.checkmark.action.text"),
    icon = MarkdownIcons.EditorActions.CheckmarkList
  ) {
    override fun isSameMarker(markerElement: PsiElement, style: MarkdownCustomCodeStyleSettings): Boolean {
      return hasCheckbox(markerElement) && hasForcedDelimiter(markerElement, style)
    }

    override fun createMarkerText(index: Int, style: MarkdownCustomCodeStyleSettings): String {
      return "${orderedMarkerText(index, style)} [ ]"
    }

    override fun processListElement(originalChild: MarkdownListItem, index: Int, style: MarkdownCustomCodeStyleSettings): PsiElement {
      val originalMarker = originalChild.markerElement ?: return originalChild
      if (hasCheckbox(originalMarker)) {
        originalMarker.replace(MarkdownPsiElementFactory.createListMarker(originalChild.project, orderedMarkerText(index, style)))
        return originalChild
      }
      val (marker, checkbox) = MarkdownPsiElementFactory.createListMarkerWithCheckbox(
        originalChild.project,
        orderedMarkerText(index, style),
        true
      )
      val addedMarker = originalMarker.replace(marker)
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
      val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
      val document = editor.document
      val style = CodeStyle.getCustomSettings(file, MarkdownCustomCodeStyleSettings::class.java)
      WriteCommandAction.writeCommandAction(project, file)
        .withName(templatePresentation.text)
        .run<RuntimeException> {
          editor.caretModel.runForEachCaret(reverseOrder = true) { caret ->
            PsiDocumentManager.getInstance(project).commitDocument(document)
            val list = findList(file, document, caret.selectionStart)
            when {
              state && list == null -> createListFromText(project, document, caret, style)
              state && list != null -> replaceList(list, style)
              !state && list != null -> replaceListWithText(document, list)
            }
          }
        }
    }

    override fun isSelected(event: AnActionEvent): Boolean {
      val file = event.getData(CommonDataKeys.PSI_FILE) ?: return false
      val editor = event.getData(CommonDataKeys.EDITOR) ?: return false
      val document = editor.document
      val carets = SelectionUtil.obtainCaretSnapshots(this, event, editor)
      if (carets.isEmpty()) {
        return false
      }
      val style = CodeStyle.getCustomSettings(file, MarkdownCustomCodeStyleSettings::class.java)
      return carets.all { caret ->
        val list = findList(file, document, caret.offset) ?: return@all false
        val marker = list.items.firstOrNull()?.markerElement ?: return@all false
        isSameMarker(marker, style)
      }
    }

    abstract fun isSameMarker(markerElement: PsiElement, style: MarkdownCustomCodeStyleSettings): Boolean

    abstract fun createMarkerText(index: Int, style: MarkdownCustomCodeStyleSettings): String

    open fun processListElement(originalChild: MarkdownListItem, index: Int, style: MarkdownCustomCodeStyleSettings): PsiElement {
      val marker = MarkdownPsiElementFactory.createListMarker(originalChild.project, createMarkerText(index, style))
      val originalMarker = originalChild.markerElement ?: return originalChild
      if (hasCheckbox(originalMarker)) {
        originalMarker.nextSibling?.delete()
      }
      originalMarker.replace(marker)
      return originalChild
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    private fun replaceList(list: MarkdownList, style: MarkdownCustomCodeStyleSettings) {
      list.items.forEachIndexed { index, item ->
        processListElement(item, index, style)
      }
    }

    private fun createListFromText(project: Project, document: Document, caret: Caret, style: MarkdownCustomCodeStyleSettings) {
      val startLine = document.getLineNumber(caret.selectionStart)
      val endLine = document.getLineNumber(caret.selectionEnd)
      val text = document.charsSequence
      val lines = (startLine..endLine).asSequence().map {
        text.substring(document.getLineStartOffset(it), document.getLineEndOffset(it))
      }
      val list = MarkdownPsiElementFactory.createList(project, lines.asIterable()) { index -> createMarkerText(index, style) }
      document.replaceString(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine), list.text)
    }

    companion object {
      private fun findList(file: PsiFile, document: Document, offset: Int): MarkdownList? {
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
    private const val DEFAULT_BULLET = '*'
    private const val DEFAULT_DELIMITER = '.'
    private const val BULLETS = "*-+"

    private fun orderedMarkerText(index: Int, style: MarkdownCustomCodeStyleSettings): String {
      return "${index + 1}${style.orderedListMarker.delimiter ?: DEFAULT_DELIMITER}"
    }

    /**
     * Returns `true` for a bullet marker, and for a number marker with the delimiter of the code style.
     */
    private fun hasForcedDelimiter(markerElement: PsiElement, style: MarkdownCustomCodeStyleSettings): Boolean {
      val forcedDelimiter = style.orderedListMarker.delimiter ?: return true
      val marker = markerElement.text?.trim() ?: return false
      return marker.dropLast(1).toIntOrNull() == null || marker.last() == forcedDelimiter
    }

    private fun obtainMarkerText(markerElement: PsiElement): String? {
      return markerElement.text?.trim()?.trimEnd('.', ')')
    }

    private fun hasCheckbox(element: PsiElement): Boolean {
      return element.nextSibling?.hasType(MarkdownTokenTypes.CHECK_BOX) == true
    }
  }
}
