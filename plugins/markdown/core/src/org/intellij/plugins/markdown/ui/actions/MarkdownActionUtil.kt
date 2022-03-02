// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.application
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor

internal object MarkdownActionUtil {
  @JvmStatic
  fun findSplitEditor(event: AnActionEvent): MarkdownEditorWithPreview? {
    val editor = event.getData(PlatformCoreDataKeys.FILE_EDITOR)
    return findSplitEditor(editor)
  }

  @JvmStatic
  fun findSplitEditor(editor: FileEditor?): MarkdownEditorWithPreview? {
    return when (editor) {
      is MarkdownEditorWithPreview -> editor
      else -> editor?.getUserData(MarkdownEditorWithPreview.PARENT_SPLIT_EDITOR_KEY)
    }
  }

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
  fun findMarkdownTextEditor(event: AnActionEvent): Editor? {
    val splitEditor = findSplitEditor(event)
    if (splitEditor == null) {
      // This fallback is used primarily for testing
      val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return null
      return when {
        psiFile.language == MarkdownLanguage.INSTANCE && application.isUnitTestMode -> event.getData(CommonDataKeys.EDITOR)
        else -> null
      }
    }
    val mainEditor = splitEditor.textEditor
    return when {
      !mainEditor.component.isVisible -> null
      else -> mainEditor.editor
    }
  }

  @JvmStatic
  fun getElementsUnderCaretOrSelection(file: PsiFile, caret: Caret): Pair<PsiElement, PsiElement> {
    if (caret.selectionStart == caret.selectionEnd) {
      val element = PsiUtilBase.getElementAtOffset(file, caret.selectionStart)
      return element to element
    }
    val startElement = PsiUtilBase.getElementAtOffset(file, caret.selectionStart)
    val endElement = PsiUtilBase.getElementAtOffset(file, caret.selectionEnd)
    return startElement to endElement
  }

  @JvmStatic
  fun getCommonParentOfType(left: PsiElement, right: PsiElement, elementType: IElementType): PsiElement? {
    return getCommonParentOfTypes(left, right, TokenSet.create(elementType))
  }

  @JvmStatic
  fun getCommonTopmostParentOfTypes(left: PsiElement, right: PsiElement, tokenSet: TokenSet): PsiElement? {
    val base = PsiTreeUtil.findCommonParent(left, right)
    return getTopmostParentOfType(base, Condition {
      val node = it.node
      node != null && tokenSet.contains(node.elementType)
    })
  }

  @JvmStatic
  fun getTopmostParentOfType(element: PsiElement?, condition: Condition<in PsiElement>): PsiElement? {
    var answer = PsiTreeUtil.findFirstParent(element, false, condition)
    do {
      val next = PsiTreeUtil.findFirstParent(answer, true, condition) ?: break
      answer = next
    } while (true)
    return answer
  }

  @JvmStatic
  fun getCommonParentOfTypes(left: PsiElement, right: PsiElement, tokenSet: TokenSet): PsiElement? {
    val base = PsiTreeUtil.findCommonParent(left, right)
    return PsiTreeUtil.findFirstParent(base, false) {
      val node = it.node
      node != null && tokenSet.contains(node.elementType)
    }
  }
}
