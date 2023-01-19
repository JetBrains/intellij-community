package org.intellij.plugins.markdown.editor.headers

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parents
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.DocumentUtil
import org.intellij.plugins.markdown.editor.lists.ListUtils.getLineIndentSpaces
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAt
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem

internal class HeaderLevelInferenceTypedHandler: TypedHandlerDelegate() {
  override fun beforeCharTyped(char: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
    if (!Registry.`is`("markdown.experimental.header.level.inference.enable", false)) {
      return super.beforeCharTyped(char, project, editor, file, fileType)
    }
    if (file.fileType != MarkdownFileType.INSTANCE || file !is MarkdownFile || char != '#') {
      return super.beforeCharTyped(char, project, editor, file, fileType)
    }
    val document = editor.document
    PsiDocumentManager.getInstance(project).commitDocument(document)
    if (shouldIgnore(file, editor)) {
      return super.beforeCharTyped(char, project, editor, file, fileType)
    }
    val caretOffset = editor.caretModel.offset
    val level = findPreviousHeader(file, document, caretOffset)?.level ?: 1
    val header = buildString {
      repeat(level) {
        append('#')
      }
      append(' ')
    }
    executeCommand(project) {
      EditorModificationUtil.insertStringAtCaret(editor, header)
    }
    return Result.STOP
  }

  companion object {
    /**
     * Since there are a lot of places there valid headers can not be created,
     * check current context and decide if header completion shouldn't be performed.
     *
     * The main rule for valid header - it should start be at the beginning of the line.
     * This rule basically covers all cases, except headers inside list items and block quotes.
     * For that case, we consider item or quote marker end as the beginning of the current line.
     *
     * This rule can be checked by looking at the current line prefix (see [isValidLinePrefix]).
     */
    // TODO: Handle headers inside block quotes
    private fun shouldIgnore(file: MarkdownFile, editor: Editor): Boolean {
      val offset = editor.caretModel.offset
      val document = editor.document
      val listItem = findListItemForOffset(file, offset, document)
      if (listItem != null) {
        val currentLine = document.getLineNumber(offset)
        val itemContentStartOffset = listItem.obtainContentStartOffset(document, currentLine)
        if (itemContentStartOffset != null && itemContentStartOffset <= offset) {
          val prefix = document.charsSequence.subSequence(itemContentStartOffset, offset)
          return !isValidLinePrefix(file, prefix)
        }
      }
      val prefix = obtainLinePrefix(document, offset)
      return !isValidLinePrefix(file, prefix)
    }

    /**
     * @param line Document plain line number.
     * @return Actual item content start offset for [line].
     */
    private fun MarkdownListItem.obtainContentStartOffset(document: Document, line: Int): Int? {
      val marker = markerElement ?: return null
      val markerEndOffset = marker.endOffset
      val markerLineStartOffset = DocumentUtil.getLineStartOffset(markerEndOffset, document)
      val contentOffsetInsideLine = markerEndOffset - markerLineStartOffset
      val lineStartOffset = document.getLineStartOffset(line)
      return lineStartOffset + contentOffsetInsideLine
    }

    /**
     * Line prefix for valid header should be:
     * * A blank string - every character should be tab or space
     * * Shorter than 4 symbols (since 4+ spaces will create code block)
     */
    private fun isValidLinePrefix(file: MarkdownFile, prefix: CharSequence): Boolean {
      if (prefix.isNotBlank()) {
        return false
      }
      val tabSize = CodeStyle.getFacade(file).tabSize
      val tabReplacement = " ".repeat(tabSize)
      val actualPrefix = prefix.toString().replace("\t", tabReplacement)
      return actualPrefix.length < 4
    }

    private fun obtainLinePrefix(document: Document, offset: Int): CharSequence {
      val lineStart = DocumentUtil.getLineStartOffset(offset, document)
      return document.charsSequence.subSequence(lineStart, offset)
    }

    private fun PsiElement.walkTreeUp(withSelf: Boolean): Sequence<PsiElement> {
      return parents(withSelf = true).flatMap { it.siblings(forward = false, withSelf = withSelf) }
    }

    private fun findPreviousHeader(file: MarkdownFile, document: Document, offset: Int): MarkdownHeader? {
      val startElement = findStartElement(file, document, offset)
      val elements = startElement.walkTreeUp(withSelf = true)
      return elements.filterIsInstance<MarkdownHeader>().firstOrNull()
    }

    private fun findStartElement(file: MarkdownFile, document: Document, offset: Int): PsiElement {
      val listItemBefore = findListItemForOffset(file, offset, document)?.lastChild
      return listItemBefore ?: PsiUtilCore.getElementAtOffset(file, offset)
    }

    private fun findListItemForOffset(file: MarkdownFile, offset: Int, document: Document): MarkdownListItem? {
      val item = findPossibleListItemForOffset(file, offset, document) ?: return null
      val itemLine = document.getLineNumber(item.startOffset)
      val itemIndent = document.getLineIndentSpaces(itemLine, file) ?: return null
      val line = document.getLineNumber(offset)
      val currentIndent = document.getLineIndentSpaces(line, file) ?: return null
      if (currentIndent.startsWith(itemIndent)) {
        return item
      }
      return null
    }

    private fun findPossibleListItemForOffset(file: MarkdownFile, offset: Int, document: Document): MarkdownListItem? {
      val currentLine = document.getLineNumber(offset)
      val minPossibleStartLine = (currentLine - 2).coerceAtLeast(0)
      for (line in (minPossibleStartLine..currentLine).reversed()) {
        val searchOffset = document.getLineEndOffset(line)
        val listItem = file.getListItemAt(searchOffset, document)
        if (listItem != null) {
          return listItem
        }
      }
      return null
    }
  }
}
