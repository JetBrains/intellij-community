// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.TypedDictFieldQualifier
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.getTypedDictFieldQualifiers
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isGetMethodToOverride
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypedDict
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.nameIsTypedDict
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.resolveToQualifiedNames
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyBoolLiteralExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.StubAwareComputation
import com.jetbrains.python.psi.impl.stubs.PyTypedDictStubImpl
import com.jetbrains.python.psi.stubs.PyTypedDictFieldStub
import com.jetbrains.python.psi.stubs.PyTypedDictStub
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.PyTypeUtil.derefOrUnknown
import com.jetbrains.python.psi.types.PyTypeUtil.notNullToRef
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_CLOSED_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_EXTRA_ITEMS_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.stream.Collectors

typealias TDFields = LinkedHashMap<String, PyTypedDictType.FieldTypeAndTotality>

class PyTypedDictTypeProvider : PyTypeProviderBase() {
  override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
    return getTypedDictTypeForCallee(referenceExpression, context)
           ?: getTypedDictMemberType(referenceExpression, context)
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val type = when (referenceTarget) {
      is PyClass -> getTypedDictTypeForClass(referenceTarget, true, context)
      is PyTargetExpression -> getTypedDictTypeForTarget(referenceTarget, context)
      else -> null
    }
    return type.notNullToRef()
  }

  override fun prepareCalleeTypeForCall(type: PyType?, callee: PyExpression, context: TypeEvalContext): Ref<PyCallableType?>? {
    return if (type is PyTypedDictType) Ref.create(type) else null
  }

  object Helper {
    val nameIsTypedDict: (String?) -> Boolean =
      { name: String? -> name == PyTypingTypeProvider.TYPED_DICT || name == PyTypingTypeProvider.TYPED_DICT_EXT }

    fun isGetMethodToOverride(call: PyCallExpression, context: TypeEvalContext): Boolean {
      val callee = call.callee
      return callee != null && PyTypingTypeProvider.resolveToQualifiedNames(callee, context)
        .any { PyNames.FQN.unqualifyBuiltinName(it) == "dict.get" /* py3 */ || it == PyTypingTypeProvider.MAPPING_GET /* py2 */ }
    }

    fun isTypedDict(expression: PyExpression, context: TypeEvalContext): Boolean {
      return PyTypingTypeProvider.resolveToQualifiedNames(expression, context).any(nameIsTypedDict)
    }

    @JvmStatic
    fun PyClass.isTypingTypedDictInheritor(context: TypeEvalContext): Boolean {
      val isTypingTD = { type: PyClassLikeType? ->
        type is PyTypedDictType || nameIsTypedDict(type?.classQName)
      }
      if (checkIfClassIsDirectTypedDictInheritor(this, context)) return true
      val ancestors = getAncestorTypes(context)

      return ancestors.any(isTypingTD)
    }

    fun getTypedDictFieldQualifiers(expression: PySubscriptionExpression, context: TypeEvalContext): List<TypedDictFieldQualifier> {
      val result = mutableListOf<TypedDictFieldQualifier>()
      expression.accept(object : PyRecursiveElementVisitor() {
        override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
          val resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(node.operand, context)
          if (resolvedNames.any { name -> PyTypingTypeProvider.REQUIRED == name || PyTypingTypeProvider.REQUIRED_EXT == name }) {
            result.add(TypedDictFieldQualifier.REQUIRED)
          }
          else if (resolvedNames.any { name -> PyTypingTypeProvider.NOT_REQUIRED == name || PyTypingTypeProvider.NOT_REQUIRED_EXT == name }) {
            result.add(TypedDictFieldQualifier.NOT_REQUIRED)
          }
          else if (resolvedNames.any { name -> PyTypingTypeProvider.READONLY == name || PyTypingTypeProvider.READONLY_EXT == name }) {
            result.add(TypedDictFieldQualifier.READ_ONLY)
          }
          super.visitPySubscriptionExpression(node)
        }
      })
      return result
    }

    enum class TypedDictFieldQualifier {
      REQUIRED,
      NOT_REQUIRED,
      READ_ONLY
    }

    fun getTypedDictTypeForResolvedElement(resolved: PsiElement, context: TypeEvalContext): PyTypedDictType? {
      return Ref.deref(PyUtil.getParameterizedCachedValue(resolved, context) { typeEvalContext ->
        val type = if (resolved is PyClass) getTypedDictTypeForClass(resolved, false, typeEvalContext) else null
        Ref.create(type)
      })
    }
  }
}

