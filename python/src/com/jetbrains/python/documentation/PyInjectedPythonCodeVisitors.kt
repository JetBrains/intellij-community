package com.jetbrains.python.documentation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyByteLiteralInspection
import com.jetbrains.python.inspections.PyClassHasNoInitInspection
import com.jetbrains.python.inspections.PyDocstringTypesInspection
import com.jetbrains.python.inspections.PyIncorrectDocstringInspection
import com.jetbrains.python.inspections.PyMandatoryEncodingInspection
import com.jetbrains.python.inspections.PyMissingOrEmptyDocstringInspection
import com.jetbrains.python.inspections.PyNonAsciiCharInspection
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.inspections.PyPep8Inspection
import com.jetbrains.python.inspections.PySingleQuotedDocstringInspection
import com.jetbrains.python.inspections.PyStatementEffectInspection
import com.jetbrains.python.inspections.PyUnboundLocalVariableInspection
import com.jetbrains.python.inspections.PyUnnecessaryBackslashInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.validation.PyDocStringHighlightingAnnotator
import com.jetbrains.python.validation.PyFunctionHighlightingAnnotator
import com.jetbrains.python.validation.PyParameterListAnnotatorVisitor
import com.jetbrains.python.validation.PyReturnYieldAnnotatorVisitor

/**
 * Holds the visitor sets that PyDocstringCodeBlockVisitorFilter and PyDoctestVisitorFilter both disable
 * for Python code injected into a docstring code block or a doctest fragment.
 */
object PyInjectedPythonCodeVisitors {
  @JvmField
  val disabledAnnotators: Set<Class<*>> = setOf(
    PyDocStringHighlightingAnnotator::class.java,
    PyParameterListAnnotatorVisitor::class.java,
    PyReturnYieldAnnotatorVisitor::class.java,
    PyFunctionHighlightingAnnotator::class.java,
  )

  @JvmField
  val disabledInspections: Set<Class<*>> = setOf(
    PyPackageRequirementsInspection::class.java,
    PyPep8Inspection::class.java,
    PyArgumentListInspection::class.java,
    PyIncorrectDocstringInspection::class.java,
    PyMissingOrEmptyDocstringInspection::class.java,
    PyUnboundLocalVariableInspection::class.java,
    PyUnnecessaryBackslashInspection::class.java,
    PyByteLiteralInspection::class.java,
    PyNonAsciiCharInspection::class.java,
    PyMandatoryEncodingInspection::class.java,
    PyDocstringTypesInspection::class.java,
    PySingleQuotedDocstringInspection::class.java,
    PyClassHasNoInitInspection::class.java,
    PyStatementEffectInspection::class.java,
  )

  /**
   * True when [visitorClass] is the unresolved-reference inspection and the injected code
   * does not live in a real Python file. The inspection needs a real file for its reference index.
   */
  @JvmStatic
  fun isUnresolvedReferenceOutsideTopLevelFile(visitorClass: Class<*>, file: PsiFile): Boolean {
    if (visitorClass != PyUnresolvedReferencesInspection::class.java) return false
    val topLevelFile = InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
    return topLevelFile !is PyFile
  }
}
