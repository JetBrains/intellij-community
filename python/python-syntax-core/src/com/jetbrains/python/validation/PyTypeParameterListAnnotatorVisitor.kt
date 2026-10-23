// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation

import com.intellij.lang.annotation.HighlightSeverity
import com.jetbrains.python.PySyntaxCoreBundle
import com.jetbrains.python.ast.PyAstTypeParameter
import com.jetbrains.python.ast.PyAstTypeParameter.Kind.TypeVar
import com.jetbrains.python.ast.PyAstElementVisitor
import com.jetbrains.python.ast.PyAstTupleExpression
import com.jetbrains.python.ast.PyAstTypeParameterList
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyTypeParameterListAnnotatorVisitor(private val holder: PyAnnotationHolder) : PyAstElementVisitor() {
  override fun visitPyTypeParameterList(node: PyAstTypeParameterList) {
    if (!node.typeParameters.isEmpty()) {
      val namesSet = mutableSetOf<String>()

      for (typeParameter in node.typeParameters) {
        val name = typeParameter.name
        val identifier = typeParameter.nameIdentifier
        if (name != null && identifier != null)
          if (!namesSet.add(name)) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                                 PySyntaxCoreBundle.message("type.param.list.annotator.type.parameter.already.defined", name))
              .range(typeParameter.nameIdentifier!!.textRange).create()
          }
      }
    }
  }

  override fun visitPyTypeParameter(node: PyAstTypeParameter) {
    val boundExpression = PyPsiUtilsCore.flattenParens(node.boundExpression)
    if (node.kind == TypeVar) {

      if (boundExpression is PyAstTupleExpression<*>) {
        if (boundExpression.elements.size < 2) {
          holder.newAnnotation(HighlightSeverity.ERROR,
                               PySyntaxCoreBundle.message("type.param.list.annotator.two.or.more.types.required"))
            .range(boundExpression.textRange).create()
        }
      }
    }
    else if (boundExpression != null) {
      val name = if (node.kind == PyAstTypeParameter.Kind.ParamSpec) "Parameter specifications" else "Type variable tuples"
      holder.newAnnotation(HighlightSeverity.ERROR,
                           PySyntaxCoreBundle.message("type.param.list.annotator.type.var.tuple.and.param.spec.can.not.have.bounds", name))
        .range(boundExpression.textRange).create()
    }
  }
}