private fun getTypedDictTypeForTarget(target: PyTargetExpression, context: TypeEvalContext): PyTypedDictType? {
  return StubAwareComputation.on(target)
    .withCustomStub { it.getCustomStub(PyTypedDictStub::class.java) }
    .overStub { getTypedDictTypeFromStub(target, it, context) }
    .withStubBuilder { PyTypedDictStubImpl.create(it) }
    .compute(context)
}

private enum class TypedDictMethod(val qualifiedName: String) {
  KEYS("dict.keys"),
  VALUES("dict.values"),
  ITEMS("dict.items"),
  POPITEM("typing.MutableMapping.popitem");

  companion object {
    private val BY_QUALIFIED_NAME = entries.associateBy { it.qualifiedName }

    fun fromQualifiedName(name: String): TypedDictMethod? = BY_QUALIFIED_NAME[name]

    fun fromResolvedNames(names: Collection<String>): TypedDictMethod? {
      return names.firstNotNullOfOrNull { fromQualifiedName(it) }
    }
  }
}

private fun getTypedDictMemberType(referenceTarget: PsiElement, context: TypeEvalContext): PyCallableType? {
  val callExpression =
    if (context.maySwitchToAST(referenceTarget)) PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceTarget) else null
  val callee = callExpression?.callee as? PyQualifiedExpression ?: return null
  val receiver = callee.qualifier ?: return null
  val receiverType = context.getType(receiver)
  if (receiverType !is PyTypedDictType) return null

  if (isGetMethodToOverride(callExpression, context)) {
    return buildGetMethodType(referenceTarget, callExpression, receiverType, context)
  }

  val resolvedNames = resolveToQualifiedNames(callee, context)
  val method = TypedDictMethod.fromResolvedNames(resolvedNames) ?: return null

  val builtinCache = PyBuiltinCache.getInstance(referenceTarget)
  val strType = builtinCache.strType ?: return null
  val valueType = collectTypedDictValueType(receiverType, builtinCache) ?: return null

  val returnType: PyType = when (method) {
    TypedDictMethod.KEYS -> {
      val listClass = builtinCache.listType?.pyClass ?: return null
      PyCollectionTypeImpl(listClass, false, listOf(strType))
    }
    TypedDictMethod.VALUES -> {
      val listClass = builtinCache.listType?.pyClass ?: return null
      PyCollectionTypeImpl(listClass, false, listOf(valueType))
    }
    TypedDictMethod.ITEMS -> {
      val listClass = builtinCache.listType?.pyClass ?: return null
      val tupleType = PyTupleType.create(referenceTarget, listOf(strType, valueType)) ?: return null
      PyCollectionTypeImpl(listClass, false, listOf(tupleType))
    }
    TypedDictMethod.POPITEM -> {
      PyTupleType.create(referenceTarget, listOf(strType, valueType)) ?: return null
    }
  }

  return PyCallableTypeImpl(emptyList(), returnType)
}

private fun buildGetMethodType(
  referenceTarget: PsiElement,
  callExpression: PyCallExpression,
  typedDictType: PyTypedDictType,
  context: TypeEvalContext
): PyCallableType {
  val parameters = mutableListOf<PyCallableParameter>()
  val builtinCache = PyBuiltinCache.getInstance(referenceTarget)
  parameters.add(PyCallableParameterImpl.nonPsi("key", builtinCache.strType))
  parameters.add(PyCallableParameterImpl.nonPsi("default", PyAnyType.any, PyNames.NONE))
  val key = PyEvaluator.evaluate(callExpression.getArgument(0, "key", PyExpression::class.java), String::class.java)
  val defaultArgument = callExpression.getArgument(1, "default", PyExpression::class.java)
  val default = if (defaultArgument != null) context.getType(defaultArgument) else builtinCache.noneType
  val valueTypeAndTotality = typedDictType.fields[key]
  return PyCallableTypeImpl(parameters,
                            when {
                              valueTypeAndTotality == null -> default
                              valueTypeAndTotality.qualifiers.isRequired == true -> valueTypeAndTotality.type
                              else -> PyUnionType.union(valueTypeAndTotality.type, default)
                            })
}

