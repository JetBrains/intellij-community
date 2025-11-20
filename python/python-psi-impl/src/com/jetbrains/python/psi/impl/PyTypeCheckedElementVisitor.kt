package com.jetbrains.python.psi.impl

import com.intellij.openapi.util.Version
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class PyTypeCheckedElementVisitor(languageLevel: LanguageLevel?) : PyRecursiveElementVisitor() {
  private val version = languageLevel?.let { Version(it.majorVersion, it.minorVersion, 0) }

  override fun visitPyIfStatement(node: PyIfStatement) {
    val ifParts = sequenceOf(node.ifPart) + node.elifParts.asSequence()
    for (ifPart in ifParts) {
      val result = evaluate(ifPart.condition)
      if (result == null) {
        super.visitPyIfStatement(node)
        return
      }
      if (result) {
        ifPart.statementList.accept(this)
        return
      }
    }
    node.elsePart?.statementList?.accept(this)
  }

  private fun evaluate(expression: PyExpression?): Boolean? {
    return PyEvaluator.evaluateAsBooleanNoResolve(expression) ?: evaluateVersionCheck(expression)
  }

  private fun evaluateVersionCheck(expression: PyExpression?): Boolean? {
    expression ?: return null
    version ?: return null
    return PyVersionCheck.convertToVersionRanges(expression)?.contains(version)
  }
}