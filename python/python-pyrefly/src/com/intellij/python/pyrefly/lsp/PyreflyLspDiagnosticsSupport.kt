package com.intellij.python.pyrefly.lsp

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.python.lsp.core.PyLspUnresolvedReferenceCodes
import com.intellij.python.lsp.core.pyLspUnresolvedReferenceQuickFixes
import org.eclipse.lsp4j.Diagnostic

private const val UNTYPED_IMPORT = "untyped-import"

private val SUPPRESSED_DIAGNOSTIC_CODES = setOf(UNTYPED_IMPORT)

private val PYREFLY_UNRESOLVED_REFERENCE_CODES = PyLspUnresolvedReferenceCodes(
  unknownName = setOf("unknown-name"),
  missingImport = setOf("missing-import"),
  missingModuleAttribute = setOf("missing-module-attribute"),
  missingSource = setOf("missing-source-for-stubs", "missing-source"),
)

internal fun isSuppressedPyreflyDiagnostic(diagnostic: Diagnostic): Boolean =
  diagnostic.code?.left in SUPPRESSED_DIAGNOSTIC_CODES

internal fun customizePyreflyQuickFixes(
  holder: AnnotationHolder,
  diagnostic: Diagnostic,
  textRange: TextRange,
  quickFixes: List<IntentionAction>,
): List<IntentionAction> =
  pyLspUnresolvedReferenceQuickFixes(holder.currentAnnotationSession.file, diagnostic, textRange, PYREFLY_UNRESOLVED_REFERENCE_CODES) +
  quickFixes