private fun collectTypedDictValueType(typedDictType: PyTypedDictType, builtinCache: PyBuiltinCache): PyType? {
  val valueTypes = mutableListOf<PyType?>()

  typedDictType.fields.values.forEach { field ->
    valueTypes.add(field.type)
  }

  if (!typedDictType.isClosed && typedDictType.extraItemsType != null) {
    valueTypes.add(typedDictType.extraItemsType)
  }

  val nonNullTypes = valueTypes.filterNotNull()
  if (nonNullTypes.isEmpty()) {
    return builtinCache.objectType
  }

  return PyUnionType.union(nonNullTypes)
}

private fun getTypedDictTypeForCallee(referenceExpression: PyReferenceExpression, context: TypeEvalContext): PyType? {
  if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) return null

  if (isTypedDict(referenceExpression, context)) {
    val builtinCache = PyBuiltinCache.getInstance(referenceExpression)
    val languageLevel = LanguageLevel.forElement(referenceExpression)

    val dictType = builtinCache.dictType
    val strToTypeDictType = if (dictType != null) {
      PyCollectionTypeImpl(dictType.pyClass, false, listOf(builtinCache.strType, builtinCache.typeType))
    }
    else {
      null
    }

    val parameters = listOf(
      PyCallableParameterImpl.nonPsi("typename", builtinCache.getStringType(languageLevel)),
      PyCallableParameterImpl.nonPsi("fields", strToTypeDictType),
      PyCallableParameterImpl.keywordOnlySeparatorNonPsi(),
      PyCallableParameterImpl.positionalOnlySeparatorNonPsi(),
      PyCallableParameterImpl.nonPsi(TYPED_DICT_TOTAL_PARAMETER, builtinCache.boolType, PyNames.TRUE),
      PyCallableParameterImpl.nonPsi(TYPED_DICT_CLOSED_PARAMETER, builtinCache.boolType, PyNames.FALSE),
      PyCallableParameterImpl.nonPsi(
        TYPED_DICT_EXTRA_ITEMS_PARAMETER,
        builtinCache.typeType,
        PyNames.NONE
      )
    )

    return PyCallableTypeImpl(parameters, PyAnyType.unknown)
  }

  return null
}

private fun getTypedDictTypeForClass(
  cls: PyClass,
  isDefinition: Boolean,
  context: TypeEvalContext,
): PyTypedDictType? {
  if (!cls.isTypingTypedDictInheritor(context)) return null

  val forms = Ref.deref(PyUtil.getParameterizedCachedValue(cls, context) { evalContext ->
    val definition = createTypedDictTypeForClass(cls, evalContext)
    Ref.create(definition?.let { it to it.toInstance() })
  }) ?: return null
  return if (isDefinition) forms.first else forms.second
}

private fun createTypedDictTypeForClass(cls: PyClass, context: TypeEvalContext): PyTypedDictType? {

  val typedDictAncestors = typedDictAncestors(cls, context)
    .filterIsInstance<PyTypedDictType>()

  val extraItemsText = getSuperClassKeywordArgumentText(cls, TYPED_DICT_EXTRA_ITEMS_PARAMETER)
  val closedText = getSuperClassKeywordArgumentText(cls, TYPED_DICT_CLOSED_PARAMETER)

  val extraItemsProvider = {
    val type =
      if (extraItemsText != null) PyTypingTypeProvider.getStringBasedType(extraItemsText, cls, context).derefOrUnknown()
      else typedDictAncestors.firstNotNullOfOrNull { it.extraItemsType } ?: PyAnyType.unknown
    extraItems(type, extraItemsText, cls, context)
  }

  val closed = when (closedText) {
    PyNames.TRUE -> true
    PyNames.FALSE -> false
    else -> typedDictAncestors.firstOrNull()?.isClosed ?: false
  }

  return PyTypedDictType(
    cls.name ?: return null,
    { collectFields(cls, context) },
    PyBuiltinCache.getInstance(cls).dictType?.pyClass ?: return null,
    true,
    cls,
    closed,
    extraItemsProvider,
    ownTypeParameters(cls, context),
  )
}

