// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.elementsAtOffsetUp
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.LocalFileUrl
import com.intellij.util.Urls
import com.intellij.util.io.exists
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.ui.actions.MarkdownActionPlaces
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil
import org.jetbrains.annotations.Nls
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class MarkdownCreateLinkAction : ToggleAction(), DumbAware {
  private val wrapActionBaseName: String
    get() = MarkdownBundle.message("action.Markdown.Styling.CreateLink.text")

  private val unwrapActionName: String
    get() = MarkdownBundle.message("action.Markdown.Styling.CreateLink.unwrap.text")

  private fun obtainWrapActionName(place: String): @Nls String {
    return when (place) {
      MarkdownActionPlaces.INSERT_POPUP -> MarkdownBundle.message("action.Markdown.Styling.CreateLink.insert.popup.text")
      else -> wrapActionBaseName
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val editor = MarkdownActionUtil.findMarkdownTextEditor(e)
    val file = e.getData(CommonDataKeys.PSI_FILE)

    if (editor == null || file !is MarkdownFile) {
      e.presentation.isEnabledAndVisible = false
      return false
    }
    e.presentation.isEnabledAndVisible = true

    val caretsWithLinks = editor.caretModel.allCarets
      .filter { it.getSelectedLinkElement(file) != null }

    val caretsWithLinksCount = caretsWithLinks.count()
    return when {
      caretsWithLinksCount == 0 || e.place == ActionPlaces.EDITOR_POPUP -> {
        e.presentation.isEnabled = !editor.isViewer
        e.presentation.text = obtainWrapActionName(e.place)
        e.presentation.description = MarkdownBundle.message(
          "action.Markdown.Styling.CreateLink.description")
        false
      }
      caretsWithLinksCount == editor.caretModel.caretCount -> {
        e.presentation.isEnabled = !editor.isViewer
        e.presentation.text = unwrapActionName
        e.presentation.description = MarkdownBundle.message(
          "action.Markdown.Styling.CreateLink.unwrap.description")
        true
      }
      else -> { // some carets are located at links, others are not
        e.presentation.isEnabled = false
        false
      }
    }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val editor = MarkdownActionUtil.findMarkdownTextEditor(e) ?: error("Could not find Markdown editor")
    val file = e.getRequiredData(CommonDataKeys.PSI_FILE)

    if (state) {
      for (caret in editor.caretModel.allCarets) {
        wrapSelectionWithLink(caret, editor, file.project)
      }
    }
    else {
      editor.caretModel.allCarets
        .sortedBy { -it.offset }
        .map { caret -> caret to caret.getSelectedLinkElement(file) }
        .distinctBy { (_, element) -> element }
        .forEach { (caret, element) ->
          assert(element != null)
          if (element == null) return@forEach
          unwrapLink(element, caret, editor, file.project)
        }
    }
  }

  override fun update(e: AnActionEvent) {
    val originalIcon = e.presentation.icon
    super.update(e)
    if (ActionPlaces.isPopupPlace(e.place)) {
      // Restore original icon, as it will be disabled in popups, and we still want to show in GeneratePopup
      e.presentation.icon = originalIcon
    }
  }

  private fun wrapSelectionWithLink(caret: Caret, editor: Editor, project: Project) {
    val selected = caret.selectedText ?: ""
    val selectionStart = caret.selectionStart
    val selectionEnd = caret.selectionEnd

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val fileArray = file?.let { arrayOf(it) } ?: emptyArray()
    WriteCommandAction.writeCommandAction(project, *fileArray)
      .withName(wrapActionBaseName)
      .run<Nothing> {
        caret.removeSelection()

        editor.document.replaceString(selectionStart, selectionEnd, "[$selected]()")
        caret.moveToOffset(selectionEnd + 3)

        getLinkDestinationInClipboard(editor)?.let { linkDestination ->
          val linkStartOffset = caret.offset
          editor.document.insertString(linkStartOffset, linkDestination)
          caret.setSelection(linkStartOffset, linkStartOffset + linkDestination.length)
        }
      }
  }

  private fun getLinkDestinationInClipboard(editor: Editor): String? =
    ClipboardUtil.getTextInClipboard()?.takeIf { path ->
      when (Urls.parse(path, asLocalIfNoScheme = true)) {
        null -> false
        is LocalFileUrl -> {
          val relativePath = try {
            Path.of(path)
          }
          catch (e: InvalidPathException) {
            return@takeIf false
          }

          val dir = FileDocumentManager.getInstance().getFile(editor.document)?.parent
          val absolutePath = dir?.let { Path.of(it.path) }?.resolve(relativePath)
          absolutePath?.exists() ?: false
        }
        else -> true
      }
    }

  private fun unwrapLink(linkElement: PsiElement, caret: Caret, editor: Editor, project: Project) {
    val linkText = linkElement.children
                     .find { it.elementType == MarkdownElementTypes.LINK_TEXT }
                     ?.text?.drop(1)?.dropLast(1) // unwrap []
                   ?: ""

    val start = linkElement.startOffset
    val newEnd = start + linkText.length

    // left braket is deleted, so offsets should be shifted 1 position backwards;
    // they should also match the constraint: start <= offset <= newEnd
    val selectionStart = maxOf(start, minOf(newEnd, caret.selectionStart - 1))
    val selectionEnd = maxOf(start, minOf(newEnd, caret.selectionEnd - 1))

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val fileArray = file?.let { arrayOf(it) } ?: emptyArray()
    WriteCommandAction.writeCommandAction(project, *fileArray)
      .withName(unwrapActionName)
      .run<Nothing> {
        if (selectionStart == selectionEnd) {
          caret.removeSelection() // so that the floating toolbar is hidden; must be done before text replacement
          caret.moveCaretRelatively(-1, 0, false, false)
        }

        editor.document.replaceString(start, linkElement.endOffset, linkText)

        if (selectionStart != selectionEnd)
          caret.setSelection(selectionStart, selectionEnd)
      }
  }

  private fun Caret.getSelectedLinkElement(file: PsiFile): PsiElement? {
    val linkElement = file.elementsAtOffsetUp(selectionStart).asSequence()
      .find { (element, _) -> element.elementType == MarkdownElementTypes.INLINE_LINK }
      ?.first

    return linkElement?.takeIf { element ->
      if (hasSelection())
        selectionEnd <= element.endOffset
      else
        offset > element.startOffset // caret should be strictly inside
    }
  }
}
