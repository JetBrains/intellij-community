/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.tailOrEmpty
import com.jetbrains.python.PyNames
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.*
import com.jetbrains.python.codeInsight.PyDataclassNames.Attrs
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus

class PyDataclassTypeProvider : PyTypeProviderBase() {

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getDataclassesReplaceType(referenceExpression, context)
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val result = when {
      referenceTarget is PyClass && anchor is PyCallExpression -> getDataclassTypeForClass(referenceTarget, context)
      referenceTarget is PyParameter && referenceTarget.isSelf && anchor is PyCallExpression -> {
        PsiTreeUtil.getParentOfType(referenceTarget, PyFunction::class.java)
          ?.takeIf { it.modifier == PyAstFunction.Modifier.CLASSMETHOD }
          ?.containingClass
          ?.let { getDataclassTypeForClass(it, context) }
      }
      else -> null
    }

    return PyTypeUtil.notNullToRef(result)
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (func.name != Dataclasses.DUNDER_POST_INIT) return null
    if (!param.isPositionalContainer && !param.isKeywordContainer && param.annotationValue == null) {
      val parameters = func.getParameters(context).tailOrEmpty()
      val parameterIndex = parameters.indexOfFirst { it.parameter == param }
      if (parameterIndex == -1) return null

      val cls = func.containingClass ?: return null
      val initVars = getInitVars(cls, parseStdDataclassParameters(cls, context), context) ?: return null
      return initVars
        .drop(parameterIndex)
        .map { Ref.create(it.type) }
        .firstOrNull()
    }

