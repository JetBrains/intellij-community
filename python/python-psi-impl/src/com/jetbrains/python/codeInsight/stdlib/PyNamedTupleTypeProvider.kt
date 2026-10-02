// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
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
import com.jetbrains.python.psi.PyNamedParameter
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
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyNamedTupleType
import com.jetbrains.python.psi.types.PyOverloadType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.PyTypeParameterMapping
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private typealias NTFields = LinkedHashMap<String, PyNamedTupleType.FieldTypeAndDefaultValue>
private typealias ImmutableNTFields = Map<String, PyNamedTupleType.FieldTypeAndDefaultValue>

@ApiStatus.Internal
class PyNamedTupleTypeProvider : PyTypeProviderBase() {

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val anchor = anchor?.let(PyCallExpressionNavigator::getPyCallExpressionByCallee)
    val type = when (referenceTarget) {
      is PyFunction if anchor is PyCallExpression -> getNamedTupleFunctionType(referenceTarget, context, anchor)
      is PyTargetExpression -> getNamedTupleTypeForTarget(referenceTarget, context)
      is PyClass if anchor is PyCallExpression -> getNamedTupleTypeForClass(referenceTarget, context, anchor)
      else -> null
    }
    return type.notNullToRef()
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (!param.isSelf) return null
    val cls = func.containingClass ?: return null
    val type = getNamedTupleTypeForClass(cls, context) ?: return null
    val scopeClassType = if (func.modifier == PyAstFunction.Modifier.CLASSMETHOD) type.toClass() else type.toInstance()
    return Ref.create(PySelfType(scopeClassType))
  }

  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getFieldTypeForNamedTupleAsTarget(referenceExpression, context)
           ?: getFieldTypeForTypingNTFunctionInheritor(referenceExpression, context)
           ?: getNamedTupleReplaceType(referenceExpression, context)
  }

  override fun prepareCalleeTypeForCall(type: PyType?, callee: PyExpression, context: TypeEvalContext): Ref<PyType?>? {
    if (type !is PyNamedTupleType) return null
    return Ref.create(generateGenericConstructorType(type, context) ?: type)
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

    /**
     * Returns true if [attribute] is a field of a class that inherits directly from typing `NamedTuple`.
     * A field has an annotation and is not a `ClassVar`.
     * An instance also cannot change an unannotated class attribute, but this function does not include it.
     */
    @ApiStatus.Internal
    fun isNamedTupleField(attribute: PyTargetExpression, context: TypeEvalContext): Boolean {
      if (attribute.annotationValue == null || !PyUtil.isClassAttribute(attribute) || PyTypingTypeProvider.isClassVar(attribute, context)) return false
      val cls = attribute.containingClass ?: return false
      return isTypingNamedTupleDirectInheritor(cls, context)
    }
  }
}

private fun getFieldTypeForNamedTupleAsTarget(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
  val qualifierNTType = referenceExpression.qualifier?.let { context.getType(it) } as? PyNamedTupleType ?: return null
  return qualifierNTType.fields[referenceExpression.name]?.type
}

private fun getFieldTypeForTypingNTFunctionInheritor(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
  val qualifierType = referenceExpression.qualifier?.let { context.getType(it) } as? PyWithAncestors
  if (qualifierType == null || qualifierType is PyNamedTupleType) return null

  val fieldTypes = qualifierType
    .getAncestorTypes(context)
    .filterIsInstance<PyNamedTupleType>()
    .mapNotNull { it.fields[referenceExpression.name] }
    .map { it.type }
    .toList()
  if (fieldTypes.isEmpty()) return null
  return PyUnionType.unionOrUnknown(fieldTypes)
}

/**
 * Parameterizes a generic named tuple with [typeArguments].
 *
 * The function maps the type parameters of the class to [typeArguments]. It keeps the result as the type arguments
 * of the named tuple, and rewrites each field type. For `class Base(NamedTuple, Generic[T])` with the field `path: T`,
 * `Base[str]` gets the type argument `str` and the field `path: str`.
 *
 * The function returns `null` when the class is not generic, or when the type arguments do not match the
 * type parameters.
 */
