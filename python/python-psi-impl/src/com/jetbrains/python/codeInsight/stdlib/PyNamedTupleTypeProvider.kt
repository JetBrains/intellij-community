// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.mapSmartNotNull
import com.jetbrains.python.PyNames
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleTypeProvider.Helper.isTypingNamedTupleDirectInheritor
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.PyWithAncestors
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyNamedTupleStubImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.stubs.PyNamedTupleStub
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyNamedTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import one.util.streamex.StreamEx
import java.util.stream.Collectors
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private typealias NTFields = LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue>
private typealias ImmutableNTFields = Map<String, PyNamedTupleType.FieldTypeAndDefaultValue>

class PyNamedTupleTypeProvider : PyTypeProviderBase() {

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    return getNamedTupleTypeForResolvedCallee(referenceTarget, context, anchor).notNullToRef()
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

    val namedTupleReplaceType = getNamedTupleReplaceType(referenceExpression, context)
    if (namedTupleReplaceType != null) {
      return namedTupleReplaceType
    }

    return null
  }

  override fun prepareCalleeTypeForCall(type: PyType?, call: PyCallExpression, context: TypeEvalContext): Ref<PyCallableType?>? {
    return if (type is PyNamedTupleType) Ref.create(type) else null
  }

  override fun getMemberTypes(type: PyType, name: String, location: PyExpression?, direction: AccessDirection, context: PyResolveContext): List<PyTypeMember>? {
    val typeEvalContext = context.typeEvalContext
    if (PyNames.MATCH_ARGS == name && Helper.isNamedTuple(type, typeEvalContext)) {
      val fieldNames = getCallableType(type, typeEvalContext, type.pyClass)?.getParameters(typeEvalContext)?.mapNotNull { it.name }
      if (fieldNames == null) return null
      val matchArgsType = PyTypeUtil.createTupleOfLiteralStringsType(type.pyClass, fieldNames) ?: return null
      return listOf(PyTypeMember(null, matchArgsType))
    }
    if (type !is PyNamedTupleType) return null
    type.fields[name]?.let {
      return listOf(PyTypeMember(null, it.type))
    }
    return null
  }

  object Helper {
    @OptIn(ExperimentalContracts::class)
    fun isNamedTuple(type: PyType?, context: TypeEvalContext): Boolean {
      contract { returns(true) implies (type is PyClassType) }
      if (type is PyNamedTupleType) return true

      val isNT = { t: PyClassLikeType? -> t is PyNamedTupleType || t != null && PyTypingTypeProvider.NAMEDTUPLE == t.classQName }
      return type is PyClassType && type.getAncestorTypes(context).any(isNT)
    }

    fun isTypingNamedTupleDirectInheritor(cls: PyClass, context: TypeEvalContext): Boolean {
      val isTypingNT = { type: PyClassLikeType? ->
        type != null && type !is PyNamedTupleType && PyTypingTypeProvider.NAMEDTUPLE == type.classQName
      }

      return cls.getSuperClassTypes(context).any(isTypingNT)
    }
  }

}

private fun getNamedTupleTypeForResolvedCallee(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): PyType? {
  return when {
    referenceTarget is PyFunction && anchor is PyCallExpression -> getNamedTupleFunctionType(referenceTarget, context, anchor)
    referenceTarget is PyTargetExpression -> getNamedTupleTypeForTarget(referenceTarget, context)
    referenceTarget is PyClass && anchor is PyCallExpression -> getNamedTupleTypeForClass(referenceTarget, context, anchor)
    referenceTarget is PyParameter && anchor is PyCallExpression && referenceTarget.isSelf -> {
      PsiTreeUtil.getParentOfType(referenceTarget, PyFunction::class.java)
        ?.takeIf { it.modifier == PyAstFunction.Modifier.CLASSMETHOD }
        ?.let { method ->
          method.containingClass?.let { getNamedTupleTypeForClass(it, context, anchor) }
        }
    }
    else -> null
  }
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

private fun getNamedTupleReplaceType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyCallableType? {
  val call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) ?: return null

  val qualifier = referenceExpression.qualifier
  if (qualifier != null && "_replace" == referenceExpression.referencedName) {
    val qualifierType = context.getType(qualifier) as? PyClassLikeType ?: return null

    return getCallableType(qualifierType, context, call)
  }

  return null
}

private fun getCallableType(
  qualifierType: PyClassLikeType,
  context: TypeEvalContext,
  anchor: PsiElement,
): PyCallableType? {
  val namedTupleType = StreamEx
    .of<PyType>(qualifierType)
    .append(qualifierType.getSuperClassTypes(context))
    .select(PyNamedTupleType::class.java)
    .findFirst()
    .orElse(null)

  if (namedTupleType != null) {
    return if (namedTupleType.isTyped) createTypedNamedTupleReplaceType(anchor, namedTupleType.fields, qualifierType)
    else createUntypedNamedTupleReplaceType(anchor, namedTupleType.fields, qualifierType, context)
  }

  if (qualifierType is PyClassType) {
    val cls = qualifierType.pyClass
    if (isTypingNamedTupleDirectInheritor(cls, context)) {
      return createTypedNamedTupleReplaceType(anchor, collectTypingNTInheritorFields(cls, context), qualifierType)
    }
  }
  return null
}

