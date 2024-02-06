// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.requirements.psi

import com.intellij.psi.PsiElement
import com.jetbrains.python.requirements.Expression
import com.jetbrains.python.requirements.False
import com.jetbrains.python.requirements.Logical
import com.jetbrains.python.requirements.True

interface MarkerExpr : PsiElement {
  val markerOp: MarkerOp?

  val markerOr: MarkerOr?

  val markerVarList: List<MarkerVar>

  fun logical(): Logical {
    val marker = markerOr

    if (marker != null) {
      return marker.logical()
    }

    val operation = markerOp?.text

    if (markerVarList.isEmpty()) {
      return False()
    }

    if (markerVarList.size == 1) {
      return True()
    }

    if (operation == null) {
      return False()
    }

    val variable = markerVarList[0].text
    var value = markerVarList[1].text
    value = value.substring(1, value.length - 1)

    return Expression(variable, operation, value)
  }
}
