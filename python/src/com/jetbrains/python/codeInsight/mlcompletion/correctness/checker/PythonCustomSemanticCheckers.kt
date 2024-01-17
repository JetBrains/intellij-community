package com.jetbrains.python.codeInsight.mlcompletion.correctness.checker

import com.intellij.platform.ml.impl.correctness.checker.CorrectnessError
import com.intellij.platform.ml.impl.correctness.checker.CustomSemanticChecker
import com.intellij.platform.ml.impl.correctness.checker.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.ProjectScope
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

object PyAssignmentToLibraryScopeSemanticChecker : CustomSemanticChecker() {
  override fun findErrors(originalPsi: PsiFile,
                          element: PsiElement,
                          offset: Int,
                          prefix: String,
                          suggestion: String): List<CorrectnessError> {
    val assignment = element as? PyAssignmentStatement ?: return emptyList()
    val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, originalPsi)
    val resolveContext = PyResolveContext.defaultContext(typeEvalContext)
    val librariesScope = ProjectScope.getLibrariesScope(element.project)
    return assignment.targets
      .filterIsInstance<PyTargetExpression>()
      .filter {
        val declarationElement = it.getReference(resolveContext).resolve() ?: return@filter false
        val virtualFile = declarationElement.containingFile?.virtualFile ?: return@filter false
        librariesScope.contains(virtualFile)
      }.mapNotNull {
        val location = getLocationInSuggestion(it.textRange, offset, prefix, suggestion) ?: return@mapNotNull null
        CorrectnessError(location, Severity.CRITICAL, javaClass.getSimpleName())
      }
  }

}