// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
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
    return when (editor) {
      is MarkdownEditorWithPreview -> editor
      else -> editor?.getUserData(MarkdownEditorWithPreview.PARENT_SPLIT_EDITOR_KEY)
    }
  }

  @RequiresEdt
  @JvmStatic
  fun findMarkdownPreviewEditor(event: AnActionEvent): MarkdownPreviewFileEditor? {
    val splitEditor = findSplitEditor(event) ?: return null
    val editor = splitEditor.previewEditor
    return when {
      editor !is MarkdownPreviewFileEditor || !editor.getComponent().isVisible -> null
      else -> editor
    }
  }

  @JvmStatic
  fun findMarkdownEditor(event: AnActionEvent): Editor? {
    val file = event.getData(CommonDataKeys.PSI_FILE) ?: return null
    return when {
      file.language.isMarkdownLanguage() -> event.getData(CommonDataKeys.EDITOR)
      else -> null
    }
  }

  @JvmStatic
  fun findRequiredMarkdownEditor(event: AnActionEvent): Editor {
    val editor = findMarkdownEditor(event)
    return checkNotNull(editor) { "Markdown editor was expected to be found in data context" }
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
