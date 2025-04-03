package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.PyWithStatement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class PyWithContextExitInstruction(builder: ControlFlowBuilder, withItem: PyWithItem): InstructionImpl(builder, withItem) {
  override fun getElementPresentation(): String = "exit context manager: ${element.text}"
  override fun getElement(): PyWithItem = super.getElement() as PyWithItem

  /**
   * While traversing CFG, use this method to know if you should let your traversal consider this node.
   * Usually, you would want it only if the context manager DOES suppress exceptions.
   */
  fun isSuppressingExceptions(context: TypeEvalContext): Boolean {
    val withStmt = PsiTreeUtil.getParentOfType(element, PyWithStatement::class.java, false) ?: return false
    val abstractType = if (withStmt.isAsync) "contextlib.AbstractAsyncContextManager" else "contextlib.AbstractContextManager"
    return context.getType(element.expression)
      .let { PyTypeUtil.convertToType(it, abstractType, element, context) }
      .let { (it as? PyCollectionType)?.elementTypes?.getOrNull(1) }
      .let { it == PyBuiltinCache.getInstance(element).boolType ||
             it is PyLiteralType && PyEvaluator.getBooleanLiteralValue(it.expression) == true }
  }
}