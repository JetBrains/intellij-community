// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * Enter handler of Markdown plugin,
 *
 * It generates blockquotes on `enter`.
 * Also it stops indentation when there is >= 2 new lines after text
 */
internal class MarkdownEnterHandler : EnterHandlerDelegateAdapter() {
  /**
   * During preprocessing indentation can be stopped if there are more than
   * two new lines after last element of text in Markdown file.
   *
   * E.g. it means, that there will be no indent if you will hit enter two times
   * after list item on any indent level.
   *
   * Also, for non-toplevel codefences indentation is implemented via preprocessing.
   * The actual reason for it is that injection-based formatting does not work
   * correctly in frankenstein-like injection (for example, for codefence inside blockquote)
   */
  override fun preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>,
                               dataContext: DataContext, originalHandler: EditorActionHandler?): Result {
    val offset = editor.caretModel.offset
    val element = MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, offset) ?: return Result.Continue
    if (!file.isValid || !shouldHandle(editor, dataContext, element)) return Result.Continue

    if (shouldAbortIndentation(file, editor, caretOffset.get())) {
      EditorModificationUtil.insertStringAtCaret(editor, "\n")
      return Result.Stop
    }

    val fence = MarkdownCodeFenceUtils.getCodeFence(element)
    if (fence != null && !MarkdownPsiUtil.isTopLevel(fence.node)) {
      val indent = MarkdownCodeFenceUtils.getIndent(fence) ?: return Result.Continue

      EditorModificationUtil.insertStringAtCaret(editor, "\n${indent}")
      return Result.Stop
    }

    return Result.Continue
  }

  /**
   * During post-processing `>` can be added if it is necessary
   */
  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
    val offset = editor.caretModel.offset
    val element = MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, offset) ?: return Result.Continue
    if (!file.isValid || !shouldHandle(editor, dataContext, element)) return Result.Continue

    processBlockQuote(editor, element)

    return Result.Continue
  }


  private fun processBlockQuote(editor: Editor, element: PsiElement) {
    val quote = PsiTreeUtil.getParentOfType(element, MarkdownBlockQuote::class.java) ?: return
    val markdown = CodeStyle.getCustomSettings(quote.containingFile, MarkdownCustomCodeStyleSettings::class.java)

    var toAdd = ">"
    if (markdown.FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL) {
      toAdd += " "
    }
    EditorModificationUtil.insertStringAtCaret(editor, toAdd)
  }

  /**
   * Check if alignment process should not be performed for this offset at all.
   *
   * Alignment of enter would not be performed if there is >= 2 new lines after
   * last text element.
   */
  private fun shouldAbortIndentation(file: PsiFile, editor: Editor, offset: Int): Boolean {
    //do not stop indentation after two spaces in code fences
    if (
      file !is MarkdownFile
      || file.findElementAt(offset - 1)?.let { MarkdownCodeFenceUtils.inCodeFence(it.node) } == true
    ) {
      return false
    }

    val text = editor.document.charsSequence.toString()

    var cur = offset - 1
    while (cur > 0) {
      val char = text.getOrNull(cur)

      if (char == null) {
        cur--
        continue
      }

      if (char.isWhitespace().not()) {
        break
      }

      if (char == '\n') {
        return true
      }

      cur--
    }

    return false
  }

  private fun shouldHandle(editor: Editor, dataContext: DataContext, element: PsiElement): Boolean {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false

    if (!editor.document.isWritable) return false
    if (InjectedLanguageManager.getInstance(project).getTopLevelFile(element) !is MarkdownFile) return false

    return !editor.isViewer
  }
}