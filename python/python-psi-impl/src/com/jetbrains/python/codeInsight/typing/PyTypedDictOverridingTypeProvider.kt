// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.impl.PyOverridingTypeProvider
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyTypedDictOverridingTypeProvider : PyTypeProviderBase(), PyOverridingTypeProvider {

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val type = PyTypedDictTypeProvider.getTypedDictTypeForResolvedCallee(referenceTarget, context)

    return PyTypeUtil.notNullToRef(type)
  }
}