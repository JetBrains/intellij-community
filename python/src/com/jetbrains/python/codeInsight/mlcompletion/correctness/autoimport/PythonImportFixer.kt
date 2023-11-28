package com.jetbrains.python.codeInsight.mlcompletion.correctness.autoimport

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.platform.ml.impl.correctness.autoimport.InspectionBasedImportFixer
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFileElementType

class PythonImportFixer : InspectionBasedImportFixer() {
  override fun getAutoImportInspections(element: PsiElement?) =
    PyUnresolvedReferencesInspection.getInstance(element)?.let { listOf(it) } ?: listOf()

  override fun filterApplicableFixes(fixes: List<LocalQuickFixOnPsiElement>): List<AutoImportQuickFix> {
    val fixesToApply = fixes.filterIsInstance<AutoImportQuickFix>()
      .filter { it.isAvailable }
      .filter {
        it.candidates.any {
          val importable = it.importable
          // Check that an importing element is a module or class
          importable is PyClass || importable is PsiDirectory || importable.elementType is PyFileElementType
        }
      }
    return fixesToApply
  }
}
