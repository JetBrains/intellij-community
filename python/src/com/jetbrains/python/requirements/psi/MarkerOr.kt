// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.requirements.psi

import com.intellij.psi.PsiElement
import com.jetbrains.python.requirements.Logical
import com.jetbrains.python.requirements.Or

interface MarkerOr : PsiElement {
  val markerAndList: List<MarkerAnd>

  fun logical(): Logical {
    val ands = mutableListOf<Logical>()
    for (marker in markerAndList) {
      ands.add(marker.logical())
    }
    return Or(*ands.toTypedArray())
  }
}
