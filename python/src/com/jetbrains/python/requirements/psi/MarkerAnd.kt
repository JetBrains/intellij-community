// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.requirements.psi

import com.intellij.psi.PsiElement
import com.jetbrains.python.requirements.And
import com.jetbrains.python.requirements.Logical

interface MarkerAnd : PsiElement {

  val markerExprList: List<MarkerExpr>

  fun logical(): Logical {
    val expr = mutableListOf<Logical>()

    for (expression in markerExprList) {
      expr.add(expression.logical())
    }
    return And(*expr.toTypedArray())
  }
}
