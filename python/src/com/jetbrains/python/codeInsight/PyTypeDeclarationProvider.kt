// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.navigation.actions.TypeDeclarationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeUtil.componentSequence
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypeDeclarationProvider : TypeDeclarationProvider {

  override fun getSymbolTypeDeclarations(symbol: PsiElement): Array<out PsiElement>? {
    if (symbol is PyTypedElement) {
      val context = TypeEvalContext.userInitiated(symbol.project, symbol.containingFile)

      // When the symbol is a function,
      // navigate to the return type declaration instead of the function type itself.
      val type = when (val type = context.getType(symbol)) {
        is PyCallableType if type !is PyClassType -> type.getReturnType(context)
        else -> type
      }

      return type.componentSequence
        .filterNotNull()
        .mapNotNull { it.declarationElement }
        .distinct()
        .toList().toTypedArray()
        .takeIf { it.isNotEmpty() }
    }

    return null
  }
}