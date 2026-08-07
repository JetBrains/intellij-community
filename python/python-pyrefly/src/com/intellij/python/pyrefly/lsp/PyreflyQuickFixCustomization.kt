package com.intellij.python.pyrefly.lsp

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.inspections.quickfix.PyRenameUnresolvedRefQuickFix
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferenceQuickFixesImpl
import com.jetbrains.python.inspections.unresolvedReference.getAddParameterQuickFix
import com.jetbrains.python.inspections.unresolvedReference.getTrueFalseQuickFix
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyReferenceExpression
import org.eclipse.lsp4j.Diagnostic

private const val UNKNOWN_NAME = "unknown-name"
private const val MISSING_IMPORT = "missing-import"
private const val MISSING_MODULE_ATTRIBUTE = "missing-module-attribute"

internal fun customizePyreflyQuickFixes(
  holder: AnnotationHolder,
  diagnostic: Diagnostic,
  textRange: TextRange,
  quickFixes: List<IntentionAction>,
): List<IntentionAction> {
  return buildList {
    val file = holder.currentAnnotationSession.file
    val code = diagnostic.code?.left
    val description = diagnostic.message

    if (code == UNKNOWN_NAME) {
      val node = PsiTreeUtil.findElementOfClassAtRange(
        file,
        textRange.startOffset,
        textRange.endOffset,
        PyReferenceExpression::class.java
      )
      if (node != null) {
        val fixes = buildList {
          addAll(unresolvedNameFixes(node))
          addAll(PyUnresolvedReferenceQuickFixesImpl.getAutoImportFixes(node, node.reference, node))
        }
        addFixes(node, description, fixes)
      }
    }
    else if (code == MISSING_IMPORT || code == MISSING_MODULE_ATTRIBUTE) {
      val node = getImportedReference(file, textRange)
      if (node != null) {
        val fixes = buildList {
          addAll(unresolvedNameFixes(node))
          node.referencedName?.let { referencedName ->
            addAll(PyUnresolvedReferenceQuickFixesImpl.getInstallPackageQuickFixes(node, node.reference, referencedName))
          }
          addAll(PyUnresolvedReferenceQuickFixesImpl.getImportStatementQuickFixes(node))
        }
        addFixes(node, description, fixes)
      }
    }

    addAll(quickFixes)
  }
}

private fun getImportedReference(file: PsiFile, textRange: TextRange): PyReferenceExpression? {
  val importElement = PsiTreeUtil.findElementOfClassAtRange(file, textRange.startOffset,
                                                            textRange.endOffset, PyImportElement::class.java)
  if (importElement != null) {
    return importElement.importReferenceExpression
  }

  val fromImport = PsiTreeUtil.findElementOfClassAtRange(file, textRange.startOffset,
                                                         textRange.endOffset, PyFromImportStatement::class.java)
  return fromImport?.importSource
}

private fun unresolvedNameFixes(expr: PyReferenceExpression): List<LocalQuickFix> {
  if (expr.isQualified) return emptyList()
  val refName = expr.referencedName ?: return emptyList()

  return listOfNotNull(
    getTrueFalseQuickFix(refName),
    getAddParameterQuickFix(refName, expr),
    PyRenameUnresolvedRefQuickFix(),
  )
}

private fun MutableList<IntentionAction>.addFixes(
  psiElement: PsiElement,
  description: @NlsSafe String,
  fixes: List<LocalQuickFix>,
) {
  if (fixes.isEmpty()) return

  val descriptor = InspectionManager.getInstance(psiElement.project).createProblemDescriptor(
    psiElement,
    description,
    true,
    fixes.toTypedArray(),
    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
  )
  fixes.mapTo(this) { QuickFixWrapper.wrap(descriptor, it) }
}
