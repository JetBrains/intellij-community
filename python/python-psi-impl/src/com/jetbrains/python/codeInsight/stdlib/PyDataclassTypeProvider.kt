/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.containers.tailOrEmpty
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.getDataclassInitVars
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDataclassTypeProvider : PyTypeProviderBase() {

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getDataclassesReplaceType(referenceExpression, context)
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    // MyDataclass() call
    val anchor = anchor?.let(PyCallExpressionNavigator::getPyCallExpressionByCallee)
    if (referenceTarget is PyClass && anchor is PyCallExpression) {
      return generateDataclassConstructorType(context.getType(referenceTarget), context).notNullToRef()
    }

    return null
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (param.isPositionalContainer || param.isKeywordContainer || param.annotationValue != null) return null

    val cls = func.containingClass ?: return null
    val dataclassParameters = parseDataclassParameters(cls, context) ?: return null
    val resolver = dataclassParameters.type.resolver
    if (func.name != resolver?.postInitFunctionName()) return null

    val parameters = func.getParameters(context).tailOrEmpty()
    val parameterIndex = parameters.indexOfFirst { it.parameter == param }
    if (parameterIndex == -1) return null

    val initVars = getDataclassInitVars(cls, dataclassParameters, context) ?: return null
    return initVars
      .drop(parameterIndex)
      .map { Ref.create(it.type) }
      .firstOrNull()
  }

  override fun prepareCalleeTypeForCall(type: PyType?, callee: PyExpression, context: TypeEvalContext): Ref<PyCallableType?>? {
    for (t in type.toStream()) {
      if (t !is PyClassType) {
        continue
      }
      if (!t.isDefinition) {
        continue
      }
      val dataclassType = generateDataclassConstructorType(t, context) as? PyCallableType
      if (dataclassType != null) {
        return Ref.create(dataclassType)
      }
    }
    return null
  }

  override fun getMemberTypes(type: PyType, name: String, location: PyExpression?, direction: AccessDirection, context: PyResolveContext): List<PyTypeMember>? {
    if (type !is PyClassType) {
      return null
    }
    val dataclassParameters = parseDataclassParameters(type.pyClass, context.typeEvalContext) ?: return null
    val resolver = dataclassParameters.type.resolver
    if (PyNames.HASH == name) {
      if (resolver == null) {
        return null
      }

      // See `unsafe_hash` section here https://docs.python.org/3/library/dataclasses.html
      if (dataclassParameters.unsafeHash) {
        return null
      }

      if (!dataclassParameters.eq) {
        return null
      }

      if (dataclassParameters.frozen == true) {
        return null
      }

      val resolvedMembers = type.resolveMember(name, location, direction, context, false)
      if (resolvedMembers?.isNotEmpty() == true) {
        return null
      }
      return listOf(PyTypeMember(null, PyBuiltinCache.getInstance(type.pyClass).noneType))
    }
    else {
      if (resolver != null && dataclassParameters.frozen == true) {
        val resolvedMembers = type.resolveMember(name, location, direction, context, false)
        if (resolvedMembers?.isNotEmpty() == true) {
          return resolvedMembers.map {
            val element = it.element
            val type = if (element is PyTypedElement) {
              context.typeEvalContext.getType(element)
            }
            else {
              null
            }
            PyTypeMember(element, type, false, element, null, null)
          }
        }
      }
    }

    return null
  }

  private fun generateDataclassConstructorType(clsType: PyType?, context: TypeEvalContext): PyType? {
    if (clsType !is PyClassType) return null
    val genericClassType = clsType.takeIf { it.isParameterized }
                           ?: PyTypeChecker.findGenericDefinitionType(clsType.pyClass, context)
                           ?: clsType

    val acc = collectDataclassInitFields(genericClassType, context, initOnly = true) ?: return null
    val controlling = acc.controllingParameters ?: return null
    val dataclassResolver = controlling.type.resolver
    val paramsSets = dataclassResolver?.buildInitSignatureParameterSets(acc) ?: return null
    if (paramsSets.isEmpty()) return null

    return PyUnsafeUnionType.unsafeUnion(
      paramsSets.map {PyCallableTypeImpl(it, genericClassType.toInstance())}
    )
  }

  private fun getDataclassesReplaceType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
    val call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) ?: return null
    val callee = call.callee as? PyReferenceExpression ?: return null

    val resolveContext = PyResolveContext.defaultContext(context)
    val resolvedCallee = PyUtil.multiResolveTopPriority(callee.getReference(resolveContext)).singleOrNull()

    return if (resolvedCallee is PyCallable) getDataclassesReplaceType(resolvedCallee, call, context) else null
  }

  private fun getDataclassesReplaceType(resolvedCallee: PyCallable, call: PyCallExpression, context: TypeEvalContext): PyCallableType? {
    val qualifiedName = resolvedCallee.qualifiedName ?: return null
    val instanceName = allRegisteredDataclassResolvers
                         .flatMap { it.copyFunctions() }
                         .firstOrNull { it.qualifiedName.toString() == qualifiedName }
                         ?.instanceParameterName ?: return null

    val obj = call.getArgument(0, instanceName, PyTypedElement::class.java) ?: return null
    val objType = context.getType(obj) as? PyClassType ?: return null
    if (objType.isDefinition) return null

    val dataclassType = generateDataclassConstructorType(objType, context) as? PyCallableType ?: return null
    val dataclassParameters = dataclassType.getParameters(context) ?: return null

    val parameters = mutableListOf<PyCallableParameter>()
    val elementGenerator = PyElementGenerator.getInstance(resolvedCallee.project)

    parameters.add(PyCallableParameterImpl.nonPsi(instanceName, objType))
    parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()))

    val ellipsis = elementGenerator.createEllipsis()
    dataclassParameters.mapTo(parameters) { PyCallableParameterImpl.nonPsi(it.name, it.getType(context), ellipsis) }

    return PyCallableTypeImpl(parameters, dataclassType.getReturnType(context))
  }
}
