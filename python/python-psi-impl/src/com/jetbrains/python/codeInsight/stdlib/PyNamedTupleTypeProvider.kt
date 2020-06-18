// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.ArrayUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.stubs.PyNamedTupleStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyNamedTupleStub
import com.jetbrains.python.psi.types.*
import one.util.streamex.StreamEx
import java.util.*
import java.util.stream.Collectors

private typealias NTFields = LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue>
private typealias ImmutableNTFields = Map<String, PyNamedTupleType.FieldTypeAndDefaultValue>

class PyNamedTupleTypeProvider : PyTypeProviderBase() {

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    return PyTypeUtil.notNullToRef(getNamedTupleTypeForResolvedCallee(referenceTarget, context, anchor))
  }

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    val fieldTypeForNamedTuple = getFieldTypeForNamedTupleAsTarget(referenceExpression, context)
    if (fieldTypeForNamedTuple != null) {
      return fieldTypeForNamedTuple
    }

    val fieldTypeForTypingNTFunctionInheritor = getFieldTypeForTypingNTFunctionInheritor(referenceExpression, context)
    if (fieldTypeForTypingNTFunctionInheritor != null) {
      return fieldTypeForTypingNTFunctionInheritor
    }

    val namedTupleTypeForCallee = getNamedTupleTypeForCallee(referenceExpression, context)
    if (namedTupleTypeForCallee != null) {
      return namedTupleTypeForCallee
    }

    val namedTupleReplaceType = getNamedTupleReplaceType(referenceExpression, context)
    if (namedTupleReplaceType != null) {
      return namedTupleReplaceType
    }

    return null
  }

  companion object {

    fun isNamedTuple(type: PyType?, context: TypeEvalContext): Boolean {
      if (type is PyNamedTupleType) return true

      val isNT = { t: PyClassLikeType? -> t is PyNamedTupleType || t != null && PyTypingTypeProvider.NAMEDTUPLE == t.classQName }
      return type is PyClassLikeType && type.getAncestorTypes(context).any(isNT)
    }

    fun isTypingNamedTupleDirectInheritor(cls: PyClass, context: TypeEvalContext): Boolean {
      val isTypingNT = { type: PyClassLikeType? ->
        type != null && type !is PyNamedTupleType && PyTypingTypeProvider.NAMEDTUPLE == type.classQName
      }

      return cls.getSuperClassTypes(context).any(isTypingNT)
    }

    internal fun getNamedTupleTypeForResolvedCallee(referenceTarget: PsiElement,
                                                    context: TypeEvalContext,
                                                    anchor: PsiElement?): PyNamedTupleType? {
      return when {
        referenceTarget is PyFunction && anchor is PyCallExpression -> getNamedTupleFunctionType(referenceTarget, context, anchor)
        referenceTarget is PyTargetExpression -> getNamedTupleTypeForTarget(referenceTarget, context)
        else -> null
      }
    }

    internal fun getNamedTupleReplaceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): PyCallableType? {
      if (referenceTarget is PyFunction &&
          anchor is PyCallExpression &&
          PyTypingTypeProvider.NAMEDTUPLE == referenceTarget.containingClass?.qualifiedName) {
        val callee = anchor.callee as? PyReferenceExpression ?: return null
        return getNamedTupleReplaceType(callee, context)
      }

      return null
    }

    private fun getFieldTypeForNamedTupleAsTarget(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
      val qualifierNTType = referenceExpression.qualifier?.let { context.getType(it) } as? PyNamedTupleType ?: return null
      return qualifierNTType.fields[referenceExpression.name]?.type
    }

    private fun getFieldTypeForTypingNTFunctionInheritor(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
      val qualifierType = referenceExpression.qualifier?.let { context.getType(it) } as? PyWithAncestors
      if (qualifierType == null || qualifierType is PyNamedTupleType) return null

      return PyUnionType.union(
        qualifierType
          .getAncestorTypes(context)
          .filterIsInstance<PyNamedTupleType>()
          .mapNotNull { it.fields[referenceExpression.name] }
          .map { it.type }
          .toList()
      )
    }

    private fun getNamedTupleTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyNamedTupleType? {
      if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

      val resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context)
      val resolveResults = referenceExpression.getReference(resolveContext).multiResolve(false)

      for (element in PyUtil.filterTopPriorityResults(resolveResults)) {
        if (element is PyTargetExpression) {
          val result = getNamedTupleTypeForTarget(element, context)
          if (result != null) {
            return result
          }
        }

        if (element is PyClass) {
          val result = getNamedTupleTypeForTypingNTInheritorAsCallee(element, context)
          if (result != null) {
            return result
          }
        }

        if (element is PyTypedElement) {
          val type = context.getType(element)
          if (type is PyClassLikeType) {
            val superClassTypes = type.getSuperClassTypes(context)

            val superNTType = superClassTypes.asSequence().filterIsInstance<PyNamedTupleType>().firstOrNull()
            if (superNTType != null) {
              return superNTType
            }
          }
        }
      }

      return null
    }

    private fun getNamedTupleReplaceType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
      val call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) ?: return null

      val qualifier = referenceExpression.qualifier
      if (qualifier != null && "_replace" == referenceExpression.referencedName) {
        val qualifierType = context.getType(qualifier) as? PyClassLikeType ?: return null

        val namedTupleType = StreamEx
          .of<PyType>(qualifierType)
          .append(qualifierType.getSuperClassTypes(context))
          .select(PyNamedTupleType::class.java)
          .findFirst()
          .orElse(null)

        if (namedTupleType != null) {
          return if (namedTupleType.isTyped) createTypedNamedTupleReplaceType(referenceExpression, namedTupleType.fields, qualifierType)
          else createUntypedNamedTupleReplaceType(call, namedTupleType.fields, qualifierType, context)
        }

        if (qualifierType is PyClassType) {
          val cls = qualifierType.pyClass
          if (isTypingNamedTupleDirectInheritor(cls, context)) {
            return createTypedNamedTupleReplaceType(referenceExpression, collectTypingNTInheritorFields(cls, context), qualifierType)
          }
        }
      }

      return null
    }

    private fun getNamedTupleFunctionType(function: PyFunction, context: TypeEvalContext, call: PyCallExpression): PyNamedTupleType? {
      if (ArrayUtil.contains(function.qualifiedName, PyNames.COLLECTIONS_NAMEDTUPLE_PY2, PyNames.COLLECTIONS_NAMEDTUPLE_PY3) ||
          PyTypingTypeProvider.NAMEDTUPLE == PyUtil.turnConstructorIntoClass(function)?.qualifiedName) {
        return getNamedTupleTypeFromAST(call, context)
      }

      return null
    }

    private fun getNamedTupleTypeForTarget(target: PyTargetExpression, context: TypeEvalContext): PyNamedTupleType? {
      val stub = target.stub

      return if (stub != null) {
        getNamedTupleTypeFromStub(target,
                                  stub.getCustomStub(PyNamedTupleStub::class.java),
                                  context,
                                  PyNamedTupleType.DefinitionLevel.NEW_TYPE)
      }
      else getNamedTupleTypeFromAST(target, context)
    }

    private fun getNamedTupleTypeForTypingNTInheritorAsCallee(cls: PyClass, context: TypeEvalContext): PyNamedTupleType? {
      if (isTypingNamedTupleDirectInheritor(cls, context)) {
        val name = cls.name ?: return null
        val tupleClass = PyPsiFacade.getInstance(cls.project).createClassByQName(PyTypingTypeProvider.NAMEDTUPLE, cls) ?: return null

        return PyNamedTupleType(tupleClass,
                                name,
                                collectTypingNTInheritorFields(cls, context),
                                PyNamedTupleType.DefinitionLevel.NEW_TYPE,
                                true,
                                cls)
      }

      return null
    }

    private fun getNamedTupleTypeFromStub(targetOrCall: PsiElement,
                                          stub: PyNamedTupleStub?,
                                          context: TypeEvalContext,
                                          definitionLevel: PyNamedTupleType.DefinitionLevel): PyNamedTupleType? {
      if (stub == null) return null

      val tupleClass = PyPsiFacade
                         .getInstance(targetOrCall.project)
                         .createClassByQName(PyTypingTypeProvider.NAMEDTUPLE, targetOrCall) ?: return null
      val fields = stub.fields

      return PyNamedTupleType(tupleClass,
                              stub.name,
                              parseNamedTupleFields(targetOrCall, fields, context),
                              definitionLevel,
                              fields.values.any { it.isPresent },
                              getDeclaration(targetOrCall))
    }

    private fun getNamedTupleTypeFromAST(target: PyTargetExpression, context: TypeEvalContext): PyNamedTupleType? {
      return if (context.maySwitchToAST(target)) {
        getNamedTupleTypeFromStub(target, PyNamedTupleStubImpl.create(target), context, PyNamedTupleType.DefinitionLevel.NEW_TYPE)
      }
      else null
    }

    private fun createTypedNamedTupleReplaceType(anchor: PsiElement,
                                                 fields: ImmutableNTFields,
                                                 qualifierType: PyClassLikeType): PyCallableType {
      val parameters = mutableListOf<PyCallableParameter>()
      val resultType = qualifierType.toInstance()
      val elementGenerator = PyElementGenerator.getInstance(anchor.project)

      if (qualifierType.isDefinition) {
        parameters.add(PyCallableParameterImpl.nonPsi(PyNames.CANONICAL_SELF, resultType))
      }
      parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()))

      val ellipsis = elementGenerator.createEllipsis()

      for ((name, typeAndValue) in fields) {
        parameters.add(PyCallableParameterImpl.nonPsi(name, typeAndValue.type, typeAndValue.defaultValue ?: ellipsis))
      }

      return PyCallableTypeImpl(parameters, resultType)
    }

    private fun createUntypedNamedTupleReplaceType(call: PyCallExpression,
                                                   fields: ImmutableNTFields,
                                                   qualifierType: PyClassLikeType,
                                                   context: TypeEvalContext): PyCallableType {
      val parameters = mutableListOf<PyCallableParameter>()
      val resultType = qualifierType.toInstance()
      val elementGenerator = PyElementGenerator.getInstance(call.project)

      if (qualifierType.isDefinition) {
        parameters.add(PyCallableParameterImpl.nonPsi(PyNames.CANONICAL_SELF, resultType))
      }
      parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()))

      val ellipsis = elementGenerator.createEllipsis()

      fields.keys.mapTo(parameters) { PyCallableParameterImpl.nonPsi(it, null, ellipsis) }

      return if (resultType is PyNamedTupleType) {
        val newFields = mutableMapOf<String?, PyType?>()

        for (argument in call.arguments) {
          if (argument is PyKeywordArgument) {
            val value = argument.valueExpression
            if (value != null) {
              newFields[argument.keyword] = context.getType(value)
            }
          }
        }

        PyCallableTypeImpl(parameters, resultType.clarifyFields(newFields))
      }
      else PyCallableTypeImpl(parameters, resultType)
    }

    private fun collectTypingNTInheritorFields(cls: PyClass, context: TypeEvalContext): NTFields {
      val fields = mutableListOf<PyTargetExpression>()

      cls.processClassLevelDeclarations { element, _ ->
        if (element is PyTargetExpression && element.annotationValue != null) {
          fields.add(element)
        }

        true
      }

      val ellipsis = PyElementGenerator.getInstance(cls.project).createEllipsis()

      val toNTFields = Collectors.toMap<PyTargetExpression, String, PyNamedTupleType.FieldTypeAndDefaultValue, NTFields>(
        { it.name },
        { field ->
          val value = when {
            context.maySwitchToAST(field) -> field.findAssignedValue()
            field.hasAssignedValue() -> ellipsis
            else -> null
          }

          PyNamedTupleType.FieldTypeAndDefaultValue(context.getType(field), value)
        },
        { _, v2 -> v2 },
        { NTFields() })

      return fields.stream().collect(toNTFields)
    }

    private fun getNamedTupleTypeFromAST(call: PyCallExpression, context: TypeEvalContext): PyNamedTupleType? {
      return if (context.maySwitchToAST(call)) {
        getNamedTupleTypeFromStub(call,
                                  PyNamedTupleStubImpl.create(call),
                                  context,
                                  PyNamedTupleType.DefinitionLevel.NT_FUNCTION)
      }
      else null
    }

    private fun parseNamedTupleFields(anchor: PsiElement, fields: Map<String, Optional<String>>, context: TypeEvalContext): NTFields {
      val result = NTFields()
      for ((name, type) in fields) {
        result[name] = parseNamedTupleField(anchor, type.orElse(null), context)
      }
      return result
    }

    private fun parseNamedTupleField(anchor: PsiElement,
                                     type: String?,
                                     context: TypeEvalContext): PyNamedTupleType.FieldTypeAndDefaultValue {
      if (type == null) return PyNamedTupleType.FieldTypeAndDefaultValue(null, null)

      val pyType = Ref.deref(PyTypingTypeProvider.getStringBasedType(type, anchor, context))
      return PyNamedTupleType.FieldTypeAndDefaultValue(pyType, null)
    }

    private fun getDeclaration(referenceTarget: PsiElement): PyQualifiedNameOwner? {
      return when (referenceTarget) {
        is PyTargetExpression -> referenceTarget
        is PyCallExpression -> (referenceTarget.parent as? PyAssignmentStatement)?.leftHandSideExpression as? PyTargetExpression
        else -> null
      }
    }
  }
}