/**
 * The ancestors up to `TypedDict` itself, past which come `dict` and its own hierarchy.
 *
 * Without AST the ancestors are resolved from the superclass names in the stub, so `TypedDict` is the resolved class rather than
 * a synthetic [PyCustomType] and a TypedDict ancestor is a plain [PyClassType]. The boundary is found by name so that both modes
 * see the same slice, and the arguments dropped along with the expressions are put back by [specializeTypedDictAncestors].
 */
private fun typedDictAncestors(cls: PyClass, context: TypeEvalContext): List<PyClassLikeType?> {
  val ancestors = cls.getAncestorTypes(context)
  val boundary = ancestors.indexOfFirst { nameIsTypedDict(it?.classQName) }
  val inheritedAsTypedDict = if (boundary >= 0) ancestors.take(boundary) else ancestors
  return specializeTypedDictAncestors(cls, inheritedAsTypedDict, context)
}

/**
 * The ancestor of `class Child(Base[int])` is `Base`: a superclass expression is unfolded to its operand, and over a stub the
 * ancestors are built from the superclass names. The arguments are read back from the superclass expressions, which a stub keeps
 * as text, because a TypedDict does not receive them through the substitutions map.
 *
 * Applied to the finished ancestor list on purpose: evaluating `Base[T]` has to anchor `T` in the scope declaring it, which asks
 * for the type parameters of [cls] and through them for its ancestors, so it cannot run while they are being computed.
 */
private fun specializeTypedDictAncestors(
  cls: PyClass,
  ancestors: List<PyClassLikeType?>,
  context: TypeEvalContext,
): List<PyClassLikeType?> {
  val specializedBases = mutableMapOf<PyClass, PyTypedDictType>()
  for (expression in PyTypingTypeProvider.getSuperClassExpressions(cls)) {
    if (expression !is PySubscriptionExpression) continue
    val baseType = Ref.deref(PyTypingTypeProvider.getType(expression, context)) as? PyTypedDictType ?: continue
    if (!baseType.isParameterized) continue
    val baseClass = baseType.declarationElement as? PyClass ?: continue
    specializedBases[baseClass] = baseType
  }
  if (specializedBases.isEmpty()) return ancestors

  return ancestors.map { specializedBases[it?.declaringClass()] ?: it }
}

/** A TypedDict type is built on the `dict` class, so it is identified by its own declaration rather than by [PyClassType.getPyClass]. */
private fun PyClassLikeType.declaringClass(): PyClass? = when (this) {
  is PyTypedDictType -> declarationElement as? PyClass
  is PyClassType -> pyClass
  else -> null
}

/** Read from the declaration, not from the items, which a recursive TypedDict cannot evaluate while it is being built. */
private fun ownTypeParameters(cls: PyClass, context: TypeEvalContext): List<PyType?> =
  PyTypeChecker.findGenericDefinitionType(cls, context)?.typeArguments.orEmpty()

/** `extra_items` is a keyword argument rather than an assignment, so the item it stands for has no value expression. */
private fun extraItems(
  type: PyType?,
  extraItemsText: String?,
  anchor: PsiElement,
  context: TypeEvalContext,
): PyTypedDictType.FieldTypeAndTotality {
  val qualifiers = extraItemsText
    ?.let { getStringBasedTypedDictQualifiers(it, anchor, context) }
    ?: PyTypedDictType.TypedDictFieldQualifiers()
  return PyTypedDictType.FieldTypeAndTotality(null, type, qualifiers)
}

private fun getSuperClassKeywordArgumentText(cls: PyClass, name: String): String? {
  // This method is stub-friendly
  val stub = cls.stub
  if (stub != null) {
    for (entry in stub.superClassesText) {
      val eqIndex = entry.indexOf('=')
      if (eqIndex < 0) continue
      if (entry.substring(0, eqIndex).trim() == name) {
        return entry.substring(eqIndex + 1).trim()
      }
    }
    return null
  }
  return cls.superClassExpressionList?.getKeywordArgument(name)?.valueExpression?.text
}


