// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.getLiteralType

class PyTupleExpressionImpl(astNode: ASTNode) : PySequenceExpressionImpl(astNode), PyTupleExpression {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyTupleExpression(this)
  }

  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    val inferLiteralTypes = PyLiteralType.inferLiteralTypeForLiteralExpressions()
    val elementTypes = buildList {
      for (element in elements) {
        val starOperand = (element as? PyStarExpression)?.expression
        if (starOperand != null) {
          // Splice a statically known (heterogeneous) tuple operand into the surrounding tuple:
          // `(1, *a)` with `a: tuple[int, int]` has type `tuple[int, int, int]`.
          val operandType = context.getType(starOperand)
          if (operandType is PyTupleType && !operandType.isHomogeneous) {
            addAll(operandType.elementTypes)
            continue
          }
        }
        add((if (inferLiteralTypes) element.getLiteralType(context) else null) ?: context.getType(element))
      }
    }
    return PyTupleType.create(this, elementTypes)
  }

  override fun deleteChildInternal(child: ASTNode) {
    super.deleteChildInternal(child)
    val children = elements
    val generator = PyElementGenerator.getInstance(project)
    if (children.size == 1 && PyPsiUtils.getNextComma(children.single()) == null) {
      addAfter(generator.createComma().psi, children.single())
    }
    else if (children.isEmpty() && parent !is PyParenthesizedExpression) {
      replace(generator.createExpressionFromText(LanguageLevel.forElement(this), "()"))
    }
  }
}
