// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiType

fun PsiType.toEllipsisTypeIfNeeded(isVarargs: Boolean) =
    if (isVarargs && this is PsiArrayType) PsiEllipsisType(componentType, annotationProvider) else this