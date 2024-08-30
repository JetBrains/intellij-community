package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElement

data class PyPackageInstallAllProblemInfo(
  val psiElement: PsiElement,
  @InspectionMessage val descriptionTemplate: String,
  val highlightType: ProblemHighlightType,
  val refName: String
)