    return null
  }

  companion object {
    @ApiStatus.Internal
    fun getInitVars(
      cls: PyClass,
      dataclassParams: PyDataclassParameters?,
      context: TypeEvalContext,
    ): Sequence<InitVarInfo>? {
      if (dataclassParams == null || !dataclassParams.init) {
        return null
      }
      return cls.getAncestorClasses(context)
        .asReversed()
        .asSequence()
        .filter { parseDataclassParameters(it, context) != null }
        .plus(cls)
        .flatMap { it.classAttributes }
        .mapNotNull {
          val type = context.getType(it)
          if (type is PyCollectionType && type.classQName == Dataclasses.DATACLASSES_INITVAR) {
            InitVarInfo(it, type.elementTypes.singleOrNull())
          }
          else {
            null
          }
        }
    }

    @ApiStatus.Internal
    class InitVarInfo(val targetExpression: PyTargetExpression, val type: PyType?)

    fun getGeneratedMatchArgs(cls: PyClass, context: TypeEvalContext): List<String>? {
      if (parseDataclassParameters(cls, context)?.matchArgs != true) return null
      return getDataclassTypeForClass(cls, context)?.getParameters(context)?.mapNotNull { it.name }
    }

    private fun getDataclassesReplaceType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
      val call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) ?: return null
      val callee = call.callee as? PyReferenceExpression ?: return null

      val resolveContext = PyResolveContext.defaultContext(context)
      val resolvedCallee = PyUtil.multiResolveTopPriority(callee.getReference(resolveContext)).singleOrNull()

      return if (resolvedCallee is PyCallable) getDataclassesReplaceType(resolvedCallee, call, context) else null
    }

    private fun getDataclassesReplaceType(resolvedCallee: PyCallable, call: PyCallExpression, context: TypeEvalContext): PyCallableType? {
      val instanceName = when (resolvedCallee.qualifiedName) {
        Dataclasses.DATACLASSES_REPLACE -> "obj"
        in Attrs.ATTRS_ASSOC, in Attrs.ATTRS_EVOLVE -> "inst"
        else -> return null
      }

      val obj = call.getArgument(0, instanceName, PyTypedElement::class.java) ?: return null
      val objType = context.getType(obj) as? PyClassType ?: return null
      if (objType.isDefinition) return null

      val dataclassType = getDataclassTypeForClass(objType.pyClass, context) ?: return null
      val dataclassParameters = dataclassType.getParameters(context) ?: return null

      val parameters = mutableListOf<PyCallableParameter>()
      val elementGenerator = PyElementGenerator.getInstance(resolvedCallee.project)

      parameters.add(PyCallableParameterImpl.nonPsi(instanceName, objType))
      parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()))

      val ellipsis = elementGenerator.createEllipsis()
      dataclassParameters.mapTo(parameters) { PyCallableParameterImpl.nonPsi(it.name, it.getType(context), ellipsis) }

      return PyCallableTypeImpl(parameters, dataclassType.getReturnType(context))
    }

    private fun getDataclassTypeForClass(cls: PyClass, context: TypeEvalContext): PyCallableType? {
      val clsType = (context.getType(cls) as? PyClassLikeType) ?: return null

      val resolveContext = PyResolveContext.defaultContext(context)
      val elementGenerator = PyElementGenerator.getInstance(cls.project)
      val ellipsis = elementGenerator.createEllipsis()

      val collected = linkedMapOf<String, PyCallableParameter>()
      var seenInit = false
      val keywordOnly = linkedSetOf<String>()
      var seenKeywordOnlyClass = false
      val seenNames = mutableSetOf<String>()

      for (currentType in StreamEx.of(clsType).append(cls.getAncestorTypes(context))) {
        if (currentType == null ||
            !currentType.resolveMember(PyNames.INIT, null, AccessDirection.READ, resolveContext, false).isNullOrEmpty() ||
            !currentType.resolveMember(PyNames.NEW, null, AccessDirection.READ, resolveContext, false).isNullOrEmpty() ||
            currentType !is PyClassType) {
          if (seenInit) continue else break
        }

        val current = currentType.pyClass
        val parameters = parseDataclassParameters(current, context)

        if (parameters == null) {
          // The base class decorated with @dataclass_transform gets filtered out already here, because for it we don't detect DataclassParameters
          if (PyKnownDecoratorUtil.hasUnknownDecorator(current, context)) break else continue
        }
        else if (parameters.type.asPredefinedType == null) {
          break
        }

        seenInit = seenInit || parameters.init
        seenKeywordOnlyClass = seenKeywordOnlyClass || parameters.kwOnly

        if (seenInit) {
          val fieldsInfo = current
            .classAttributes
            .asReversed()
            .asSequence()
            .filterNot { PyTypingTypeProvider.isClassVar(it, context) }
            .mapNotNull { fieldToParameter(current, it, parameters, ellipsis, context) }
            .filterNot { it.first in seenNames }
            .toList()

          val indexOfKeywordOnlyAttribute = fieldsInfo.indexOfLast { (_, _, parameter) ->
            parameter != null && isKwOnlyMarkerField(parameter, context)
          }

          fieldsInfo.forEachIndexed { index, (name, kwOnly, parameter) ->
            // note: attributes are visited from inheritors to ancestors, in reversed order for every of them

            if ((seenKeywordOnlyClass && (parameters.type == PyDataclassParameters.PredefinedType.ATTRS || kwOnly != false) 
                 || index < indexOfKeywordOnlyAttribute || kwOnly == true) 
                && name !in collected) {
              keywordOnly += name
            }

            if (parameter == null) {
              seenNames.add(name)
            }
            else if (!isKwOnlyMarkerField(parameter, context)) {
              if (parameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD ||
                  parameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM) {
                // std: attribute that overrides ancestor's attribute does not change the order but updates type
                collected[name] = collected.remove(name) ?: parameter
              }
              else if (!collected.containsKey(name)) {
                // attrs: attribute that overrides ancestor's attribute changes the order
                collected[name] = parameter
              }
            }
          }
        }
      }
      return if (seenInit) PyCallableTypeImpl(buildParameters(elementGenerator, collected, keywordOnly), clsType.toInstance()) else null
    }

    private fun isKwOnlyMarkerField(parameter: PyCallableParameter, context: TypeEvalContext): Boolean {
      val psi = parameter.declarationElement
      if (psi !is PyTargetExpression) return false
      val typeHint = PyTypingTypeProvider.getAnnotationValue(psi, context) as? PyReferenceExpression ?: return false
      val type = Ref.deref(PyTypingTypeProvider.getType(typeHint, context))
      return type is PyClassType && type.classQName == Dataclasses.DATACLASSES_KW_ONLY
    }

    private fun buildParameters(elementGenerator: PyElementGenerator,
                                fields: Map<String, PyCallableParameter>,
                                keywordOnly: Set<String>): List<PyCallableParameter> {
      if (keywordOnly.isEmpty()) return fields.values.reversed()

      val positionalOrKeyword = mutableListOf<PyCallableParameter>()
      val keyword = mutableListOf<PyCallableParameter>()

      for ((name, value) in fields.entries.reversed()) {
        if (name !in keywordOnly) {
          positionalOrKeyword += value
        }
        else {
          keyword += value
        }
      }

      val singleStarParameter = elementGenerator.createSingleStarParameter()
      return positionalOrKeyword + listOf(PyCallableParameterImpl.psi(singleStarParameter)) + keyword
    }

    private fun fieldToParameter(
      cls: PyClass,
      field: PyTargetExpression,
      dataclassParameters: PyDataclassParameters,
      ellipsis: PyNoneLiteralExpression,
      context: TypeEvalContext
    ): Triple<String, Boolean?, PyCallableParameter?>? {
      val fieldName = field.name ?: return null

      val fieldParams = resolveDataclassFieldParameters(cls, dataclassParameters, field, context)
      if (fieldParams != null && !fieldParams.initValue) return Triple(fieldName, false, null)
      if (fieldParams == null && field.annotationValue == null) return null // skip fields that are not annotated

      val parameterName = when (dataclassParameters.type.asPredefinedType) {
        // Fields starting with more than one underscore will be mangled into ClassName__field_name, but we don't support that
        PyDataclassParameters.PredefinedType.ATTRS -> fieldParams?.alias ?: fieldName.removePrefix("_")
        PyDataclassParameters.PredefinedType.DATACLASS_TRANSFORM -> fieldParams?.alias ?: fieldName
        PyDataclassParameters.PredefinedType.STD -> fieldName
        else -> fieldName
      }

      val parameter = PyCallableParameterImpl.nonPsi(
        parameterName,
        getTypeForParameter(cls, field, dataclassParameters.type, context),
        getDefaultValueForParameter(cls, field, fieldParams, dataclassParameters, ellipsis, context),
        field
      )

      return Triple(parameterName, fieldParams?.kwOnly, parameter)
    }

    private fun getTypeForParameter(cls: PyClass,
                                    field: PyTargetExpression,
                                    dataclassType: PyDataclassParameters.Type,
                                    context: TypeEvalContext): PyType? {
      if (dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS && context.maySwitchToAST(field)) {
        (field.findAssignedValue() as? PyCallExpression)
          ?.getKeywordArgument("type")
          ?.let { PyTypingTypeProvider.getType(it, context) }
          ?.apply { return get() }
      }

      val type = context.getType(field)
      if (type is PyCollectionType && type.classQName == Dataclasses.DATACLASSES_INITVAR) {
        return type.elementTypes.firstOrNull()
      }
      if (type is PyClassLikeType) {
        val expectedConstructorArgumentTypeRef = PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(field, type, context)
        if (expectedConstructorArgumentTypeRef != null) {
          return Ref.deref(expectedConstructorArgumentTypeRef)
        }
      }

      if (type == null && dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
        methodDecoratedAsAttributeDefault(cls, field.name)
          ?.let { context.getReturnType(it) }
          ?.let { return PyUnionType.createWeakType(it) }
      }

      return type
    }

    private fun getDefaultValueForParameter(
      cls: PyClass,
      field: PyTargetExpression,
      fieldParams: PyDataclassFieldParameters?,
      dataclassParams: PyDataclassParameters,
      ellipsis: PyNoneLiteralExpression,
      context: TypeEvalContext
    ): PyExpression? {
      return if (fieldParams == null) {
        when {
          context.maySwitchToAST(field) -> field.findAssignedValue()
          field.hasAssignedValue() -> ellipsis
          else -> null
        }
      }
      else if (fieldParams.hasDefault ||
               fieldParams.hasDefaultFactory ||
               dataclassParams.type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS &&
               methodDecoratedAsAttributeDefault(cls, field.name) != null) {
        ellipsis
      }
      else null
    }

    private fun methodDecoratedAsAttributeDefault(cls: PyClass, attributeName: String?): PyFunction? {
      if (attributeName == null) return null
      return cls.methods.firstOrNull { it.decoratorList?.findDecorator("$attributeName.default") != null }
    }
  }
}
