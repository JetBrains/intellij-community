/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassNames.Attrs
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.parseStdDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.stubs.PyDataclassFieldStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyDataclassFieldStub
import com.jetbrains.python.psi.types.*
import one.util.streamex.StreamEx

class PyDataclassTypeProvider : PyTypeProviderBase() {

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getDataclassesReplaceType(referenceExpression, context)
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val result = when {
      referenceTarget is PyClass && anchor is PyCallExpression -> getDataclassTypeForClass(referenceTarget, context)
      referenceTarget is PyParameter && referenceTarget.isSelf && anchor is PyCallExpression -> {
        PsiTreeUtil.getParentOfType(referenceTarget, PyFunction::class.java)
          ?.takeIf { it.modifier == PyFunction.Modifier.CLASSMETHOD }
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
      val cls = func.containingClass
      val name = param.name

      if (cls != null && name != null && parseStdDataclassParameters(cls, context)?.init == true) {
        cls
          .findClassAttribute(name, false, context) // `true` is not used here because ancestor should be a dataclass
          ?.let { return Ref.create(getTypeForParameter(cls, it, PyDataclassParameters.PredefinedType.STD, context)) }

        for (ancestor in cls.getAncestorClasses(context)) {
          if (parseStdDataclassParameters(ancestor, context) != null) {
            ancestor
              .findClassAttribute(name, false, context)
              ?.let { return Ref.create(getTypeForParameter(ancestor, it, PyDataclassParameters.PredefinedType.STD, context)) }
          }
        }
      }
    }

    return null
  }

  companion object {

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
          if (PyKnownDecoratorUtil.hasUnknownDecorator(current, context)) break else continue
        }
        else if (parameters.type.asPredefinedType == null) {
          break
        }

        seenInit = seenInit || parameters.init
        seenKeywordOnlyClass = seenKeywordOnlyClass || parameters.kwOnly

        if (seenInit) {
          current
            .classAttributes
            .asReversed()
            .asSequence()
            .filterNot { PyTypingTypeProvider.isClassVar(it, context) }
            .mapNotNull { fieldToParameter(current, it, parameters.type, ellipsis, context) }
            .filterNot { it.first in seenNames }
            .forEach { (name, kwOnly, parameter) ->
              // note: attributes are visited from inheritors to ancestors, in reversed order for every of them

              if ((seenKeywordOnlyClass || kwOnly) && name !in collected) {
                keywordOnly += name
              }

              if (parameter == null) {
                seenNames.add(name)
              }
              else if (parameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD) {
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

      return if (seenInit) PyCallableTypeImpl(buildParameters(elementGenerator, collected, keywordOnly), clsType.toInstance()) else null
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

    private fun fieldToParameter(cls: PyClass,
                                 field: PyTargetExpression,
                                 dataclassType: PyDataclassParameters.Type,
                                 ellipsis: PyNoneLiteralExpression,
                                 context: TypeEvalContext): Triple<String, Boolean, PyCallableParameter?>? {
      val fieldName = field.name ?: return null

      val stub = field.stub
      val fieldStub = if (stub == null) PyDataclassFieldStubImpl.create(field) else stub.getCustomStub(PyDataclassFieldStub::class.java)
      if (fieldStub != null && !fieldStub.initValue()) return Triple(fieldName, false, null)
      if (fieldStub == null && field.annotationValue == null) return null // skip fields that are not annotated

      val parameterName =
        fieldName.let {
          if (dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS && PyUtil.getInitialUnderscores(it) == 1) {
            it.substring(1)
          }
          else it
        }

      val parameter = PyCallableParameterImpl.nonPsi(
        parameterName,
        getTypeForParameter(cls, field, dataclassType, context),
        getDefaultValueForParameter(cls, field, fieldStub, dataclassType, ellipsis, context),
        field
      )

      return Triple(parameterName, fieldStub?.kwOnly() == true, parameter)
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

      if (type == null && dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
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
               dataclassType.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS &&
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
