// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiUtil

fun PsiVariable.hasWriteAccesses(searcher: ReferenceSearcher, scope: PsiElement?): Boolean =
    if (scope != null) searcher.findVariableUsages(this, scope).any { PsiUtil.isAccessedForWriting(it) } else false

fun ReferenceSearcher.findVariableUsages(variable: PsiVariable, scope: PsiElement): Collection<PsiReferenceExpression> =
    findLocalUsages(variable, scope).filterIsInstance<PsiReferenceExpression>()
