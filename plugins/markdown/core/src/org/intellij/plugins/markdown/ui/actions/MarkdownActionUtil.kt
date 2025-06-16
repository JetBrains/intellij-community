// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.util.parents
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.lang.isMarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.jetbrains.annotations.ApiStatus
import org.intellij.plugins.markdown.lang.supportsMarkdown

@ApiStatus.Internal
object MarkdownActionUtil {
  @RequiresEdt
  @JvmStatic
  fun findSplitEditor(event: AnActionEvent): MarkdownEditorWithPreview? {
    val editor = event.getData(PlatformCoreDataKeys.FILE_EDITOR)
    return findSplitEditor(editor)
  }

  @RequiresEdt
  @JvmStatic
  fun findSplitEditor(editor: FileEditor?): MarkdownEditorWithPreview? {
    if (editor == null) return null
    return when (editor) {
      is MarkdownEditorWithPreview -> editor
      else -> TextEditorWithPreview.getParentSplitEditor(editor) as? MarkdownEditorWithPreview
    }
  }

  @RequiresEdt
  @JvmStatic
  fun findMarkdownPreviewEditor(event: AnActionEvent): MarkdownPreviewFileEditor? {
    val splitEditor = findSplitEditor(event) ?: return null
    val editor = splitEditor.previewEditor
    return when {
      editor !is MarkdownPreviewFileEditor || !editor.component.isVisible -> null
      else -> editor
    }
  }

  /**
   * @param strictMarkdown If true, requires pure Markdown language;
   * if false, allows Markdown-compatible languages (such as Jupyter).
   */
  @JvmStatic
  @JvmOverloads
  fun findMarkdownEditor(event: AnActionEvent, strictMarkdown: Boolean = false): Editor? {
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return null
    return when {
      if (strictMarkdown) file.language.isMarkdownLanguage() else file.language.supportsMarkdown() ->
        event.getData(CommonDataKeys.EDITOR)
      else -> null
    }
  }

  @RequiresEdt
  @JvmStatic
  fun getElementsUnderCaretOrSelection(file: PsiFile, caret: Caret): Pair<PsiElement, PsiElement> {
    return getElementsUnderCaretOrSelection(file, caret.selectionStart, caret.selectionEnd)
  }

  @JvmStatic
  fun getElementsUnderCaretOrSelection(file: PsiFile, selectionStart: Int, selectionEnd: Int): Pair<PsiElement, PsiElement> {
    if (selectionStart == selectionEnd) {
      val element = PsiUtilBase.getElementAtOffset(file, selectionStart)
      return element to element
    }
    val startElement = PsiUtilBase.getElementAtOffset(file, selectionStart)
    val endElement = PsiUtilBase.getElementAtOffset(file, selectionEnd)
    return startElement to endElement
  }

  @JvmStatic
  fun getCommonParentOfType(left: PsiElement, right: PsiElement, elementType: IElementType): PsiElement? {
    return getCommonParentOfTypes(left, right, TokenSet.create(elementType))
  }

  @JvmStatic
  fun getCommonTopmostParentOfTypes(left: PsiElement, right: PsiElement, types: TokenSet): PsiElement? {
    val base = PsiTreeUtil.findCommonParent(left, right)
    return base?.parents(withSelf = true)?.findLast { it.hasType(types) }
  }

  @JvmStatic
  fun getCommonParentOfTypes(left: PsiElement, right: PsiElement, types: TokenSet): PsiElement? {
    val base = PsiTreeUtil.findCommonParent(left, right)
    return base?.parents(withSelf = true)?.find { it.hasType(types) }
  }
}
