/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.types.*

class PyDataclassTypeProvider : PyTypeProviderBase() {

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getDataclassTypeForCallee(referenceExpression, context) ?: getDataclassesReplaceType(referenceExpression, context)
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (!param.isPositionalContainer && !param.isKeywordContainer && param.annotationValue == null && func.name == DUNDER_POST_INIT) {
      val cls = func.containingClass
      val name = param.name

      if (cls != null && name != null && parseStdDataclassParameters(cls, context)?.init == true) {
        cls
          .findClassAttribute(name, false, context)
          ?.let { return Ref.create(getTypeForParameter(cls, it, PyDataclassParameters.Type.STD, context)) }
      }
    }

    return null
  }

  private fun getDataclassTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
    if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

    val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
    val resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false)

    return PyUtil.filterTopPriorityResults(resolveResults)
      .asSequence()
      .filterIsInstance<PyClass>()
      .map { getDataclassTypeForClass(it, context) }
      .firstOrNull { it != null }
  }

  private fun getDataclassesReplaceType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
    val call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) ?: return null
    val callee = call.callee as? PyReferenceExpression ?: return null

    val resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context)
    val resolvedCallee = PyUtil.multiResolveTopPriority(callee.getReference(resolveContext)).singleOrNull()

    if (resolvedCallee is PyCallable) {
      val instanceName = when (resolvedCallee.qualifiedName) {
        "dataclasses.replace" -> "obj"
        "attr.__init__.assoc", "attr.__init__.evolve" -> "inst"
        else -> return null
      }

      val obj = call.getArgument(0, instanceName, PyTypedElement::class.java) ?: return null
      val objType = context.getType(obj) as? PyClassType ?: return null
      if (objType.isDefinition) return null

      val dataclassType = getDataclassTypeForClass(objType.pyClass, context) ?: return null
      val dataclassParameters = dataclassType.getParameters(context) ?: return null

      val parameters = mutableListOf<PyCallableParameter>()
      val elementGenerator = PyElementGenerator.getInstance(referenceExpression.project)

      parameters.add(PyCallableParameterImpl.nonPsi(instanceName, objType))
      parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()))

      val ellipsis = elementGenerator.createEllipsis()
      dataclassParameters.mapTo(parameters, { PyCallableParameterImpl.nonPsi(it.name, it.getType(context), it.defaultValue ?: ellipsis) })

      return PyCallableTypeImpl(parameters, dataclassType.getReturnType(context))
    }

    return null
  }

  private fun getDataclassTypeForClass(cls: PyClass, context: TypeEvalContext): PyCallableType? {
    val dataclassParameters = parseDataclassParameters(cls, context)
    if (dataclassParameters == null || !dataclassParameters.init) {
      return null
    }

    val ellipsis = PyElementGenerator.getInstance(cls.project).createEllipsis()
    val parameters = cls
      .classAttributes
      .asSequence()
      .filterNot { PyTypingTypeProvider.isClassVar(it, context) }
      .mapNotNull { fieldToParameter(cls, it, dataclassParameters.type, ellipsis, context) }
      .toList()

    return PyCallableTypeImpl(parameters, context.getType(cls))
  }

  private fun fieldToParameter(cls: PyClass,
                               field: PyTargetExpression,
                               dataclassType: PyDataclassParameters.Type,
                               ellipsis: PyNoneLiteralExpression,
                               context: TypeEvalContext): PyCallableParameter? {
    val stub = field.stub
    val fieldStub = if (stub == null) PyDataclassFieldStubImpl.create(field) else stub.getCustomStub(PyDataclassFieldStub::class.java)
    if (fieldStub?.initValue() == false) return null

    val name =
      field.name?.let {
        if (dataclassType == PyDataclassParameters.Type.ATTRS && PyUtil.getInitialUnderscores(it) == 1) it.substring(1) else it
      }

    return PyCallableParameterImpl.nonPsi(name,
                                          getTypeForParameter(cls, field, dataclassType, context),
                                          getDefaultValueForParameter(cls, field, fieldStub, dataclassType, ellipsis, context))
  }

  private fun getTypeForParameter(cls: PyClass,
                                  field: PyTargetExpression,
                                  dataclassType: PyDataclassParameters.Type,
                                  context: TypeEvalContext): PyType? {
    if (dataclassType == PyDataclassParameters.Type.ATTRS && context.maySwitchToAST(field)) {
      (field.findAssignedValue() as? PyCallExpression)
        ?.getKeywordArgument("type")
        ?.let { PyTypingTypeProvider.getType(it, context) }
        ?.apply { return get() }
    }

    val type = context.getType(field)
    if (type is PyCollectionType && type.classQName == DATACLASSES_INITVAR_TYPE) {
      return type.elementTypes.firstOrNull()
    }

    if (type == null && dataclassType == PyDataclassParameters.Type.ATTRS) {
      methodDecoratedAsAttributeDefault(cls, field.name)
        ?.let { context.getReturnType(it) }
        ?.let { return PyUnionType.createWeakType(it) }
    }

    return type
  }

  private fun getDefaultValueForParameter(cls: PyClass,
                                          field: PyTargetExpression,
                                          fieldStub: PyDataclassFieldStub?,
                                          dataclassType: PyDataclassParameters.Type,
                                          ellipsis: PyNoneLiteralExpression,
                                          context: TypeEvalContext): PyExpression? {
    return if (fieldStub == null) {
      when {
        context.maySwitchToAST(field) -> field.findAssignedValue()
        field.hasAssignedValue() -> ellipsis
        else -> null
      }
    }
    else if (fieldStub.hasDefault() ||
             fieldStub.hasDefaultFactory() ||
             dataclassType == PyDataclassParameters.Type.ATTRS && methodDecoratedAsAttributeDefault(cls, field.name) != null) {
      ellipsis
    }
    else null
  }

  private fun methodDecoratedAsAttributeDefault(cls: PyClass, attributeName: String?): PyFunction? {
    if (attributeName == null) return null
    return cls.methods.firstOrNull { it.decoratorList?.findDecorator("$attributeName.default") != null }
  }
}