private fun getNamedTupleFunctionType(function: PyFunction, context: TypeEvalContext, call: PyCallExpression): PyType? {
  if (ArrayUtil.contains(function.qualifiedName, PyNames.COLLECTIONS_NAMEDTUPLE_PY2, PyNames.COLLECTIONS_NAMEDTUPLE_PY3) ||
      PyTypingTypeProvider.NAMEDTUPLE == PyUtil.turnConstructorIntoClass(function)?.qualifiedName) {
    return if (context.maySwitchToAST(call)) {
      val functionType = context.getType(function) as? PyCallableType ?: return null
      val returnType = getNamedTupleTypeFromStub(call, PyNamedTupleStubImpl.create(call), context) ?: return null

      PyCallableTypeImpl(
        functionType.getParameters(context),
        returnType,
        functionType.callable,
        functionType.modifier,
        functionType.implicitOffset
      )
    }
    else null
  }

  return null
}

private fun getNamedTupleTypeForTarget(target: PyTargetExpression, context: TypeEvalContext): PyNamedTupleType? {
  return StubAwareComputation.on(target)
    .withCustomStub { it.getCustomStub(PyNamedTupleStub::class.java) }
    .overStub { getNamedTupleTypeFromStub(target, it, context) }
    .withStubBuilder { PyNamedTupleStubImpl.create(it) }
    .compute(context)
}

private fun getNamedTupleTypeForClass(cls: PyClass, context: TypeEvalContext, call: PyCallExpression): PyType? {
  return getNamedTupleTypeForNTInheritorAsCallee(cls, context)
         ?: PyUnionType.union(
           cls.multiFindInitOrNew(false, context).mapSmartNotNull { getNamedTupleFunctionType(it, context, call) }
         )
}

private fun getNamedTupleTypeForNTInheritorAsCallee(cls: PyClass, context: TypeEvalContext): PyNamedTupleType? {
  if (cls.findInitOrNew(false, context) != null) return null

  return if (isTypingNamedTupleDirectInheritor(cls, context)) {
    val name = cls.name ?: return null
    PyNamedTupleType(cls, name, collectTypingNTInheritorFields(cls, context), true, true, cls)
  }
  else {
    val base =
      cls.getSuperClassTypes(context).firstOrNull(PyNamedTupleType::class.java::isInstance) as PyNamedTupleType? ?: return null
    val name = cls.name ?: return null
    PyNamedTupleType(cls, name, LinkedHashMap(base.fields), true, true, cls)
  }
}

private fun getNamedTupleTypeFromStub(targetOrCall: PsiElement, stub: PyNamedTupleStub?, context: TypeEvalContext): PyNamedTupleType? {
  if (stub == null) return null

  val tupleClass = PyPsiFacade
                     .getInstance(targetOrCall.project)
                     .createClassByQName(PyTypingTypeProvider.NAMEDTUPLE, targetOrCall) ?: return null
  val fields = stub.fields

  return PyNamedTupleType(tupleClass,
                          stub.name,
                          parseNamedTupleFields(targetOrCall, fields, context),
                          true,
                          fields.values.any { it.type != null },
                          getDeclaration(targetOrCall))
}

private fun createTypedNamedTupleReplaceType(
  anchor: PsiElement,
  fields: ImmutableNTFields,
  qualifierType: PyClassLikeType,
): PyCallableType {
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

private fun createUntypedNamedTupleReplaceType(
  anchor: PsiElement,
  fields: ImmutableNTFields,
  qualifierType: PyClassLikeType,
  context: TypeEvalContext,
): PyCallableType? {
  val parameters = mutableListOf<PyCallableParameter>()
  val resultType = qualifierType.toInstance()
  val elementGenerator = PyElementGenerator.getInstance(anchor.project)

  if (qualifierType.isDefinition) {
    parameters.add(PyCallableParameterImpl.nonPsi(PyNames.CANONICAL_SELF, resultType))
  }
  parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()))

  val ellipsis = elementGenerator.createEllipsis()

  fields.keys.mapTo(parameters) { PyCallableParameterImpl.nonPsi(it, null, ellipsis) }

  return if (resultType is PyNamedTupleType && anchor is PyCallExpression) {
    val newFields = mutableMapOf<String?, PyType?>()
    val call = anchor

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

private fun parseNamedTupleFields(
  anchor: PsiElement,
  fields: LinkedHashMap<String, PyNamedTupleStub.FieldTypeAndHasDefault>,
  context: TypeEvalContext,
): NTFields {
  val result = NTFields()
  for ((name, typeAndDefault) in fields) {
    result[name] = parseNamedTupleField(anchor, typeAndDefault.type(), typeAndDefault.hasDefault(), context)
  }
  return result
}

private fun parseNamedTupleField(
  anchor: PsiElement,
  type: String?,
  hasDefault: Boolean,
  context: TypeEvalContext,
): PyNamedTupleType.FieldTypeAndDefaultValue {
  val pyType = type?.let { Ref.deref(PyTypingTypeProvider.getStringBasedType(type, anchor, context)) }
  val defaultValue = if (hasDefault) PyElementGenerator.getInstance(anchor.project).createEllipsis() else null
  return PyNamedTupleType.FieldTypeAndDefaultValue(pyType, defaultValue)
}

private fun getDeclaration(referenceTarget: PsiElement): PyQualifiedNameOwner? {
  return when (referenceTarget) {
    is PyTargetExpression -> referenceTarget
    is PyCallExpression -> (referenceTarget.parent as? PyAssignmentStatement)?.leftHandSideExpression as? PyTargetExpression
    else -> null
  }
}