package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspectionMessages

data class PyPackageInstallAllProblemInfo(
  val psiElement: PsiElement,
  val message: PyInspectionMessages.ProblemMessage,
  val highlightType: ProblemHighlightType,
  val refName: String,
  val fixes: List<LocalQuickFix>,
)