private fun collectFields(cls: PyClass, context: TypeEvalContext): TDFields {
  val fields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
  for (ancestorType in typedDictAncestors(cls, context)) {
    when (ancestorType) {
      is PyTypedDictType -> fields.putAll(ancestorType.fields)
      // Without AST a TypedDict ancestor is a plain PyClassType, see typedDictAncestors.
      is PyClassType -> if (ancestorType.pyClass.isTypingTypedDictInheritor(context)) {
        fields.putAll(collectTypingTDInheritorFields(ancestorType.pyClass, context))
      }
    }
  }
  fields.putAll(collectDeclaredFields(cls, context))
  return TDFields(fields)
}

private fun collectTypingTDInheritorFields(cls: PyClass, context: TypeEvalContext): TDFields {
  val type = cls.getType(context)
  if (type is PyTypedDictType) {
    return TDFields(type.fields)
  }
  return collectDeclaredFields(cls, context)
}

/**
 * Scans the class's own declarations instead of going through [PyClass.getType], which for [cls] would re-enter the very
 * TypedDict type whose items are being computed — the recursion that used to leave a cut `Unknown` in the cache.
 */
private fun collectDeclaredFields(cls: PyClass, context: TypeEvalContext): TDFields {
  val fields = mutableListOf<Pair<PyExpression, PyTypedDictType.TypedDictFieldQualifiers>>()
  val totality = getTotality(cls)
  cls.processClassLevelDeclarations { element, _ ->
    if (element is PyTargetExpression) {
      val stub = element.stub
      if (context.maySwitchToAST(cls) || stub == null) {
        if (element.annotation != null) {
          fields.add(Pair(element, checkTypeSpecification(element.annotation!!.value, context, totality)))
        }
      }
      else {
        if (stub.annotation != null) {
          val annotation = PyUtil.createExpressionFromFragment(stub.annotation!!, cls)
          fields.add(Pair(stub.psi, checkTypeSpecification(annotation, context, totality)))
        }
      }
    }
    true
  }


  val toTDFields =
    Collectors.toMap<Pair<PyExpression, PyTypedDictType.TypedDictFieldQualifiers>, String, PyTypedDictType.FieldTypeAndTotality, TDFields>(
      { it.first.name },
      { field -> PyTypedDictType.FieldTypeAndTotality(field.first, context.getType(field.first), field.second) },
      { _, v2 -> v2 },
      { TDFields() })

  return fields.stream().collect(toTDFields)
}

private fun checkTypeSpecification(
  annotation: PyExpression?,
  context: TypeEvalContext,
  totality: Boolean,
): PyTypedDictType.TypedDictFieldQualifiers {
  if (annotation is PySubscriptionExpression) {
    return parseTypedDictFieldQualifiers(annotation, context, totality = totality)
  }
  return PyTypedDictType.TypedDictFieldQualifiers(isRequired = totality)
}

private fun parseTypedDictFieldQualifiers(expression: PySubscriptionExpression, context: TypeEvalContext, totality: Boolean? = null): PyTypedDictType.TypedDictFieldQualifiers {
  var isRequired = totality
  var isReadOnly = false
  var hasExplicitRequiredQualifier = false

  for (qualifier in getTypedDictFieldQualifiers(expression, context)) {
    when (qualifier) {
      TypedDictFieldQualifier.REQUIRED -> {
        isRequired = true
        hasExplicitRequiredQualifier = true
      }
      TypedDictFieldQualifier.NOT_REQUIRED -> {
        isRequired = false
        hasExplicitRequiredQualifier = true
      }
      TypedDictFieldQualifier.READ_ONLY -> isReadOnly = true
    }
  }
  return PyTypedDictType.TypedDictFieldQualifiers(isRequired = isRequired, isReadOnly = isReadOnly, hasExplicitRequiredQualifier = hasExplicitRequiredQualifier)
}

/**
 * This method helps to avoid the situation when two processes try to get an element's type simultaneously
 * and one of them ends up with null.
 */