internal fun parameterizeNamedTupleType(
  type: PyNamedTupleType,
  typeArguments: List<PyType?>,
  context: TypeEvalContext,
): PyNamedTupleType? {
  val genericDefinitionType = PyTypeChecker.findGenericDefinitionType(type.pyClass, context) ?: return null
  val substitutions = PyTypeChecker.mapTypeParametersToSubstitutions(
    genericDefinitionType.typeArguments,
    typeArguments,
    PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
    PyTypeParameterMapping.Option.USE_DEFAULTS,
  ) ?: return null
  val classTypeArguments = (PyTypeChecker.substitute(genericDefinitionType, substitutions, context) as? PyClassType)?.typeArguments
                           ?: return null
  return type.mapTypes(
    // A field without a type parameter keeps its type, because a parameterization must not widen the declared type of a field.
    { fieldType -> if (fieldType.hasGenerics(context)) PyTypeChecker.substitute(fieldType, substitutions, context) else fieldType },
    { classTypeArguments },
  )
}

/**
 * The constructor signature of the generic named tuple class [type]: the fields as parameters, and the named tuple
 * parameterized with its own type parameters as the return type. The generic call then binds the type parameters,
 * as for a dataclass, so `Base(42)` of `class Base[T](NamedTuple)` with the field `foo: T` gives `Base[int]`.
 *
 * Returns `null` when [type] is not the definition of a typed generic named tuple, or is already parameterized.
 */
private fun generateGenericConstructorType(type: PyNamedTupleType, context: TypeEvalContext): PyCallableType? {
  if (!type.isDefinition || !type.isTyped || type.typeArguments.isNotEmpty()) return null
  val typeParameters = PyTypeChecker.findGenericDefinitionType(type.pyClass, context)?.typeArguments ?: return null
  val returnType = parameterizeNamedTupleType(type.toInstance(), typeParameters, context) ?: return null
  return PyCallableTypeImpl(type.getParameters(context), returnType)
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
  val namedTupleType = sequenceOf(qualifierType)
    .plus(qualifierType.getSuperClassTypes(context))
    .filterIsInstance<PyNamedTupleType>()
    .firstOrNull()

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
  val isCollectionsNamedTuple = ArrayUtil.contains(function.qualifiedName, PyNames.COLLECTIONS_NAMEDTUPLE_PY2, PyNames.COLLECTIONS_NAMEDTUPLE_PY3)
  val isTypingNamedTupleInit = !isCollectionsNamedTuple &&
                               PyTypingTypeProvider.NAMEDTUPLE == PyUtil.turnConstructorIntoClass(function)?.qualifiedName
  if (!isCollectionsNamedTuple && !isTypingNamedTupleInit) return null
  if (!context.maySwitchToAST(call)) return null

  val returnType = getNamedTupleTypeFromStub(call, PyNamedTupleStubImpl.create(call), context) ?: return null

  // `typing.NamedTuple.__init__` is overloaded; build a signature per overload and keep them as a single `PyOverloadType`
  // (matching any signature is enough), rather than a union of signatures, which would force every signature to match the call.
  val constructors = if (isTypingNamedTupleInit) function.containingClass?.multiFindInitOrNew(false, context)?.takeIf { it.isNotEmpty() }
                     else null
  val signatures = (constructors ?: listOf(function)).mapSmartNotNull { constructor ->
    val constructorType = context.getType(constructor) as? PyCallableType ?: return@mapSmartNotNull null
    val parameters = constructorType.getParameters(context)
    PyCallableTypeImpl(
      if (isTypingNamedTupleInit) parameters?.drop(1) else parameters,
      returnType,
      constructorType.callable,
      constructorType.modifier
    )
  }
  return when (signatures.size) {
    0 -> null
    1 -> signatures.single()
    else -> PyOverloadType(signatures, null)
  }
}

