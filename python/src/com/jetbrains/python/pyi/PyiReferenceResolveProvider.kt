// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.pyi

import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

class PyiReferenceResolveProvider: PyReferenceResolveProvider {

  override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> = emptyList()

  override fun allowsForwardOutgoingReferencesInClass(element: PyQualifiedExpression): Boolean {
    return PyiUtil.isInsideStubAnnotation(element)
  }
}