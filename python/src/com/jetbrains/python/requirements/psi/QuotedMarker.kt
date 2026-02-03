// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.psi

import com.intellij.psi.PsiElement
import com.jetbrains.python.requirements.Logical

interface QuotedMarker : PsiElement {
  val markerOr: MarkerOr

  fun logical(): Logical {
    return markerOr.logical()
  }
}
