// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyUtil

/**
 * Folds type annotations in Python code
 */
class PyHideTypeAnnotationsIntention : PyBaseIntentionAction() {
  override fun getFamilyName(): String {
    return PyPsiBundle.message("INTN.NAME.hide.type.annotations")
  }

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    if (psiFile !is PyFile) return false
    val offset = TargetElementUtilBase.adjustOffset(psiFile, editor?.document ?: return false, editor.caretModel.offset)
    val element = PyUtil.findNonWhitespaceAtOffset(psiFile, offset)
    val annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation::class.java)
    text = PyPsiBundle.message("INTN.hide.type.annotations")
    return annotation != null
  }

  override fun doInvoke(project: Project, editor: Editor, file: PsiFile) {
    val allRegions = editor.foldingModel.allFoldRegions
    val result = allRegions.filter { it.group != null && it.group.toString() == "Python type annotation" }

    editor.foldingModel.runBatchFoldingOperation {
      for (region in result) {
        region.isExpanded = false
      }
    }
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}
