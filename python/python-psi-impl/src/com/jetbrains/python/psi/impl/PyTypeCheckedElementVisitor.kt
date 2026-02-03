package com.jetbrains.python.psi.impl

import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class PyTypeCheckedElementVisitor(private val languageLevel: LanguageLevel?) : PyRecursiveElementVisitor() {
  override fun visitPyIfStatement(node: PyIfStatement) {
    val ifParts = sequenceOf(node.ifPart) + node.elifParts.asSequence()
    for (ifPart in ifParts) {
      val result = PyEvaluator.evaluateAsBooleanNoResolve(ifPart.condition, languageLevel)
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
}