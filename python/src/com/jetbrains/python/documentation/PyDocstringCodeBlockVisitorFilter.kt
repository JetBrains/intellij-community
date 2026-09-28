package com.jetbrains.python.documentation

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import com.jetbrains.python.documentation.docstrings.DocStringUtil
import com.jetbrains.python.documentation.doctest.PyDocstringCodeBlockLanguageDialect
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PythonVisitorFilter
import com.jetbrains.python.validation.PySyntaxAnnotator
import com.jetbrains.python.validation.UnsupportedFeatures

/**
 * Controls which visitors run for a plain Python code block injected into a docstring.
 *
 * An inspection runs only when [PyDocumentationSettings.isInspectDocstring] is on.
 * Syntax and unsupported-feature annotators are always disabled for these code blocks.
 * Doctest fragments ([com.jetbrains.python.documentation.doctest.PyDoctestLanguageDialect]) are handled separately by `PyDoctestVisitorFilter`.
 */
internal class PyDocstringCodeBlockVisitorFilter : PythonVisitorFilter {
  private val disabledAnnotators: Set<Class<*>> = PyInjectedPythonCodeVisitors.disabledAnnotators + setOf(
    PySyntaxAnnotator::class.java,
    UnsupportedFeatures::class.java,
  )

  override fun isSupported(visitorClass: Class<out PyElementVisitor?>, file: PsiFile): Boolean {
    if (visitorClass in disabledAnnotators || visitorClass in PyInjectedPythonCodeVisitors.disabledInspections) return false
    if (PyInjectedPythonCodeVisitors.isUnresolvedReferenceOutsideTopLevelFile(visitorClass, file)) return false

    val isInspection = LocalInspectionTool::class.java.isAssignableFrom(visitorClass)
    if (!isInspection) return true

    val docstring = getDocstringByHost(file) ?: return true
    return inspectsDocstringEnabled(docstring)
  }

  private fun getDocstringByHost(file: PsiFile): PyStringLiteralExpression? {
    if (!file.language.`is`(PyDocstringCodeBlockLanguageDialect.getInstance())) return null
    return DocStringUtil.getDocstringInjectionHost(file)
  }

  private fun inspectsDocstringEnabled(docstring: PyStringLiteralExpression): Boolean {
    val module = ModuleUtilCore.findModuleForPsiElement(docstring)
    return PyDocumentationSettings.getInstance(module).isInspectDocstring
  }
}