// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.stdlib

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.parseStdDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleTypeProvider
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyNamedTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyStdlibInspectionExtension : PyInspectionExtension() {

  override fun ignoreInitNewSignatures(original: PyFunction, complementary: PyFunction): Boolean {
    val qName = complementary.containingClass?.qualifiedName
    return PyNames.TYPE_ENUM == qName || PyNames.FQN.TYPE == qName
  }

  override fun ignoreUnresolvedMember(type: PyType, name: String, context: TypeEvalContext): Boolean {
    if (type is PyClassLikeType) {
      return type is PyNamedTupleType && ignoredUnresolvedNamedTupleMember(type, name) ||
             type.getAncestorTypes(context).filterIsInstance<PyNamedTupleType>().any { ignoredUnresolvedNamedTupleMember(it, name) }
    }

    return false
  }

  override fun ignoreProtectedSymbol(expression: PyReferenceExpression, context: TypeEvalContext): Boolean {
    val qualifier = expression.qualifier
    return qualifier != null &&
           expression.referencedName in PyNamedTupleType.getSpecialAttributes(LanguageLevel.forElement(expression)) &&
           PyNamedTupleTypeProvider.Helper.isNamedTuple(context.getType(qualifier), context)
  }

  override fun ignoreMethodParameters(function: PyFunction, context: TypeEvalContext): Boolean {
    return when (function.name) {
      "__prepare__" -> {
        val parameters = function.getParameters(context)
        parameters.size == 3 && parameters.none { it.isKeywordContainer || it.isPositionalContainer }
      }
      Dataclasses.DUNDER_POST_INIT -> {
        val cls = function.containingClass
        cls != null && parseStdDataclassParameters(cls, context) != null
      }
      else -> false
    }
  }

  private fun ignoredUnresolvedNamedTupleMember(type: PyNamedTupleType, name: String): Boolean {
    return name == PyNames.SLOTS || type.fields.containsKey(name)
  }
}
