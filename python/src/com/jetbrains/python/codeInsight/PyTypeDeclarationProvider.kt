// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypeDeclarationProvider : TypeDeclarationProvider {

  override fun getSymbolTypeDeclarations(symbol: PsiElement): Array<PsiElement>? {
    if (symbol is PyTypedElement) {
      val context = TypeEvalContext.userInitiated(symbol.project, symbol.containingFile)
      var type = context.getType(symbol)

      // When the symbol is a function (e.g. resolved from a pytest fixture reference),
      // navigate to the return type declaration instead of the function type itself.
      if (type is PyFunctionType && symbol is PyFunction) {
        type = context.getReturnType(symbol)
      }

      return PyTypeUtil
        .toStream(type)
        .nonNull()
        .mapNotNull { it.declarationElement }
        .distinct()
        .toTypedArray<PsiElement>()
        .takeIf { it.isNotEmpty() }
    }

    return null
  }
}