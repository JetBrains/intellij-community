// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTypedDictGoToDeclarationProvider : GotoDeclarationHandlerBase() {
  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor?): PsiElement? {
    val pyReferenceElement = sourceElement?.parent
    if (pyReferenceElement !is PyReferenceExpression || editor == null) return null
    val resolvedClass = pyReferenceElement.reference.resolve() as? PyClass ?: return null
    if (resolvedClass.isTypingTypedDictInheritor(
        TypeEvalContext.userInitiated(sourceElement.project, sourceElement.containingFile)
      )
    ) {
      return resolvedClass
    }

    return null
  }
}