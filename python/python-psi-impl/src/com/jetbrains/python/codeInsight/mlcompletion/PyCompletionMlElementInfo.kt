// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyClassImpl
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.impl.PyFunctionImpl
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PyCompletionMlElementKind {
  NAMED_ARG,
  KEYWORD,
  PACKAGE_OR_MODULE,
  FROM_TARGET,
  TYPE_OR_CLASS,
  FUNCTION,
  UNKNOWN;

  fun asInfo(): PyCompletionMlElementInfo {
    return PyCompletionMlElementInfo(this, false)
  }
}

@ApiStatus.Internal
data class PyCompletionMlElementInfo(val kind: PyCompletionMlElementKind, val isBuiltins: Boolean = false) {
  companion object {
    val key = Key<PyCompletionMlElementInfo>("py.ml.completion.element.info")

    fun fromElement(element: PsiElement): PyCompletionMlElementInfo {
      val kind = when (element) {
        is PyFileImpl -> PyCompletionMlElementKind.PACKAGE_OR_MODULE
        is PyTargetExpressionImpl -> PyCompletionMlElementKind.FROM_TARGET
        is PyClassImpl -> PyCompletionMlElementKind.TYPE_OR_CLASS
        is PyFunctionImpl -> PyCompletionMlElementKind.FUNCTION
        else -> PyCompletionMlElementKind.UNKNOWN
      }
      val isBuiltins = PyBuiltinCache.getInstance(element).isBuiltin(element)
      return PyCompletionMlElementInfo(kind, isBuiltins)
    }
  }
}