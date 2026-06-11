package com.jetbrains.python.documentation.doctest

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.documentation.docstrings.DocStringUtil
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PythonVisitorFilter

/**
 * Disables all inspections for plain Python code blocks injected inside docstrings
 * Doctest fragments ([PyDocstringLanguageDialect]) are handled separately by `PyDocstringVisitorFilter`.
 */
internal class PyDocstringCodeBlockVisitorFilter : PythonVisitorFilter {
  override fun isSupported(visitorClass: Class<out PyElementVisitor?>, file: PsiFile): Boolean {
    if (file.language.`is`(PythonLanguage.getInstance())) {
      val languageManager = InjectedLanguageManager.getInstance(file.project) ?: return true
      val host = languageManager.getInjectionHost(file) ?: return true
      if (host is PyStringLiteralExpression && DocStringUtil.getParentDefinitionDocString(host) == host) {
        return false
      }
    }
    return true
  }
}