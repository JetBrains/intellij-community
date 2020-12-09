// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypeDeclarationProvider : TypeDeclarationProvider {

  override fun getSymbolTypeDeclarations(symbol: PsiElement): Array<PsiElement>? {
    if (symbol is PyTypedElement) {
      val context = TypeEvalContext.userInitiated(symbol.project, symbol.containingFile)
      return PyTypeUtil
        .toStream(context.getType(symbol))
        .nonNull()
        .mapNotNull { it.declarationElement }
        .distinct()
        .toTypedArray<PsiElement>()
        .takeIf { it.isNotEmpty() }
    }

    return null
  }
}