private fun getNamedTupleTypeForTarget(target: PyTargetExpression, context: TypeEvalContext): PyNamedTupleType? {
  return StubAwareComputation.on(target)
    .withCustomStub { it.getCustomStub(PyNamedTupleStub::class.java) }
    .overStub { getNamedTupleTypeFromStub(target, it, context) }
    .withStubBuilder { PyNamedTupleStubImpl.create(it) }
    .compute(context)
}

private fun getNamedTupleTypeForClass(cls: PyClass, context: TypeEvalContext, call: PyCallExpression): PyType? {
  return getNamedTupleTypeForClass(cls, context)
         ?: cls.multiFindInitOrNew(false, context).firstOrNull()?.let { getNamedTupleFunctionType(it, context, call) }
}

internal fun getNamedTupleTypeForClass(cls: PyClass, context: TypeEvalContext): PyNamedTupleType? {
  // Decide whether the class can be a named tuple at all before asking whether it declares `__init__`.
  // All three conditions below are necessary and each is a pure predicate, so the order does not change the result,
  // but `findInitOrNew` is far the most expensive of them: it walks the methods of the class, asks
  // `PyiUtil.isOverload` about the ones whose name matches, and each of those rescans the import statements of the
  // whole containing file (`PyKnownDecoratorUtil.computeKnownDecoratorsInFile`). This provider runs for every class
  // type evaluation, so for a class that has nothing to do with `NamedTuple` - any `pandas` class, say - that walk
  // was pure waste.
  val isDirectInheritor = isTypingNamedTupleDirectInheritor(cls, context)
  val namedTupleBase =
    if (isDirectInheritor) null
    else cls.getSuperClassTypes(context).filterIsInstance<PyNamedTupleType>().firstOrNull() ?: return null

  if (cls.findInitOrNew(false, context) != null) return null

  val name = cls.name ?: return null

  return if (isDirectInheritor) {
    PyNamedTupleType(cls, name, collectTypingNTInheritorFields(cls, context), true, true, cls)
  }
  else {
    PyNamedTupleType(cls, name, LinkedHashMap(namedTupleBase!!.fields), true, true, cls)
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
  parameters.add(PyCallableParameterImpl.keywordOnlySeparatorNonPsi())

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

  if (qualifierType.isDefinition) {
    parameters.add(PyCallableParameterImpl.nonPsi(PyNames.CANONICAL_SELF, resultType))
  }
  parameters.add(PyCallableParameterImpl.keywordOnlySeparatorNonPsi())

  fields.keys.mapTo(parameters) { PyCallableParameterImpl.nonPsi(it, PyAnyType.unknown, PyNames.ELLIPSIS) }

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

  return fields.associateTo(NTFields()) { field ->
    val value = when {
      context.maySwitchToAST(field) -> field.findAssignedValue()
      field.hasAssignedValue() -> ellipsis
      else -> null
    }

    field.name!! to PyNamedTupleType.FieldTypeAndDefaultValue(context.getType(field), value)
  }
}

private fun parseNamedTupleFields(
  anchor: PsiElement,
  fields: LinkedHashMap<String, PyNamedTupleStub.FieldTypeAndHasDefault>,
  context: TypeEvalContext,
): NTFields {
  return fields.entries.associateTo(NTFields()) { (name, typeAndDefault) ->
    val type = typeAndDefault.type()
    val pyType = type?.let { Ref.deref(PyTypingTypeProvider.getStringBasedType(type, anchor, context)) } ?: PyAnyType.unknown
    val defaultValue = if (typeAndDefault.hasDefault()) PyElementGenerator.getInstance(anchor.project).createEllipsis() else null
    name to PyNamedTupleType.FieldTypeAndDefaultValue(pyType, defaultValue)
  }
}

private fun getDeclaration(referenceTarget: PsiElement): PyQualifiedNameOwner? {
  return when (referenceTarget) {
    is PyTargetExpression -> referenceTarget
    is PyCallExpression -> (referenceTarget.parent as? PyAssignmentStatement)?.leftHandSideExpression as? PyTargetExpression
    else -> null
  }
}