private fun checkIfClassIsDirectTypedDictInheritor(cls: PyClass, context: TypeEvalContext): Boolean {
  val stub = cls.stub
  if (context.maySwitchToAST(cls) || stub == null) {
    return cls.superClassExpressions.any { isTypedDict(it, context) }
  }
  else {
    return stub.superClassesText.any { isTypedDict(PyUtil.createExpressionFromFragment(it, cls) ?: return false, context) }
  }
}


private fun getTotality(cls: PyClass): Boolean {
  return if (cls.stub != null) {
    "total=False" !in cls.stub.superClassesText
  }
  else {
    (cls.superClassExpressionList?.getKeywordArgument("total")?.valueExpression as? PyBoolLiteralExpression)?.value ?: true
  }
}

private fun getTypedDictTypeFromStub(
  target: PyTargetExpression,
  stub: PyTypedDictStub?,
  context: TypeEvalContext,
): PyTypedDictType? {
  if (stub == null) return null

  val dictClass = PyBuiltinCache.getInstance(target).dictType?.pyClass ?: return null

  val extraItemsText = stub.extraItemsType
  val extraItemsProvider = {
    val type = extraItemsText?.let { PyTypingTypeProvider.getStringBasedType(it, target, context) }.derefOrUnknown()
    extraItems(type, extraItemsText, target, context)
  }

  return PyTypedDictType(
    stub.name,
    { parseTypedDictFields(target, stub.fields, context, stub.isRequired) },
    dictClass,
    true,
    target,
    stub.isClosed,
    extraItemsProvider,
    declaredTypeParameters = emptyList(),
  )
}

private fun parseTypedDictFields(
  anchor: PsiElement,
  fields: List<PyTypedDictFieldStub>,
  context: TypeEvalContext,
  total: Boolean,
): TDFields {
  val result = TDFields()
  for (field in fields) {
    result[field.name] = parseTypedDictField(anchor, field.type, context, total)
  }
  return result
}

private fun parseTypedDictField(
  anchor: PsiElement,
  type: String?,
  context: TypeEvalContext,
  total: Boolean,
): PyTypedDictType.FieldTypeAndTotality {
  if (type == null) return PyTypedDictType.FieldTypeAndTotality(null, PyAnyType.unknown)

  val valueTypeWithQualifiers = getStringBasedTypeForTypedDict(type, anchor, context)
  if (valueTypeWithQualifiers == null) return PyTypedDictType.FieldTypeAndTotality(null, PyAnyType.unknown)

  val pyType = valueTypeWithQualifiers.first.derefOrUnknown()
  val requiredField = valueTypeWithQualifiers.second

  val isRequired = requiredField?.isRequired ?: total
  val qualifiers = PyTypedDictType.TypedDictFieldQualifiers(isRequired = isRequired, isReadOnly = requiredField?.isReadOnly == true)
  return PyTypedDictType.FieldTypeAndTotality(null, pyType, qualifiers)
}

private fun getStringBasedTypeForTypedDict(
  contents: String,
  anchor: PsiElement,
  context: TypeEvalContext,
): Pair<Ref<PyType?>?, PyTypedDictType.TypedDictFieldQualifiers?>? {
  val expr = createStringBasedTypedDictExpression(contents, anchor) ?: return null
  var qualifiers: PyTypedDictType.TypedDictFieldQualifiers? = null
  if (expr is PySubscriptionExpression) {
    qualifiers = parseTypedDictFieldQualifiers(expr, context)
  }
  return Pair(PyTypingTypeProvider.getType(expr, context), qualifiers)
}

private fun getStringBasedTypedDictQualifiers(
  contents: String,
  anchor: PsiElement,
  context: TypeEvalContext,
): PyTypedDictType.TypedDictFieldQualifiers? =
  (createStringBasedTypedDictExpression(contents, anchor) as? PySubscriptionExpression)
    ?.let { parseTypedDictFieldQualifiers(it, context) }

private fun createStringBasedTypedDictExpression(contents: String, anchor: PsiElement): PyExpression? {
  val file = FileContextUtil.getContextFile(anchor) ?: return null
  return PyUtil.createExpressionFromFragment(contents, file)
}
