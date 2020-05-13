// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.MAPPING_GET
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyOverridingTypeProvider
import com.jetbrains.python.psi.types.*

class PyTypedDictOverridingTypeProvider : PyTypeProviderBase(), PyOverridingTypeProvider {

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val typedDictGetType = getTypedDictGetType(referenceTarget, context, anchor)
    if (typedDictGetType != null) {
      return Ref.create(typedDictGetType)
    }

    val type = PyTypedDictTypeProvider.getTypedDictTypeForResolvedCallee(referenceTarget, context)
    return PyTypeUtil.notNullToRef(type)
  }

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getTypedDictGetType(referenceExpression, context, null)
  }

  private fun getTypedDictGetType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): PyCallableType? {
    val callExpression = if (anchor == null && context.maySwitchToAST(referenceTarget) && referenceTarget.parent is PyCallExpression)
      referenceTarget.parent
    else anchor
    if (callExpression !is PyCallExpression || callExpression.callee == null) return null
    val receiver = callExpression.getReceiver(null) ?: return null
    val type = context.getType(receiver)
    if (type !is PyTypedDictType) return null

    if (PyTypingTypeProvider.resolveToQualifiedNames(callExpression.callee!!, context).contains(MAPPING_GET)) {
      val parameters = mutableListOf<PyCallableParameter>()
      val builtinCache = PyBuiltinCache.getInstance(referenceTarget)
      val elementGenerator = PyElementGenerator.getInstance(referenceTarget.project)
      parameters.add(PyCallableParameterImpl.nonPsi("key", builtinCache.strType))
      parameters.add(PyCallableParameterImpl.nonPsi("default", null,
                                                    elementGenerator.createExpressionFromText(LanguageLevel.forElement(referenceTarget),
                                                                                              "None")))
      val key = PyEvaluator.evaluate(callExpression.getArgument(0, "key", PyExpression::class.java), String::class.java)
      val defaultArgument = callExpression.getArgument(1, "default", PyExpression::class.java)
      val default = if (defaultArgument != null) context.getType(defaultArgument) else PyNoneType.INSTANCE
      val valueTypeAndTotality = type.fields[key]
      return PyCallableTypeImpl(parameters,
                                when {
                                  valueTypeAndTotality == null -> default
                                  valueTypeAndTotality.isRequired -> valueTypeAndTotality.type
                                  else -> PyUnionType.union(valueTypeAndTotality.type, default)
                                })
    }

    return null
  }
}