// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.util.messageIfStringOrEmpty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.inspections.quickfix.PyRenameUnresolvedRefQuickFix
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferenceQuickFixesImpl
import com.jetbrains.python.inspections.unresolvedReference.getAddParameterQuickFix
import com.jetbrains.python.inspections.unresolvedReference.getTrueFalseQuickFix
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyReferenceExpression
import org.eclipse.lsp4j.Diagnostic
import org.jetbrains.annotations.ApiStatus

/**
 * The diagnostic codes with which a Python LSP tool reports a reference that it cannot resolve.
 *
 * Each tool names these errors in its own way, so each tool states its codes once, and
 * [pyLspUnresolvedReferenceQuickFixes] gives all of them the quick fixes of PyCharm's own inspection.
 */
@ApiStatus.Internal
class PyLspUnresolvedReferenceCodes(
  /** A name that is not defined, such as `print(undefined_var)`. */
  val unknownName: Set<String>,
  /** A module that the tool cannot find, such as `import flask` without `flask` installed. */
  val missingImport: Set<String>,
  /**
   * A module that the tool finds only as stubs, because its package is not installed.
   * The stubs show that the name is correct, so these codes get no rename.
   */
  val missingSource: Set<String> = emptySet(),
  /** A name that an existing module does not define, such as `from os import nonexistent`. */
  val missingModuleAttribute: Set<String>,
)

/**
 * The quick fixes of the unresolved reference inspection for [diagnostic], or an empty list when its
 * code is not one of [codes].
 *
 * The fixes act on the PSI at [textRange], so they need no answer from the server. Tools mark an import
 * in different ways, see [pyLspImportedReference].
 */
@ApiStatus.Internal
fun pyLspUnresolvedReferenceQuickFixes(
  file: PsiFile,
  diagnostic: Diagnostic,
  textRange: TextRange,
  codes: PyLspUnresolvedReferenceCodes,
): List<IntentionAction> {
  val code = diagnostic.code?.left ?: return emptyList()
  val description = diagnostic.messageIfStringOrEmpty
  return when (code) {
    in codes.unknownName -> unknownNameQuickFixes(file, textRange, description)
    in codes.missingImport -> importQuickFixes(file, textRange, description, installPackage = true)
    in codes.missingModuleAttribute -> importQuickFixes(file, textRange, description, installPackage = false)
    in codes.missingSource -> missingSourceQuickFixes(file, textRange, description)
    else -> emptyList()
  }
}

private fun unknownNameQuickFixes(file: PsiFile, textRange: TextRange, description: @NlsSafe String): List<IntentionAction> {
  val node = PsiTreeUtil.findElementOfClassAtRange(file, textRange.startOffset, textRange.endOffset, PyReferenceExpression::class.java)
             ?: return emptyList()
  val fixes = buildList {
    val referencedName = node.referencedName
    if (referencedName != null && !node.isQualified) {
      addIfNotNull(getTrueFalseQuickFix(referencedName))
      addIfNotNull(getAddParameterQuickFix(referencedName, node))
      add(PyRenameUnresolvedRefQuickFix())
    }
    addAll(PyUnresolvedReferenceQuickFixesImpl.getAutoImportFixes(node, node.reference, node))
  }
  return pyLspWrapQuickFixes(node, description, fixes)
}

private fun importQuickFixes(
  file: PsiFile,
  textRange: TextRange,
  description: @NlsSafe String,
  installPackage: Boolean,
): List<IntentionAction> {
  val topmostQualifier = pyLspImportedReference(file, textRange) ?: return emptyList()
  val fixes = buildList {
    add(PyRenameUnresolvedRefQuickFix())
    if (installPackage) {
      addAll(installPackageQuickFixes(topmostQualifier))
    }
    addAll(PyUnresolvedReferenceQuickFixesImpl.getImportStatementQuickFixes(topmostQualifier))
  }
  return pyLspWrapQuickFixes(topmostQualifier, description, fixes)
}

private fun missingSourceQuickFixes(file: PsiFile, textRange: TextRange, description: @NlsSafe String): List<IntentionAction> {
  val topmostQualifier = pyLspImportedReference(file, textRange) ?: return emptyList()
  return pyLspWrapQuickFixes(topmostQualifier, description, installPackageQuickFixes(topmostQualifier))
}

private fun installPackageQuickFixes(topmostQualifier: PyReferenceExpression): List<LocalQuickFix> {
  val referencedName = topmostQualifier.referencedName ?: return emptyList()
  return PyUnresolvedReferenceQuickFixesImpl.getInstallPackageQuickFixes(topmostQualifier, topmostQualifier.reference, referencedName)
}

/**
 * The topmost qualifier of the module reference that a diagnostic at [textRange] marks, or `null` when
 * the range marks no import. For `import flask.json` this is `flask`.
 *
 * Pyrefly marks the whole import element, or the whole `from` import. Zuban marks only the module name
 * inside the import, so a reference expression inside an import statement counts too.
 */
@ApiStatus.Internal
fun pyLspImportedReference(file: PsiFile, textRange: TextRange): PyReferenceExpression? {
  val reference = findImportedReference(file, textRange) ?: return null
  return generateSequence(reference) { it.qualifier as? PyReferenceExpression }.last()
}

private fun findImportedReference(file: PsiFile, textRange: TextRange): PyReferenceExpression? {
  val start = textRange.startOffset
  val end = textRange.endOffset
  PsiTreeUtil.findElementOfClassAtRange(file, start, end, PyImportElement::class.java)
    ?.let { return it.importReferenceExpression }
  PsiTreeUtil.findElementOfClassAtRange(file, start, end, PyFromImportStatement::class.java)
    ?.let { return it.importSource }
  return PsiTreeUtil.findElementOfClassAtRange(file, start, end, PyReferenceExpression::class.java)
    ?.takeIf { PsiTreeUtil.getParentOfType(it, PyImportStatementBase::class.java) != null }
}

/**
 * [fixes] as intentions on [element], so the editor offers them for a diagnostic of a language server.
 * The [description] is the message of the diagnostic.
 */
@ApiStatus.Internal
fun pyLspWrapQuickFixes(element: PsiElement, description: @NlsSafe String, fixes: List<LocalQuickFix>): List<IntentionAction> {
  if (fixes.isEmpty()) return emptyList()
  val descriptor = InspectionManager.getInstance(element.project).createProblemDescriptor(
    element, description, true, fixes.toTypedArray(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING
  )
  return fixes.map { QuickFixWrapper.wrap(descriptor, it) }
}
