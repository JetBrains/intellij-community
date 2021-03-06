// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingAnnotationInjector
import com.jetbrains.python.psi.PyStatement

class TypeIgnoreSuppressingCommentLocalityDetector: ChangeLocalityDetector {
  override fun getChangeHighlightingDirtyScopeFor(changedElement: PsiElement): PsiElement? {
    if (changedElement is PsiComment && PyTypingAnnotationInjector.isTypeIgnoreComment(changedElement)) {
      return PsiTreeUtil.getParentOfType(changedElement, PyStatement::class.java)
    }
    return null
  }
}