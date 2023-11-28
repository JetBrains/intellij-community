// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.mlcompletion.correctness.checker

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.platform.ml.impl.correctness.MLCompletionCorrectnessSupporter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.mlcompletion.PyMlCompletionHelpers
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyCallingNonCallableInspection
import com.jetbrains.python.inspections.PyRedeclarationInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyImportStatementBase
import com.intellij.platform.ml.impl.correctness.checker.CorrectnessError
import com.intellij.platform.ml.impl.correctness.checker.InspectionBasedSemanticChecker
import com.intellij.platform.ml.impl.correctness.checker.Severity
import com.jetbrains.python.codeInsight.mlcompletion.correctness.PythonMLCompletionCorrectnessSupporter

object PyUnresolvedReferencesSemanticChecker : InspectionBasedSemanticChecker(PyUnresolvedReferencesInspection()) {
  override fun convertInspectionsResults(originalPsi: PsiFile,
                                         problemDescriptors: List<ProblemDescriptor>,
                                         offset: Int,
                                         prefix: String,
                                         suggestion: String): List<CorrectnessError> =
    problemDescriptors.filterErrorsInsideUnresolvedWellKnownImports().mapNotNull { problemDescriptor ->
      val severity = getErrorSeverity(problemDescriptor)
      val location = getLocationInSuggestion(problemDescriptor, offset, prefix, suggestion) ?: return@mapNotNull null
      CorrectnessError(location, severity)
  }

  private fun List<ProblemDescriptor>.filterErrorsInsideUnresolvedWellKnownImports(): List<ProblemDescriptor> {
    val unresolvedWellKnownProblems = filter {
      val fromStatement = it.psiElement.parentOfType<PyFromImportStatement>() ?: return@filter false
      val importSource = fromStatement.importSource ?: return@filter false
      getErrorRangeInFile(it) in importSource.textRange &&
      it.psiElement.text in PyMlCompletionHelpers.importPopularity
    } + filter {
      val importStatement = it.psiElement.parentOfType<PyImportStatement>() ?: return@filter false
      importStatement.importElements.all { element ->
        val firstName = element.importedQName?.components?.firstOrNull() ?: return@all true
        getErrorRangeInFile(it) in TextRange(0, firstName.length).shiftRight(element.startOffset) &&
        firstName in PyMlCompletionHelpers.importPopularity
      }
    }
    val ignoreStartOffsets = unresolvedWellKnownProblems.mapNotNull {
      it.psiElement.parentOfType<PyImportStatementBase>()?.startOffset
    }.toSet()
    return filter {
      val importStatement = it.psiElement.parentOfType<PyImportStatementBase>() ?: return@filter true
      importStatement.startOffset !in ignoreStartOffsets
    }
  }

  private fun getErrorSeverity(problemDescriptor: ProblemDescriptor): Severity {
    problemDescriptor.psiElement.parentOfType<PyImportStatementBase>()?.let {
      // errors inside import are critical
      return Severity.CRITICAL
    }

    val importFixer = (MLCompletionCorrectnessSupporter.getInstance(PythonLanguage.INSTANCE) as PythonMLCompletionCorrectnessSupporter).importFixer
    return if (importFixer.areFixableByAutoImport(listOf(problemDescriptor))) {
      Severity.ACCEPTABLE
    } else {
      Severity.CRITICAL
    }
  }
}

object PyCallingNonCallableSemanticChecker : InspectionBasedSemanticChecker(PyCallingNonCallableInspection()) {
  override fun convertInspectionsResults(originalPsi: PsiFile,
                                         problemDescriptors: List<ProblemDescriptor>,
                                         offset: Int,
                                         prefix: String,
                                         suggestion: String): List<CorrectnessError> =
    problemDescriptors.mapNotNull { problemDescriptor ->
      val location = getLocationInSuggestion(problemDescriptor, offset, prefix, suggestion) ?: return@mapNotNull null
      CorrectnessError(location, Severity.CRITICAL)
  }
}

object PyArgumentListSemanticChecker : InspectionBasedSemanticChecker(PyArgumentListInspection()) {
  override fun convertInspectionsResults(originalPsi: PsiFile,
                                         problemDescriptors: List<ProblemDescriptor>,
                                         offset: Int,
                                         prefix: String,
                                         suggestion: String): List<CorrectnessError> =
    problemDescriptors.mapNotNull { problemDescriptor ->
      val location = getLocationInSuggestion(problemDescriptor, offset, prefix, suggestion) ?: return@mapNotNull null
    if (problemDescriptor.highlightType == ProblemHighlightType.INFORMATION) {
      return@mapNotNull null
    }
    val elementType = problemDescriptor.psiElement.node.elementType
    if (elementType === PyTokenTypes.RPAR) {
      return@mapNotNull null
    }
      CorrectnessError(location, Severity.CRITICAL)
  }
}

object PyRedeclarationSemanticChecker : InspectionBasedSemanticChecker(PyRedeclarationInspection()) {
  override fun convertInspectionsResults(originalPsi: PsiFile,
                                         problemDescriptors: List<ProblemDescriptor>,
                                         offset: Int,
                                         prefix: String,
                                         suggestion: String): List<CorrectnessError> =
    problemDescriptors.mapNotNull { problemDescriptor ->
      val location = getLocationInSuggestion(problemDescriptor, offset, prefix, suggestion) ?: return@mapNotNull null
      CorrectnessError(location, Severity.CRITICAL)
  }
}