// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionMessages
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class PyPackageInstallAllProblemInfo(
  val psiElement: PsiElement,
  val message: PyInspectionMessages.ProblemMessage,
  val highlightType: ProblemHighlightType,
  val refName: String,
  val fixes: List<LocalQuickFix>,
)
