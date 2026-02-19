package com.jetbrains.python.validation

import com.intellij.lang.annotation.HighlightSeverity
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ast.PyAstTypeParameter.Kind.TypeVar
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypeParameterList
import com.jetbrains.python.psi.impl.PyPsiUtils

class PyTypeParameterListAnnotatorVisitor(private val holder: PyAnnotationHolder) : PyElementVisitor() {
  override fun visitPyTypeParameterList(node: PyTypeParameterList) {
    if (!node.typeParameters.isEmpty()) {
      val namesSet = mutableSetOf<String>()

      for (typeParameter in node.typeParameters) {
        val name = typeParameter.name
        val identifier = typeParameter.nameIdentifier
        if (name != null && identifier != null)
          if (!namesSet.add(name)) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                                 PyPsiBundle.message("type.param.list.annotator.type.parameter.already.defined", name))
              .range(typeParameter.nameIdentifier!!.textRange).create()
          }
      }
    }
  }

  override fun visitPyTypeParameter(node: PyTypeParameter) {
    val boundExpression = PyPsiUtils.flattenParens(node.boundExpression)
    if (node.kind == TypeVar) {

      if (boundExpression is PyTupleExpression) {
        if (boundExpression.elements.size < 2) {
          holder.newAnnotation(HighlightSeverity.ERROR,
                               PyPsiBundle.message("type.param.list.annotator.two.or.more.types.required"))
            .range(boundExpression.textRange).create()
        }
      }
    }
    else if (boundExpression != null) {
      holder.newAnnotation(HighlightSeverity.ERROR,
                           PyPsiBundle.message("type.param.list.annotator.type.var.tuple.and.param.spec.can.not.have.bounds"))
        .range(boundExpression.textRange).create()
    }
  }
}