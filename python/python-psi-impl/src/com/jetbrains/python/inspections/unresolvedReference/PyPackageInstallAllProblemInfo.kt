package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElement

data class PyPackageInstallAllProblemInfo(
  val psiElement: PsiElement,
  val descriptionTemplate: @InspectionMessage String,
  val highlightType: ProblemHighlightType,
  val refName: String,
  val fixes: List<LocalQuickFix>,
)
