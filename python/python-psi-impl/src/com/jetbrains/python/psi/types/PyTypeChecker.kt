// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyNames.isPrivate
import com.jetbrains.python.PyNames.isProtected
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.getProtocolMembers
import com.jetbrains.python.codeInsight.typing.inspectProtocolSubclass
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.ParamHelper
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.PyTypeProvider
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableParameterMapping.mapCallableParameters
import com.jetbrains.python.psi.types.PyLiteralStringType.Companion.match
import com.jetbrains.python.psi.types.PyLiteralType.Companion.match
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser
import com.jetbrains.python.psi.types.PyTypeChecker.match
import com.jetbrains.python.psi.types.PyTypeUtil.getEffectiveBound
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.Optional

object PyTypeChecker {
  /**
   * See [match] for description.
   */
  @JvmStatic
  fun match(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean {
    val substitutions = GenericSubstitutions()
    return match(expected, actual, MatchContext(context, substitutions, false)).orElse(true)!!
  }

  /**
   * Checks whether a type `actual` can be placed where `expected` is expected.
   *
   *
   * For example `int` matches `object`, while `str` doesn't match `int`.
   * Work for builtin types, classes, tuples etc.
   *
   *
   * Whether it's unknown if `actual` match `expected` the method returns `true`.
   *
   * @param expected      expected type
   * @param actual        type to be matched against expected
   * @param context       type evaluation context
   * @param substitutions map of substitutions for `expected` type
   * @return `false` if `expected` and `actual` don't match, true otherwise
   * @implNote This behavior may be changed in future by replacing `boolean` with `Optional<Boolean>` and updating the clients.
   */
  fun match(
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    typeVars: Map<PyTypeParameterType, PyType?>,
  ): Boolean {
    val substitutions = GenericSubstitutions(typeVars)
    return match(expected, actual, MatchContext(context, substitutions, false)).orElse(true)!!
  }

  @JvmStatic
  fun match(
    expected: PyType?,
    actual: PyType?,
    context: TypeEvalContext,
    substitutions: GenericSubstitutions,
  ): Boolean {
    return match(expected, actual, MatchContext(context, substitutions, false))
      .orElse(true)!!
  }

  private fun match(expected: PyType?, actual: PyType?, context: MatchContext): Optional<Boolean> {
    val result = RecursionManager.doPreventingRecursion(expected to actual, false) {
      matchImpl(expected, actual, context)
    }

    return result ?: Optional.of(true)
  }

  /**
   * Perform type matching.
   *
   *
   * Implementation details:
   *
   *  * The method mutates `context.substitutions` map adding new entries into it
   *  * The order of match subroutine calls is important
   *  * The method may recursively call itself
   *
   */
  private fun matchImpl(expected: PyType?, actual: PyType?, context: MatchContext): Optional<Boolean> {
    if (expected == actual) {
      return Optional.of(true)
    }

    for (extension in PyTypeCheckerExtension.EP_NAME.extensionList) {
      val result = extension.match(expected, actual, context.context, context.mySubstitutions)
      if (result.isPresent) {
        return result
      }
    }

    if (expected is PyClassType) {
      val match = matchObject(expected, actual)
      if (match.isPresent) {
        return match
      }
    }

    if (actual is PyNarrowedType && expected is PyNarrowedType) {
      return match(expected, actual, context)
    }

    if (actual is PyTypeVarTupleType && context.reversedSubstitutions) {
      return Optional.of(match(actual, expected, context))
    }

    if (expected is PyPositionalVariadicType) {
      return Optional.of(match(expected, actual, context))
    }

    if (actual is PyTypeVarType && context.reversedSubstitutions) {
      return Optional.of(match(actual, expected, context))
    }

    if (expected is PyTypeVarType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected is PySelfType) {
      return Optional.of(match(expected, actual, context))
    }

    if (actual is PySelfType && context.reversedSubstitutions) {
      return Optional.of(match(actual, expected, context))
    }

    if (expected is PyParamSpecType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected is PyConcatenateType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected == null || actual == null || isUnknown(actual, context.context)) {
      return Optional.of(true)
    }

    if (expected is PyCallableParameterListType) {
      return Optional.of(match(expected, actual, context))
    }

    if (actual is PyNeverType) {
      return Optional.of(true)
    }

    if (expected is PyNeverType) {
      return Optional.of(false)
    }

    if (actual is PyUnionType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected is PyUnionType) {
      return Optional.of(match(expected, actual, context))
    }

    if (actual is PyUnsafeUnionType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected is PyUnsafeUnionType) {
      return Optional.of(match(expected, actual, context))
    }

    if (actual is PyIntersectionType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected is PyIntersectionType) {
      return Optional.of(match(expected, actual, context))
    }

    if (expected is PyClassType && actual is PyClassType) {
      val match = match(expected, actual, context)
      if (match.isPresent) {
        return match
      }
    }

    if (actual is PyStructuralType && actual.isInferredFromUsages) {
      return Optional.of(true)
    }

    if (expected is PyStructuralType) {
      return Optional.of(match(expected, actual, context.context))
    }

    if (actual is PyStructuralType && expected is PyClassType) {
      val expectedAttributes = expected.getMemberNames(true, context.context)
      return Optional.of(expectedAttributes.containsAll(actual.attributeNames))
    }

    if (actual is PyCallableType && expected is PyCallableType) {
      val match = match(expected, actual, context)
      if (match.isPresent) {
        return match
      }
    }

    if (expected is PyModuleType) {
      return Optional.of(actual is PyModuleType && expected.module === actual.module)
    }

    if (expected is PyClassType && actual is PyModuleType) {
      if (expected.isProtocol(context.context)) {
        return Optional.of(match(expected, actual, context))
      }
      return match(expected, actual.moduleClassType, context)
    }

    return Optional.of(matchNumericTypes(expected, actual))
  }

  private fun match(
    expectedNarrowedType: PyNarrowedType,
    actualNarrowedType: PyNarrowedType,
    context: MatchContext,
  ): Optional<Boolean> {
    if (expectedNarrowedType.typeIs != actualNarrowedType.typeIs) {
      return Optional.of(false)
    }
    if (expectedNarrowedType.typeIs) {
      return Optional.of(expectedNarrowedType.narrowedType == actualNarrowedType.narrowedType)
    }
    return match(expectedNarrowedType.narrowedType, actualNarrowedType.narrowedType, context)
  }

  /**
   * Check whether `expected` is Python *object* or *type*.
   *
   *
   * {@see PyTypeChecker#match(PyType, PyType, TypeEvalContext, Map)}
   */
  private fun matchObject(expected: PyClassType, actual: PyType?): Optional<Boolean> {
    if (ArrayUtil.contains(expected.name, PyNames.OBJECT, PyNames.TYPE)) {
      val builtinCache = getInstance(expected.pyClass)
      if (expected == builtinCache.objectType) {
        return Optional.of(true)
      }
      if (expected == builtinCache.typeType &&
          actual is PyInstantiableType<*> && actual.isDefinition
      ) {
        return Optional.of(true)
      }
    }
    return Optional.empty()
  }

  /**
   * Match `actual` versus [PyTypeVarType] expected.
   *
   *
   * The method mutates `context.substitutions` map adding new entries into it
   */
  private fun match(expected: PyTypeVarType, actual: PyType?, context: MatchContext): Boolean {
    if (expected.isDefinition && actual is PyInstantiableType<*> && !actual.isDefinition) {
      return false
    }

    var substitutedRef = context.mySubstitutions.typeVars[expected]
    val defaultTypeRef = expected.defaultType
    if (defaultTypeRef != null) {
      val defaultType: PyType? = defaultTypeRef.get()
      // Skip default substitution
      if (defaultType != null && defaultType == Ref.deref(substitutedRef)) {
        substitutedRef = null
      }
    }
    var bound = expected.bound
    var constraints = expected.constraints
    // Promote int in Type[TypeVar('T', int)] to Type[int] before checking that bounds match
    if (expected.isDefinition) {
      bound = toClass(bound)
      constraints = constraints.map { toClass(it) }
    }

    // Remove value-specific components from the actual type to make it safe to propagate
    var safeActual = if (constraints.isEmpty() && bound is PyLiteralStringType) actual else replaceLiteralStringWithStr(actual)

    if (substitutedRef != null) {
      val substitution = substitutedRef.get()
      if (expected == safeActual || expected == substitution) {
        return true
      }

      val matchHelper = MyMatchHelper(expected, context)
      if (matchHelper.match(substitution, safeActual)) {
        return true
      }
      if (expected in context.mySubstitutions.getFrozenTypeVars(KeyImpl)) {
        return false
      }
      if (!matchHelper.match(safeActual, substitution)) {
        safeActual = PyUnionType.union(safeActual, substitution)
      }
    }

    var matchedConstraintIndex = -1
    if (constraints.isEmpty()) {
      val match = match(bound, safeActual, context)
      if (match.isPresent && !match.get()) {
        return false
      }
    }
    else {
      val finalSafeActual = safeActual
      matchedConstraintIndex =
        constraints.indexOfFirst { constraint -> match(constraint, finalSafeActual, context).orElse(true)!! }
      if (matchedConstraintIndex == -1) {
        return false
      }
    }

    if (safeActual != null) {
      val type = if (constraints.isEmpty()) safeActual else constraints[matchedConstraintIndex]
      context.mySubstitutions.putTypeVar(expected, Ref(type), KeyImpl)
    }
    else {
      val effectiveBound = expected.getEffectiveBound()
      if (effectiveBound != null) {
        context.mySubstitutions.putTypeVar(expected, Ref(PyUnionType.createWeakType(effectiveBound)), KeyImpl)
      }
    }

    return true
  }

  private fun match(expected: PySelfType, actual: PyType?, context: MatchContext): Boolean {
    if (actual == null) return true
    val substitution = context.mySubstitutions.qualifierType
    if (substitution != null && substitution !is PySelfType) {
      return match(substitution, actual, context).orElse(false)!!
    }
    if (actual !is PySelfType) return false
    return expected.isDefinition == actual.isDefinition &&
           match(expected.scopeClassType, actual.scopeClassType, context).orElse(false)!!
  }

  private fun toClass(type: PyType?): PyType? {
    return type.toStream()
      .map<PyType?> { t: PyType? -> if (t is PyInstantiableType<*>) t.toClass() else t }
      .collect(PyTypeUtil.toUnion(type))
  }

  private fun match(expected: PyPositionalVariadicType, actual: PyType?, context: MatchContext): Boolean {
    if (actual == null) {
      return true
    }
    if (actual !is PyPositionalVariadicType) {
      return false
    }
    if (expected is PyUnpackedTupleType) {
      // The actual type is just a TypeVarTuple
      if (actual !is PyUnpackedTupleType) {
        return false
      }
      if (expected.isUnbound) {
        val repeatedExpectedType = expected.elementTypes[0]
        if (actual.isUnbound) {
          return match(repeatedExpectedType, actual.elementTypes[0], context).orElse(false)!!
        }
        else {
          return actual.elementTypes
            .all { singleActualType -> match(repeatedExpectedType, singleActualType, context).orElse(false)!! }
        }
      }
      else {
        if (actual.isUnbound) {
          val repeatedActualType: PyType? = actual.elementTypes[0]
          return expected.elementTypes
            .all { singleExpectedType -> match(singleExpectedType, repeatedActualType, context).orElse(false)!! }
        }
        else {
          return matchTypeParameters(expected.elementTypes, actual.elementTypes, context)
        }
      }
    }
    else {
      val substitution = context.mySubstitutions.typeVarTuples[expected]
      if (substitution != null && substitution != PyUnpackedTupleTypeImpl.UNSPECIFIED) {
        if (expected == actual || substitution == expected) {
          return true
        }
        return if (context.reversedSubstitutions) match(actual, substitution, context)
        else match(
          substitution,
          actual,
          context
        )
      }
      context.mySubstitutions.putTypeVarTuple(expected as PyTypeVarTupleType, actual, KeyImpl)
    }
    return true
  }

  private fun replaceLiteralStringWithStr(actual: PyType?): PyType? {
    // TODO replace with PyTypeVisitor API once it's ready
    if (actual is PyLiteralStringType) {
      return PyClassTypeImpl(actual.pyClass, false)
    }
    if (actual is PyUnionType) {
      return actual.map { replaceLiteralStringWithStr(it) }
    }
    if (actual is PyNamedTupleType) {
      return actual
    }
    if (actual is PyTupleType) {
      return PyTupleType(
        actual.pyClass,
        actual.elementTypes.map { replaceLiteralStringWithStr(it) },
        actual.isHomogeneous, actual.isDefinition
      )
    }
    if (actual is PyCollectionType) {
      return PyCollectionTypeImpl(
        actual.pyClass, actual.isDefinition,
        actual.elementTypes.map { replaceLiteralStringWithStr(it) }
      )
    }
    return actual
  }

  private fun match(expected: PyParamSpecType, actual: PyType?, context: MatchContext): Boolean {
    if (actual == null) return true
    if (actual !is PyCallableParameterVariadicType) return false
    context.mySubstitutions.putParamSpec(expected, actual, KeyImpl)
    return true
  }

  private fun match(expected: PyConcatenateType, actual: PyType?, context: MatchContext): Boolean {
    if (actual == null) return true
    val expectedFirstTypes = expected.firstTypes
    val expectedPrefixSize = expectedFirstTypes.size
    val expectedParamSpec = expected.paramSpec
    if (actual is PyConcatenateType) {
      if (expectedPrefixSize > actual.firstTypes.size) {
        return false
      }
      val actualFirstTypes = actual.firstTypes.subList(0, expectedPrefixSize)
      if (!match(expectedFirstTypes, actualFirstTypes, context)) {
        return false
      }
      if (expectedParamSpec == null) {
        return true
      }
      if (actualFirstTypes.size > expectedPrefixSize) {
        return match(
          expectedParamSpec,
          PyConcatenateType(
            expectedFirstTypes.subList(0, actualFirstTypes.size),
            actual.paramSpec
          ), context
        )
      }
      else {
        return match(expectedParamSpec, actual.paramSpec, context)
      }
    }
    else if (actual is PyCallableParameterListType) {
      if (expectedPrefixSize > actual.parameters.size) {
        return false
      }
      val actualFirstParamTypes =
        actual.parameters.subList(0, expectedPrefixSize).map { it.getType(context.context) }
      if (!match(expectedFirstTypes, actualFirstParamTypes, context)) {
        return false
      }
      if (expectedParamSpec == null) {
        return true
      }
      return match(
        expectedParamSpec,
        PyCallableParameterListTypeImpl(ContainerUtil.subList(actual.parameters, expectedPrefixSize)),
        context
      )
    }
    return false
  }

  private fun match(
    expectedParameters: PyCallableParameterListType,
    actual: PyType?,
    context: MatchContext,
  ): Boolean {
    if (actual == null) return true
    if (actual !is PyCallableParameterListType) return false
    return match(expectedParameters, actual, context)
  }

  private fun match(expected: PyType, actual: PyUnionType, context: MatchContext): Boolean {
    if (expected is PyTupleType) {
      // XXX A type-widening hack for cases like PyTypeTest.testDictFromTuple
      val match = match(expected, actual, context)
      if (match.isPresent) {
        return match.get()
      }
    }

    if (!PyUnionType.isStrictSemanticsEnabled()) { // checking strictly separately until PY-24834 gets implemented
      if (actual.members.any { it is PyLiteralStringType || it is PyLiteralType }) {
        return actual.members.all { type -> match(expected, type, context).orElse(false)!! }
      }
      return actual.members.any { type -> match(expected, type, context).orElse(false)!! }
    }
    return actual.members.all { type -> match(expected, type, context).orElse(false)!! }
  }

  private fun match(
    expected: PyTupleType,
    actual: PyUnionType,
    context: MatchContext,
  ): Optional<Boolean> {
    val elementCount = expected.elementCount

    if (!expected.isHomogeneous) {
      val widenedActual = widenUnionOfTuplesToTupleOfUnions(actual, elementCount)
      if (widenedActual != null) {
        return match(expected, widenedActual, context)
      }
    }

    return Optional.empty()
  }

  private fun match(expected: PyUnionType, actual: PyType, context: MatchContext): Boolean {
    if (actual in expected.members) {
      return true
    }
    return expected.members.any { type: PyType? -> match(type, actual, context).orElse(true)!! }
  }

  private fun match(expected: PyType, actual: PyIntersectionType, context: MatchContext): Boolean {
    return actual.members.any { type: PyType? -> match(expected, type, context).orElse(false)!! }
  }

  private fun match(expected: PyIntersectionType, actual: PyType, context: MatchContext): Boolean {
    return expected.members.all { type: PyType? -> match(type, actual, context).orElse(true)!! }
  }

  private fun match(expected: PyType, actual: PyUnsafeUnionType, context: MatchContext): Boolean {
    return actual.members.any { type: PyType? -> match(expected, type, context).orElse(false)!! }
  }

  private fun match(expected: PyUnsafeUnionType, actual: PyType, context: MatchContext): Boolean {
    if (actual in expected.members) {
      return true
    }
    return expected.members.any { type: PyType? -> match(type, actual, context).orElse(true)!! }
  }

  private fun match(
    expected: PyClassType,
    actual: PyClassType,
    matchContext: MatchContext,
  ): Optional<Boolean> {
    if (expected == actual) {
      return Optional.of(true)
    }

    val context = matchContext.context

    if (expected.isDefinition xor actual.isDefinition && !expected.isProtocol(context)) {
      if (!expected.isDefinition && actual.isDefinition) {
        val metaClass = actual.getMetaClassType(context, true)
        return Optional.of(
          metaClass != null && match(expected as PyType, metaClass.toInstance(), matchContext).orElse(
            true
          )!!
        )
      }
      return Optional.of(false)
    }

    if (expected is PyTupleType && actual is PyTupleType) {
      return match(expected, actual, matchContext)
    }

    if (expected is PyLiteralType) {
      return Optional.of(actual is PyLiteralType && match(expected, actual))
    }

    if (expected is PyTypedDictType && actual !is PyTypedDictType) {
      return Optional.of(false)
    }

    if (actual is PyTypedDictType) {
      val matchResult = PyTypedDictType.match(expected, actual, context)
      if (matchResult != null) return Optional.of(matchResult)
    }

    if (expected is PyLiteralStringType) {
      return Optional.of(match(expected, actual))
    }

    val superClass = expected.pyClass
    val subClass = actual.pyClass

    if (!subClass.isSubclass(superClass, context) && expected.isProtocol(context)) {
      return Optional.of(matchProtocols(expected, actual, matchContext))
    }

    if (expected is PyCollectionType) {
      return Optional.of(match(expected, actual, matchContext))
    }

    if (matchClasses(superClass, subClass, context)) {
      if (expected is PyTypingNewType && (expected != actual) && superClass == subClass) {
        return Optional.of(expected in actual.getAncestorTypes(context))
      }
      return Optional.of(true)
    }
    return Optional.empty()
  }

  private fun matchProtocols(expected: PyClassType, actual: PyClassType, matchContext: MatchContext): Boolean {
    val expectedSubstitutions = collectTypeSubstitutions(expected, matchContext.context)
    val actualSubstitutions = collectTypeSubstitutions(actual, matchContext.context)


    // See https://typing.python.org/en/latest/spec/generics.html#use-in-protocols
    // > If a protocol uses Self in methods or attribute annotations, then a class Foo is assignable to the protocol
    //   if its corresponding methods and attribute annotations use either Self or Foo or any of Foo’s subclasses.
    //
    // It should be equivalent to replacing Self in the protocol with the Foo class we're matching it with.
    val protocolSubstitutions = GenericSubstitutions()
    protocolSubstitutions.qualifierType = actual.toInstance()
    val protocolContext =
      MatchContext(matchContext.context, protocolSubstitutions, matchContext.reversedSubstitutions)
    for (pair in inspectProtocolSubclass(
      expected, actual,
      matchContext.context
    )) {
      val protocolMember = pair.first
      val subclassElementMembers = pair.second
      if (ContainerUtil.isEmpty(subclassElementMembers)) {
        return false
      }

      val rawProtocolElementType =
        dropSelfIfNeeded(expected, protocolMember.type, matchContext.context)

      val protocolElementType = substitute(rawProtocolElementType, expectedSubstitutions, matchContext.context)
      val elementResult: Boolean =
        subclassElementMembers.any { subclassElementMember: PyTypeMember? ->
          if (protocolMember.isWritable && !subclassElementMember!!.isWritable) {
            return@any false
          }
          if (protocolMember.isDeletable && !subclassElementMember!!.isDeletable) {
            return@any false
          }
          val isProtocolMemberClassVar = protocolMember.isClassVar
          val isSubclassMemberClassVar = subclassElementMember!!.isClassVar
          if (isSubclassMemberClassVar != isProtocolMemberClassVar) {
            return@any false
          }

          var subclassElementType = dropSelfIfNeeded(actual, subclassElementMember.type, matchContext.context)
          subclassElementType = substitute(subclassElementType, actualSubstitutions, matchContext.context)
          match(protocolElementType, subclassElementType, protocolContext).orElse(true)!!
        }

      if (!elementResult) {
        return false
      }
    }


    if (expected is PyCollectionType && expected.hasGenerics(matchContext.context)) {
      val concreteExpected: PyCollectionType = checkNotNull(
        substitute(expected, protocolContext.mySubstitutions, protocolContext.context) as PyCollectionType?
      )
      // This match is supposed to succeed since all protocol members were compatible.
      // Effectively, we're just copying substitutions for the terminal type parameters e.g.
      // extracting: T@func -> str
      // from: Iterable[T@func] <- Iterable[str]
      return matchGenericClassesParameterWise(expected, concreteExpected, matchContext)
    }

    return true
  }

  private fun match(expectedProtocol: PyClassType, actualModule: PyModuleType, matchContext: MatchContext): Boolean {
    val module = actualModule.module

    val moduleElements =
      (module.topLevelAttributes + module.topLevelFunctions)
        .asSequence()
        .filter { e: PsiNameIdentifierOwner? ->
          val name = (e as PyQualifiedNameOwner).name
          name != null && !isPrivate(name) && !isProtected(name)
        }
        .associateBy { it.name }

    val protocolElements =
      inspectProtocolSubclass(expectedProtocol, expectedProtocol, matchContext.context)

    if (protocolElements.size != moduleElements.size) return false

    val substitutions = collectTypeSubstitutions(expectedProtocol, matchContext.context)
    for (pair in protocolElements) {
      val pm = pair.first.element
      if (pm !is PsiNamedElement) {
        continue
      }
      val name = pm.name
      val moduleElement = moduleElements[name]
      if (moduleElement != null) {
        val expectedProtocolMemberType =
          substitute(
            dropSelfIfNeeded(expectedProtocol, pair.first.type, matchContext.context), substitutions,
            matchContext.context
          )
        val actualModuleElementType = matchContext.context.getType(moduleElement)
        if (!match(expectedProtocolMemberType, actualModuleElementType, matchContext.context)) {
          return false
        }
        continue
      }
      return false
    }
    return true
  }

  private fun dropSelfIfNeeded(
    classType: PyClassType,
    elementType: PyType?,
    context: TypeEvalContext,
  ): PyType? {
    if (elementType is PyCallableType) {
      if (PyUtil.isInitOrNewMethod(elementType.callable) || !classType.isDefinition) {
        return elementType.dropSelf(context)
      }
    }
    return elementType
  }

  // https://typing.python.org/en/latest/spec/tuples.html#type-compatibility-rules
  private fun match(
    expected: PyTupleType,
    actual: PyTupleType,
    context: MatchContext,
  ): Optional<Boolean> {
    if (actual.isHomogeneous) {
      // The type tuple[Any, ...] is consistent with any tuple
      val elementType = actual.iteratedItemType
      if (elementType == null) {
        return Optional.of(true)
      }
    }
    // TODO Delegate to UnpackedTupleType here, once it's introduced
    if (!expected.isHomogeneous && !actual.isHomogeneous) {
      val isUnbound =
        { type: PyType? -> (type is PyUnpackedTupleType && type.isUnbound) || type is PyTypeVarTupleType }
      val expectedContainsUnboundTypes =
        expected.elementTypes.any(isUnbound)
      val actualContainsUnboundTypes =
        actual.elementTypes.any(isUnbound)

      if (actualContainsUnboundTypes && !expectedContainsUnboundTypes) {
        return Optional.of(false)
      }

      return Optional.of(matchTypeParameters(expected.elementTypes, actual.elementTypes, context))
    }

    if (expected.isHomogeneous && !actual.isHomogeneous) {
      val expectedElementType = expected.iteratedItemType
      return Optional.of(
        actual.iteratedItemType.toStream()
          .allMatch { type: PyType? -> match(expectedElementType, type, context).orElse(true)!! }
      )
    }

    if (!expected.isHomogeneous && actual.isHomogeneous) {
      return Optional.of(false)
    }

    return match(expected.iteratedItemType, actual.iteratedItemType, context)
  }

  private fun match(expected: PyCollectionType, actual: PyClassType, context: MatchContext): Boolean {
    if (actual is PyTupleType) {
      return match(expected, actual, context)
    }

    val superClass = expected.pyClass
    val subClass = actual.pyClass

    return matchClasses(superClass, subClass, context.context) && matchGenerics(expected, actual, context)
  }

  private fun match(expected: PyCollectionType, actual: PyTupleType, context: MatchContext): Boolean {
    if (!matchClasses(expected.pyClass, actual.pyClass, context.context)) {
      return false
    }

    val superElementType = expected.iteratedItemType
    val subElementType = actual.iteratedItemType

    return match(superElementType, subElementType, context).orElse(true)!!
  }

  private fun match(expected: PyStructuralType, actual: PyType, context: TypeEvalContext): Boolean {
    if (actual is PyStructuralType) {
      return match(expected, actual)
    }
    if (actual is PyClassType) {
      return match(expected, actual, context)
    }
    if (actual is PyModuleType) {
      val module = actual.module
      if (module.languageLevel.isAtLeast(LanguageLevel.PYTHON37) && definesGetAttr(module, context)) {
        return true
      }
    }

    val resolveContext = PyResolveContext.defaultContext(context)
    return !expected.attributeNames.any { attribute: String? ->
      ContainerUtil
        .isEmpty(actual.resolveMember(attribute!!, null, AccessDirection.READ, resolveContext))
    }
  }

  private fun match(expected: PyStructuralType, actual: PyStructuralType): Boolean {
    if (expected.isInferredFromUsages) {
      return true
    }
    return expected.attributeNames.containsAll(actual.attributeNames)
  }

  private fun match(expected: PyStructuralType, actual: PyClassType, context: TypeEvalContext): Boolean {
    if (overridesGetAttr(actual.pyClass, context)) {
      return true
    }
    val actualAttributes = actual.getMemberNames(true, context)
    return actualAttributes.containsAll(expected.attributeNames)
  }

  private fun match(
    expectedParametersType: PyCallableParameterListType,
    actualParametersType: PyCallableParameterListType,
    matchContext: MatchContext,
  ): Boolean {
    val context = matchContext.context
    val expectedParameters = expectedParametersType.parameters
    val actualParameters = actualParametersType.parameters

    var startIndex = 0
    if (!expectedParameters.isEmpty() && !actualParameters.isEmpty()) {
      val firstExpectedParam: PyCallableParameter = expectedParameters.first()
      val firstActualParam: PyCallableParameter = actualParameters.first()
      if (firstExpectedParam.isSelf && firstActualParam.isSelf) {
        if (!match(firstExpectedParam.getType(context), firstActualParam.getType(context), matchContext).orElse(true)!!) {
          return false
        }
        startIndex = 1
      }
    }

    val mapping = mapCallableParameters(
      ContainerUtil.subList(expectedParameters, startIndex),
      ContainerUtil.subList(actualParameters, startIndex),
      context
    )
    if (mapping == null) {
      return false
    }
    // actual callable type could accept more general parameter type
    for (pair in mapping.mappedTypes) {
      val matched = if (matchContext.reverseSubstitutions().reversedSubstitutions)
        match(pair.getSecond(), pair.getFirst(), matchContext.reverseSubstitutions())
      else
        match(pair.getFirst(), pair.getSecond(), matchContext.reverseSubstitutions())
      if (!matched.orElse(true)!!) {
        return false
      }
    }
    return true
  }

  private fun match(
    expected: PyCallableType,
    actual: PyCallableType,
    matchContext: MatchContext,
  ): Optional<Boolean> {
    if (actual is PyFunctionType && expected is PyClassType && PyNames.FUNCTION == expected.name
        && expected == getInstance(actual.callable).getObjectType(PyNames.FUNCTION)
    ) {
      return Optional.of(true)
    }

    val context = matchContext.context

    if (expected is PyClassLikeType) {
      if (isCallableProtocol(expected, context)) {
        val protocolMembers = expected.getProtocolMembers(context)
        if (protocolMembers.size != 1 || protocolMembers[0].name != "__call__") {
          return Optional.of(false)
        }
      }
      else {
        return if (PyTypingTypeProvider.CALLABLE == expected.classQName)
          Optional.of(actual.isCallable)
        else
          Optional.empty()
      }
    }

    if (expected is PyClassType && actual is PyFunctionType && isCallableProtocol(expected, context)) {
      val expectedOverloads = expected.findMember(PyNames.CALL, PyResolveContext.defaultContext(context)).map { it.type }
      val actualCallable = actual.callable
      val actualOverloads = if (actualCallable is PyFunction) {
        PyiUtil.getOverloads(actualCallable, context)
          .map { context.getType(it) }
          .takeIf { it.isNotEmpty() } ?: listOf(actual)
      }
      else {
        listOf(actual)
      }
      return Optional.of(expectedOverloads.all { expectedCall ->
        actualOverloads.any { actualCall ->
          match(dropSelfIfNeeded(expected, expectedCall, context), actualCall, matchContext).orElse(true)
        }
      })
    }

    if (expected.isCallable && actual.isCallable) {
      val expectedParametersType = expected.getParametersType(context)
      val actualParametersType = actual.getParametersType(context)

      if (expectedParametersType != null && actualParametersType != null) {
        if (!match(expectedParametersType, actualParametersType, matchContext).orElse(true)!!) {
          return Optional.of(false)
        }
      }
      if (!match(expected.getReturnType(context), getActualReturnType(actual, context), matchContext).orElse(true)!!) {
        return Optional.of(false)
      }
      return Optional.of(true)
    }
    return Optional.empty()
  }

  private fun getActualReturnType(actual: PyCallableType, context: TypeEvalContext): PyType? {
    if (actual is PyCallableTypeImpl) {
      return actual.getReturnType(context)
    }
    val callable = actual.callable
    if (callable is PyFunction) {
      return PyUtil.getReturnTypeToAnalyzeAsCallType(callable, context)
    }
    return actual.getReturnType(context)
  }

  private fun match(expected: List<PyType?>, actual: List<PyType?>, matchContext: MatchContext): Boolean {
    if (expected.size != actual.size) return false
    for (i in expected.indices) {
      if (!match(expected[i], actual[i], matchContext).orElse(true)!!) return false
    }
    return true
  }

  private fun isCallableProtocol(expected: PyClassLikeType, context: TypeEvalContext): Boolean {
    return expected.isProtocol(context) && PyNames.CALL in expected.getMemberNames(false, context)
  }

  private fun widenUnionOfTuplesToTupleOfUnions(unionType: PyUnionType, elementCount: Int): PyTupleType? {
    val consistsOfSameSizeTuples =
      unionType.members.all { member -> member is PyTupleType && !member.isHomogeneous && elementCount == member.elementCount }
    if (!consistsOfSameSizeTuples) {
      return null
    }
    val newTupleElements = List(elementCount) { index ->
      unionType.members.asSequence()
        .filterIsInstance<PyTupleType>()
        .map { it.getElementType(index) }
        .toList()
        .let(PyUnionType::union)
    }
    val tupleClass = (unionType.members.firstOrNull() as PyTupleType).pyClass
    return PyTupleType(tupleClass, newTupleElements, false)
  }

  private fun matchGenerics(expected: PyCollectionType, actual: PyClassType, context: MatchContext): Boolean {
    if (actual is PyCollectionType && expected.pyClass == actual.pyClass) {
      return matchGenericClassesParameterWise(expected, actual, context)
    }

    val expectedGenericType = findGenericDefinitionType(expected.pyClass, context.context)
    if (expectedGenericType != null) {
      val actualSubstitutions = collectTypeSubstitutions(actual, context.context)
      val concreteExpected: PyCollectionType =
        checkNotNull(substitute(expectedGenericType, actualSubstitutions, context.context) as PyCollectionType?)
      return matchGenericClassesParameterWise(expected, concreteExpected, context)
    }
    return true
  }

  // TODO Make it a part of PyClassType interface
  /**
   * Extract all type substitutions from a generic class instance. For instance, for the type of `x`
   *
   * <pre>`class Base[T1, T2]: ... class Sub[T3](Base[int, T3]): ... x: Sub[str] `</pre>
   *
   *
   * we should extract
   *
   * <pre>`T1@Base -> int T2@Base -> Sub@T3 Sub@T3 -> str `</pre>
   */
  fun collectTypeSubstitutions(classType: PyClassType, context: TypeEvalContext): GenericSubstitutions {
    val result = GenericSubstitutions()
    // Collect substitutions from ancestor classes. In the example above these are: T1@Base -> int and T2@Base -> Sub@T3
    for (provider in PyTypeProvider.EP_NAME.extensionList) {
      val substitutionsFromClassDefinition = provider.getGenericSubstitutions(classType.pyClass, context)
      for ((key, value) in substitutionsFromClassDefinition) {
        when (key) {
          is PyTypeVarType -> result.putTypeVar(key, Ref(value), KeyImpl)
          is PyTypeVarTupleType -> result.putTypeVarTuple(key, value as PyPositionalVariadicType, KeyImpl)
          is PyParamSpecType -> if (value is PyCallableParameterVariadicType)
            result.putParamSpec(key, value, KeyImpl)
        }
      }
      // Collect own type parameters. In the example above these are: Sub@T3 -> str
      val genericDefinitionType = provider.getGenericType(classType.pyClass, context) as? PyCollectionType
      // TODO Re-use PyTypeParameterMapping, at the moment C[*Ts] <- C leads to *Ts being mapped to *tuple[], which breaks inference later on
      if (genericDefinitionType != null) {
        val definitionTypeParameters = genericDefinitionType.elementTypes

        // Inside method bodies, where Self type can appear, we map class' own type parameters to themseleves,
        // not to consider them unbound. For instance, here
        //
        // class B[T]:...
        // class C[T2](B[T2]):
        //     attr: T
        //     def m(self):
        //         self.attr
        //
        // when collecting substitutions for `self` in `m`, we will map T@B -> T2@C, but T2@C -> T2@C.
        //
        // See Py3TypeTest.testApplyingSuperSubstitutionToBoundedGenericClass
        // and Py3TypeTest#testApplyingSuperSubstitutionToGenericClass
        when (classType) {
          is PySelfType -> {
            mapTypeParametersToSubstitutions(
              result, definitionTypeParameters, definitionTypeParameters,
              PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY
            )
          }
          !is PyCollectionType -> {
            for (typeParameter in definitionTypeParameters) {
              when (typeParameter) {
                is PyTypeVarType -> result.putTypeVar(typeParameter, null, KeyImpl)
                is PyTypeVarTupleType -> result.putTypeVarTuple(typeParameter, null, KeyImpl)
                is PyParamSpecType -> result.putParamSpec(typeParameter, null, KeyImpl)
              }
            }
          }
          else -> {
            var elementTypes = classType.elementTypes
            if (classType is PyTupleType && !classType.isHomogeneous) {
              val unionTypes = classType.elementTypes.flatMap { et -> if (et is PyUnpackedTupleType) et.elementTypes else listOf(et) }
              elementTypes = listOf(PyUnionType.union(unionTypes))
            }
            mapTypeParametersToSubstitutions(
              result, definitionTypeParameters, elementTypes,
              PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY
            )
          }
        }
      }
      if (result.typeVars.isNotEmpty() || result.typeVarTuples.isNotEmpty() || result.paramSpecs.isNotEmpty()) {
        return result
      }
    }
    return result
  }

  @JvmStatic
  @ApiStatus.Internal
  fun findGenericDefinitionType(pyClass: PyClass, context: TypeEvalContext): PyCollectionType? {
    for (provider in PyTypeProvider.EP_NAME.extensionList) {
      val definitionType = provider.getGenericType(pyClass, context)
      if (definitionType is PyCollectionType) {
        return definitionType
      }
    }
    return null
  }

  private fun matchGenericClassesParameterWise(
    expected: PyCollectionType,
    actual: PyCollectionType,
    context: MatchContext,
  ): Boolean {
    if (expected == actual) {
      return true
    }
    if (expected.pyClass != actual.pyClass) {
      return false
    }
    val expectedElementTypes = expected.elementTypes
    val actualElementTypes = actual.elementTypes
    if (context.reversedSubstitutions) {
      return matchTypeParameters(actualElementTypes, expectedElementTypes, context.resetSubstitutions())
    }
    else {
      return matchTypeParameters(expectedElementTypes, actualElementTypes, context)
    }
  }

  private fun matchTypeParameters(
    expectedTypeParameters: List<PyType?>,
    actualTypeParameters: List<PyType?>,
    context: MatchContext,
  ): Boolean {
    val mapping =
      PyTypeParameterMapping.mapByShape(expectedTypeParameters, actualTypeParameters, PyTypeParameterMapping.Option.USE_DEFAULTS)
    if (mapping == null) {
      return false
    }
    for (pair in mapping.mappedTypes) {
      val matched = if (context.reversedSubstitutions)
        match(pair.getSecond(), pair.getFirst(), context)
      else
        match(pair.getFirst(), pair.getSecond(), context)
      if (!matched.orElse(true)!!) {
        return false
      }
    }
    return true
  }

  private fun matchNumericTypes(expected: PyType?, actual: PyType?): Boolean {
    if (expected is PyClassType && actual is PyClassType) {
      val superName = expected.pyClass.name
      val subName = actual.pyClass.name
      val subIsBool = "bool" == subName
      val subIsInt = PyNames.TYPE_INT == subName
      val subIsLong = PyNames.TYPE_LONG == subName
      val subIsFloat = "float" == subName
      val subIsComplex = "complex" == subName
      if (superName == null || subName == null ||
          superName == subName ||
          (PyNames.TYPE_INT == superName && subIsBool) ||
          ((PyNames.TYPE_LONG == superName || PyNames.ABC_INTEGRAL == superName) && (subIsBool || subIsInt)) ||
          (("float" == superName || PyNames.ABC_REAL == superName) && (subIsBool || subIsInt || subIsLong)) ||
          (("complex" == superName || PyNames.ABC_COMPLEX == superName) && (subIsBool || subIsInt || subIsLong || subIsFloat)) ||
          (PyNames.ABC_NUMBER == superName && (subIsBool || subIsInt || subIsLong || subIsFloat || subIsComplex))
      ) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun isUnknown(type: PyType?, context: TypeEvalContext): Boolean {
    return isUnknown(type, true, context)
  }

  @JvmStatic
  fun isUnknown(type: PyType?, genericsAreUnknown: Boolean, context: TypeEvalContext): Boolean {
    // TODO Don't consider other type parameters unknown (PY-85653)
    // Since Self is always bound, don't consider it unknown, e.g. in Py3TypeCheckerInspectionTest.testSelfAssignedToOtherTypeBad
    if (type == null || (genericsAreUnknown && type is PyTypeParameterType && (type !is PySelfType))) {
      return true
    }
    return when (type) {
      is PyUnionType -> {
        if (!PyUnionType.isStrictSemanticsEnabled()) {
          type.members.any { member: PyType? -> isUnknown(member, genericsAreUnknown, context) }
        }
        else type.members.all { member: PyType? -> isUnknown(member, genericsAreUnknown, context) }
      }
      is PyUnsafeUnionType -> {
        type.members.any { member: PyType? -> isUnknown(member, genericsAreUnknown, context) }
      }
      is PyIntersectionType -> {
        type.members.any { member: PyType? -> isUnknown(member, genericsAreUnknown, context) }
      }
      else -> false
    }
  }

  @JvmStatic
  fun getSubstitutionsWithUnresolvedReturnGenerics(
    parameters: Collection<PyCallableParameter>,
    returnType: PyType?,
    substitutions: GenericSubstitutions?,
    context: TypeEvalContext,
  ): GenericSubstitutions {
    val parameterTypes = parameters.map { it.getArgumentType(context) }
    return substituteUnboundTypeVarsWithDefaultOrAny(returnType, parameterTypes, substitutions, context)
  }

  private fun substituteUnboundTypeVarsWithDefaultOrAny(
    targetType: PyType?,
    typeParameterSources: List<PyType?>,
    substitutions: GenericSubstitutions?,
    context: TypeEvalContext,
  ): GenericSubstitutions {
    val resolvableTypeParams = GenericsImpl()
    for (parameterType in typeParameterSources) {
      collectGenerics(parameterType, context, resolvableTypeParams)
    }

    val existingSubstitutions = substitutions ?: GenericSubstitutions()
    val requiredTypeParams = targetType.collectGenerics(context)
    // TODO Handle unmatched TypeVarTuples here as well
    if (requiredTypeParams.typeVars.isEmpty() && requiredTypeParams.paramSpecs.isEmpty()) {
      return existingSubstitutions
    }

    for (typeVar in requiredTypeParams.typeVars) {
      val isResolvable = resolvableTypeParams.typeVars.contains(typeVar) ||
                         resolvableTypeParams.typeVars.contains(typeVar.invert())
      val isAlreadyBound = existingSubstitutions.typeVars.containsKey(typeVar) ||
                           existingSubstitutions.typeVars.containsKey(typeVar.invert())
      if (isResolvable && !isAlreadyBound) {
        if (typeVar.defaultType != null) {
          @Suppress("UNCHECKED_CAST")
          existingSubstitutions.putTypeVar(typeVar, typeVar.defaultType as Ref<PyType?>?, KeyImpl)
        }
        else {
          existingSubstitutions.putTypeVar(typeVar, Ref.create<PyType?>(null), KeyImpl)
        }
      }
    }
    for (paramSpecType in requiredTypeParams.paramSpecs) {
      val isResolvable = resolvableTypeParams.paramSpecs.contains(paramSpecType)
      val isAlreadyBound = existingSubstitutions.paramSpecs.containsKey(paramSpecType)
      if (isResolvable && !isAlreadyBound) {
        if (paramSpecType.defaultType != null) {
          existingSubstitutions.putParamSpec(paramSpecType, Ref.deref<PyCallableParameterVariadicType?>(paramSpecType.defaultType), KeyImpl)
        }
        else {
          existingSubstitutions.putParamSpec(paramSpecType, PyCallableParameterListTypeImpl(
            listOf(PyCallableParameterImpl.positionalNonPsi("args", null),
                              PyCallableParameterImpl.keywordNonPsi("kwargs", null))), KeyImpl
          )
        }
      }
    }
    return existingSubstitutions
  }


  private fun <T : PyInstantiableType<T>> PyInstantiableType<T>.invert(): T {
    return if (isDefinition) toInstance() else toClass()
  }

  @JvmStatic
  fun PyType?.hasGenerics(context: TypeEvalContext): Boolean {
    return !this.collectGenerics(context).isEmpty
  }

  @JvmStatic
  @ApiStatus.Internal
  fun PyType?.collectGenerics(context: TypeEvalContext): Generics {
    val result = GenericsImpl()
    collectGenerics(this, context, result)
    return result
  }

  private fun collectGenerics(
    type: PyType?,
    context: TypeEvalContext,
    generics: GenericsImpl,
  ) {
    PyRecursiveTypeVisitor.traverse(type, context, object : PyTypeTraverser() {
      override fun visitPyTypeVarType(typeVarType: PyTypeVarType): PyRecursiveTypeVisitor.Traversal? {
        generics.typeVars.add(typeVarType)
        return super.visitPyTypeVarType(typeVarType)
      }

      override fun visitPyTypeVarTupleType(typeVarTupleType: PyTypeVarTupleType): PyRecursiveTypeVisitor.Traversal? {
        generics.typeVarTuples.add(typeVarTupleType)
        return super.visitPyTypeVarTupleType(typeVarTupleType)
      }

      override fun visitPySelfType(selfType: PySelfType): PyRecursiveTypeVisitor.Traversal {
        generics.self = selfType
        return super.visitPySelfType(selfType)
      }

      override fun visitPyParamSpecType(paramSpecType: PyParamSpecType): PyRecursiveTypeVisitor.Traversal {
        generics.paramSpecs.add(paramSpecType)
        return super.visitPyParamSpecType(paramSpecType)
      }

      override fun visitPyTypeParameterType(typeParameterType: PyTypeParameterType): PyRecursiveTypeVisitor.Traversal {
        generics.allTypeParameters.add(typeParameterType)
        return PyRecursiveTypeVisitor.Traversal.PRUNE
      }
    })
  }

  @JvmStatic
  fun substitute(
    type: PyType?,
    substitutions: GenericSubstitutions,
    context: TypeEvalContext,
  ): PyType? {
    return PyCloningTypeVisitor.clone(type, object : PyCloningTypeVisitor(context) {
      fun flattenUnpackedTuple(type: PyType?): List<PyType?> {
        return if (type is PyUnpackedTupleType && !type.isUnbound) {
          type.elementTypes
        }
        else listOf(type)
      }

      override fun visitPyUnpackedTupleType(unpackedTupleType: PyUnpackedTupleType): PyType {
        return PyUnpackedTupleTypeImpl(
          unpackedTupleType.elementTypes.flatMap {
            flattenUnpackedTuple(clone(it))
          },
          unpackedTupleType.isUnbound)
      }

      override fun visitPyTypeVarTupleType(typeVarTupleType: PyTypeVarTupleType): PyType? {
        if (typeVarTupleType !in substitutions.typeVarTuples) {
          return typeVarTupleType
        }
        val substitution = substitutions.typeVarTuples[typeVarTupleType]
        if (typeVarTupleType != substitution && substitution.hasGenerics(context)) {
          return clone(substitution)
        }
        // TODO This should happen in getSubstitutionsWithUnresolvedReturnGenerics, but it won't work for inherited constructors
        //   as in testVariadicGenericCheckTypeAliasesRedundantParameter, investigate why
        // Replace unknown TypeVarTuples by *tuple[Any, ...] instead of plain Any
        return substitution ?: PyUnpackedTupleTypeImpl.UNSPECIFIED
      }

      override fun visitPyTypeVarType(typeVarType: PyTypeVarType): PyType? {
        // Both mappings of kind {T: T2} (and no mapping for T2) and {T: T} mean the substitution process should stop for T.
        // The first one occurs in the situations like
        //
        // def f(x: T1) -> T1: ...
        // def g(y: T2): f(y)
        //
        // when we're inferring the return type of "g".
        // The second is a plug for type hints like
        //
        // def g() -> Callable[[T], T]
        //
        // where we want to prevent replacing T with Any, even though it cannot be inferred at a call site.
        if (typeVarType !in substitutions.typeVars && typeVarType.invert() !in substitutions.typeVars) {
          val substitution = substitutions.typeVars.keys.firstOrNull { typeVarType2 ->
            typeVarType2.declarationElement != null && (typeVarType.scopeOwner == null || (typeVarType2.scopeOwner === typeVarType.scopeOwner))
            && typeVarType2.declarationElement == typeVarType.declarationElement
          }

          if (substitution != null) {
            return clone(substitution)
          }
          return typeVarType
        }
        val substitutionRef = substitutions.typeVars[typeVarType]
        var substitution = Ref.deref(substitutionRef)
        if (substitutionRef == null) {
          val invertedTypeVar: PyInstantiableType<*> = typeVarType.invert()
          val invertedSubstitution = Ref.deref(substitutions.typeVars[invertedTypeVar]) as? PyInstantiableType<*>
          if (invertedSubstitution != null) {
            substitution = invertedSubstitution.invert()
          }
        }
        if (substitution is PyTypeVarType) {
          val sameScopeSubstitution = substitutions.typeVars.keys.firstOrNull { typeVarType2 ->
            (typeVarType2 != substitution) && typeVarType2.declarationElement != null && typeVarType2.declarationElement == substitution.declarationElement
          }

          if (sameScopeSubstitution != null && substitution.defaultType != null) {
            return clone(sameScopeSubstitution)
          }
        }
        // TODO remove !typeVar.equals(substitution) part, it's necessary due to the logic in unifyReceiverWithParamSpecs
        if ((typeVarType != substitution) && (substitution !is PySelfType) && substitution.hasGenerics(context)) {
          return clone(substitution)
        }
        return substitution
      }

      override fun visitPyParamSpecType(paramSpecType: PyParamSpecType): PyType? {
        if (paramSpecType !in substitutions.paramSpecs) {
          val sameScopeSubstitution = substitutions.paramSpecs.keys.firstOrNull { typeVarType ->
            typeVarType.declarationElement != null && typeVarType.declarationElement == paramSpecType.declarationElement
          }

          if (sameScopeSubstitution != null) {
            return clone(sameScopeSubstitution)
          }
          return paramSpecType
        }
        val substitution = substitutions.paramSpecs[paramSpecType]
        if (substitution != null && (substitution != paramSpecType) && substitution.hasGenerics(context)) {
          return clone(substitution)
        }
        // TODO For ParamSpecs, replace Any with (*args: Any, **kwargs: Any) as it's a logical "wildcard" for this kind of type parameter
        return substitution
      }

      override fun visitPySelfType(selfType: PySelfType): PyType? {
        val qualifierType = substitutions.qualifierType ?: return selfType
        val selfScopeClassType = selfType.scopeClassType
        // TODO change unification for calls on union types
        // so that in the following
        //
        // class A:
        //     def a_method(self) -> Self:
        //         ...
        // x: A | B
        //
        // A.a_method # type: Callable[[A], A]
        // B wasn't considered as the receiver type in the first place, instead of filtering it out during substitution
        // (see PyTypingTest.testMatchSelfUnionType)
        return qualifierType.toStream()
          .map<PyType?> { qType: PyType? ->
            if (qType is PyInstantiableType<*>) {
              return@map if (selfScopeClassType.isDefinition) qType.toClass() else qType.toInstance()
            }
            qType
          }
          .filter { normalizedQType: PyType? -> match(selfScopeClassType, normalizedQType, context) }
          .collect(PyTypeUtil.toUnion(qualifierType))
      }

      override fun visitPyGenericType(genericType: PyCollectionType): PyType {
        return PyCollectionTypeImpl(
          genericType.pyClass, genericType.isDefinition,
          genericType.elementTypes.flatMap {
            flattenUnpackedTuple(clone(it))
          }
        )
      }

      override fun visitPyTupleType(tupleType: PyTupleType): PyType {
        val tupleClass = tupleType.pyClass
        val oldElementTypes = if (tupleType.isHomogeneous) listOf(tupleType.iteratedItemType)
        else
          tupleType.elementTypes
        return PyTupleType(
          tupleClass,
          oldElementTypes.flatMap {
            flattenUnpackedTuple(
              clone(it))
          },
          tupleType.isHomogeneous,
        )
      }

      override fun visitPyFunctionType(functionType: PyFunctionType): PyType? {
        // TODO legacy behavior
        if (functionType.hasGenerics(context)) {
          return super.visitPyFunctionType(functionType)
        }
        return functionType
      }

      override fun visitPyCallableType(callableType: PyCallableType): PyType {
        val substitutedParams = clone<PyCallableParameterVariadicType?>(callableType.getParametersType(context))
        return PyCallableTypeImpl(
          substitutedParams,
          clone(callableType.getReturnType(context)),
          callableType.callable,
          callableType.modifier,
          callableType.implicitOffset,
        )
      }

      override fun visitPyCallableParameterListType(callableParameterListType: PyCallableParameterListType): PyType {
        val parameters = callableParameterListType.parameters

        val isSelfArgsKwargs = ParamHelper.isSelfArgsKwargsSignature(parameters)
        val isArgsKwargs = ParamHelper.isArgsKwargsSignature(parameters)
        // substitutes (*args: P.args, **kwargs: P.kwargs) with actual signature
        if (isSelfArgsKwargs || isArgsKwargs) {
          val kwargsType: PyType? = parameters.last().getType(context)
          val substituted = substitutions.paramSpecs[kwargsType]

          if (substituted is PyCallableParameterListType) {
            var newParams = substituted.parameters
            if (isSelfArgsKwargs) {
              newParams = ContainerUtil.prepend(newParams, parameters.first())
            }
            return PyCallableParameterListTypeImpl(newParams)
          }
        }

        val substitutedParams = parameters
          .associateWith { it.getType(context) }
          .flatMap { (param, _) ->
            val paramPsi = param.parameter
            flattenUnpackedTuple(clone(param.getType(context)))
              .map { paramSubType ->
                if (paramPsi != null) PyCallableParameterImpl.psi(
                  paramPsi,
                  paramSubType
                )
                else PyCallableParameterImpl.nonPsi(param.name, paramSubType, param.defaultValue)
              }
          }

        return PyCallableParameterListTypeImpl(substitutedParams)
      }

      override fun visitPyConcatenateType(concatenateType: PyConcatenateType): PyCallableParameterVariadicType? {
        val firstParamTypeSubs = concatenateType.firstTypes.flatMap {
          flattenUnpackedTuple(clone(it))
        }
        val paramSpecSubs = clone<PyCallableParameterVariadicType>(concatenateType.paramSpec)

        return when (paramSpecSubs) {
          is PyCallableParameterListType -> {
            PyCallableParameterListTypeImpl(
              firstParamTypeSubs.map { PyCallableParameterImpl.nonPsi(it) } + paramSpecSubs.parameters
            )
          }
          is PyParamSpecType -> PyConcatenateType(firstParamTypeSubs, paramSpecSubs)
          is PyConcatenateType -> {
            PyConcatenateType(
              firstParamTypeSubs + paramSpecSubs.firstTypes,
              paramSpecSubs.paramSpec
            )
          }
          else -> null
        }
      }
    })
  }

  @JvmStatic
  fun unifyGenericCall(
    receiver: PyExpression?,
    arguments: Map<PyExpression, PyCallableParameter>,
    context: TypeEvalContext,
  ): GenericSubstitutions? {
    // UnifyReceiver retains the information about type parameters of ancestor generic classes,
    // which might be necessary if a method being called is defined in one them, not in the actual
    // class of the receiver. In theory, it could be replaced by matching the type of self parameter
    // with the receiver uniformly with the rest of the arguments.
    val substitutions = unifyReceiver(receiver, context)
    for ((key, paramWrapper) in PyCallExpressionHelper.getRegularMappedParameters(arguments).entries) {
      val expectedType = paramWrapper.getArgumentType(context)
      val promotedToLiteral = PyLiteralType.promoteToLiteral(key, expectedType, context, substitutions)
      val actualType = promotedToLiteral ?: context.getType(key)
      // Matching with the type of "self" is necessary in particular for choosing the most specific overloads, e.g.
      // LiteralString-specific methods of str, or for instantiating the type parameters of the containing class
      // when it's not possible to infer them by other means, e.g. as in the following overload of dict[_KT, _VT].__init__:
      // def __init__(self: dict[str, _VT], **kwargs: _VT) -> None: ...
      val matchedByTypes = matchParameterArgumentTypes(paramWrapper, expectedType, actualType, substitutions, context)
      if (!matchedByTypes) {
        return null
      }
    }
    if (!matchContainer(
        PyCallExpressionHelper.getMappedPositionalContainer(arguments), PyCallExpressionHelper.getArgumentsMappedToPositionalContainer(arguments),
        substitutions, context
      )
    ) {
      return null
    }
    if (!matchContainer(
        PyCallExpressionHelper.getMappedKeywordContainer(arguments), PyCallExpressionHelper.getArgumentsMappedToKeywordContainer(arguments),
        substitutions, context
      )
    ) {
      return null
    }
    return substitutions
  }

  @JvmStatic
  fun unifyGenericCallOnArgumentTypes(
    receiverType: PyType?,
    arguments: Map<Ref<PyType?>?, PyCallableParameter>,
    context: TypeEvalContext,
  ): GenericSubstitutions? {
    val substitutions = unifyReceiver(receiverType, context)
    for ((key, paramWrapper) in PyCallExpressionHelper.getRegularMappedParameters(arguments)) {
      val expectedType = paramWrapper.getArgumentType(context)
      val actualType = Ref.deref(key)
      val matchedByTypes = matchParameterArgumentTypes(paramWrapper, expectedType, actualType, substitutions, context)
      if (!matchedByTypes) {
        return null
      }
    }
    if (!matchContainerByType(PyCallExpressionHelper.getMappedPositionalContainer(arguments),
                              PyCallExpressionHelper.getArgumentsMappedToPositionalContainer(arguments).map(Ref<*>::deref),
                              substitutions, context)) {
      return null
    }
    if (!matchContainerByType(PyCallExpressionHelper.getMappedKeywordContainer(arguments),
                              PyCallExpressionHelper.getArgumentsMappedToKeywordContainer(arguments).map(Ref<*>::deref),
                              substitutions, context)) {
      return null
    }
    return substitutions
  }

  private fun processSelfParameter(
    paramWrapper: PyCallableParameter,
    expectedType: PyType?,
    actualType: PyType?,
    substitutions: GenericSubstitutions,
    context: TypeEvalContext,
  ): PyType? {
    // TODO find out a better way to pass the corresponding function inside
    var actualType = actualType
    val param = paramWrapper.parameter
    val function: PyFunction = ScopeUtil.getScopeOwner(param) as PyFunction
    if (function.modifier == PyAstFunction.Modifier.CLASSMETHOD) {
      actualType = actualType.toStream()
        .select(PyClassLikeType::class.java)
        .map { obj: PyClassLikeType? -> obj!!.toClass() }
        .select(PyType::class.java)
        .foldLeft { type1: PyType?, type2: PyType? -> PyUnionType.union(type1, type2) }
        .orElse(actualType)
    }
    else if (PyUtil.isInitMethod(function)) {
      actualType = actualType.toStream()
        .select(PyInstantiableType::class.java)
        .map { obj -> obj!!.toInstance() }
        .select(PyType::class.java)
        .foldLeft { type1, type2 -> PyUnionType.union(type1, type2) }
        .orElse(actualType)
    }
    if (PyUnionType.isStrictSemanticsEnabled()) {
      val pyClass: PyClass = checkNotNull(function.containingClass)
      val classType: PyClassLikeType = context.getType(pyClass) as PyClassLikeType
      val superType: PyClassLikeType =
        (if (function.modifier == PyAstFunction.Modifier.CLASSMETHOD || PyUtil.isNewMethod(function)) classType else classType.toInstance())
      // In a union receiver type, leave only members that actually have this function
      // TODO how does it work with qualified calls, e.g. SomeClass.method(receiver, arg1, arg2)
      // TODO how does it work with @classmethods?
      actualType = actualType.toStream()
        .filter { type: PyType? -> match(superType, type, context) }
        .collect(PyTypeUtil.toUnion(actualType))
    }

    val containingClass: PyClass = checkNotNull(function.containingClass)
    var genericClass: PyType? = findGenericDefinitionType(containingClass, context)
    if (genericClass is PyInstantiableType<*> &&
        (PyUtil.isNewMethod(function) || function.modifier == PyAstFunction.Modifier.CLASSMETHOD)
    ) {
      genericClass = genericClass.toClass()
    }
    if (genericClass != null && !match(genericClass, expectedType, context, substitutions)) {
      return null
    }
    return actualType
  }

  private fun matchParameterArgumentTypes(
    paramWrapper: PyCallableParameter,
    expectedType: PyType?,
    actualType: PyType?,
    substitutions: GenericSubstitutions,
    context: TypeEvalContext,
  ): Boolean {
    var expectedType = expectedType
    var actualType = actualType
    if (paramWrapper.isSelf) {
      if (expectedType is PySelfType) {
        val genericType = findGenericDefinitionType(expectedType.scopeClassType.pyClass, context)
        expectedType = if (genericType != null) {
          if (expectedType.isDefinition) genericType.toClass() else genericType
        }
        else expectedType.scopeClassType
      }
      actualType = processSelfParameter(paramWrapper, expectedType, actualType, substitutions, context) ?: return false
    }
    return match(expectedType, actualType, context, substitutions)
  }

  private fun matchContainer(
    container: PyCallableParameter?, arguments: List<PyExpression>,
    substitutions: GenericSubstitutions, context: TypeEvalContext,
  ): Boolean {
    if (container == null) {
      return true
    }
    val actualArgumentTypes = arguments.map { context.getType(it) }
    return matchContainerByType(container, actualArgumentTypes, substitutions, context)
  }

  private fun matchContainerByType(
    container: PyCallableParameter?, actualArgumentTypes: List<PyType?>,
    substitutions: GenericSubstitutions, context: TypeEvalContext,
  ): Boolean {
    if (container == null) {
      return true
    }
    val expectedArgumentType = container.getArgumentType(context)
    if (container.isPositionalContainer && expectedArgumentType is PyPositionalVariadicType) {
      return match(
        expectedArgumentType, PyUnpackedTupleTypeImpl.create(actualArgumentTypes),
        MatchContext(context, substitutions, false)
      )
    }
    return match(expectedArgumentType, PyUnionType.union(actualArgumentTypes), context, substitutions)
  }

  @JvmStatic
  fun unifyReceiver(receiver: PyExpression?, context: TypeEvalContext): GenericSubstitutions {
    if (receiver != null) {
      val receiverType = context.getType(receiver)
      return unifyReceiver(receiverType, context)
    }
    return GenericSubstitutions()
  }

  @JvmStatic
  fun unifyReceiver(receiverType: PyType?, context: TypeEvalContext): GenericSubstitutions {
    // Collect generic params of object type
    val substitutions = GenericSubstitutions()
    if (receiverType != null) {
      // TODO properly handle union types here
      if (receiverType is PyClassType) {
        substitutions.qualifierType = receiverType.toInstance()
      }
      else {
        substitutions.qualifierType = receiverType
      }
      receiverType.toStream()
        .select(PyClassType::class.java)
        .map { type: PyClassType? -> collectTypeSubstitutions(type!!, context) }
        .forEach { newSubstitutions ->
          for (typeVarMapping in newSubstitutions.typeVars.entries) {
            substitutions.putTypeVar(typeVarMapping.key, typeVarMapping.value, KeyImpl, true)
          }
          for (typeVarMapping in newSubstitutions.typeVarTuples.entries) {
            substitutions.putTypeVarTuple(typeVarMapping.key, typeVarMapping.value, KeyImpl, true)
          }
          for (paramSpecMapping in newSubstitutions.paramSpecs.entries) {
            substitutions.putParamSpec(paramSpecMapping.key, paramSpecMapping.value, KeyImpl, true)
          }
        }
    }
    substitutions.setFrozenTypeVars(HashSet(substitutions.typeVars.keys), KeyImpl)
    return substitutions
  }

  private fun matchClasses(superClass: PyClass?, subClass: PyClass?, context: TypeEvalContext): Boolean {
    return if (superClass == null || subClass == null ||
               subClass.isSubclass(superClass, context) ||
               PyABCUtil.isSubclass(subClass, superClass, context) ||
               isStrUnicodeMatch(subClass, superClass) ||
               isBytearrayBytesStringMatch(subClass, superClass) ||
               PyUtil.hasUnresolvedAncestors(subClass, context)
    ) {
      true
    }
    else {
      val superName = superClass.name
      superName != null && superName == subClass.name
    }
  }

  private fun isStrUnicodeMatch(subClass: PyClass, superClass: PyClass): Boolean {
    // TODO: Check for subclasses as well
    return PyNames.TYPE_STR == subClass.name && PyNames.TYPE_UNICODE == superClass.name
  }

  private fun isBytearrayBytesStringMatch(subClass: PyClass, superClass: PyClass): Boolean {
    if (PyNames.TYPE_BYTEARRAY != subClass.name) return false

    val subClassFile = subClass.containingFile

    val isPy2 = if (subClassFile is PyiFile)
      PythonRuntimeService.getInstance().getLanguageLevelForSdk(PythonSdkUtil.findPythonSdk(subClassFile)).isPython2
    else
      LanguageLevel.forElement(subClass).isPython2

    val superClassName = superClass.name
    return isPy2 && PyNames.TYPE_STR == superClassName || !isPy2 && PyNames.TYPE_BYTES == superClassName
  }

  @JvmStatic
  fun isCallable(type: PyType?): Boolean? {
    return when (type) {
      null -> null
      is PyUnionType -> isUnionCallable(type)
      is PyCallableType -> type.isCallable
      is PyStructuralType if type.isInferredFromUsages -> true
      is PyTypeVarType -> {
        if (type.isDefinition) {
          true
        }
        else isCallable(type.getEffectiveBound())
      }
      else -> false
    }
  }

  /**
   * If at least one is callable -- it is callable.
   * If at least one is unknown -- it is unknown.
   * It is false otherwise.
   */
  private fun isUnionCallable(type: PyUnionType): Boolean? {
    for (member in type.members) {
      val callable = isCallable(member)
      if (callable == null) {
        return null
      }
      if (callable) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun definesGetAttr(file: PyFile, context: TypeEvalContext): Boolean {
    if (file is PyTypedElement) {
      val type = context.getType(file as PyTypedElement)
      if (type != null) {
        return resolveTypeMember(type, PyNames.GETATTR, context) != null
      }
    }

    return false
  }

  @JvmStatic
  fun overridesGetAttr(cls: PyClass, context: TypeEvalContext): Boolean {
    val type = context.getType(cls)
    if (type != null) {
      if (resolveTypeMember(type, PyNames.GETATTR, context) != null) {
        return true
      }
      val method = resolveTypeMember(type, PyNames.GETATTRIBUTE, context)
      if (method != null && !getInstance(cls).isBuiltin(method)) {
        return true
      }
    }
    return false
  }

  private fun resolveTypeMember(type: PyType, name: String, context: TypeEvalContext): PsiElement? {
    val resolveContext = PyResolveContext.defaultContext(context)
    val results = type.resolveMember(name, null, AccessDirection.READ, resolveContext)
    return results?.getOrNull(0)?.element
  }

  @JvmStatic
  fun getTargetTypeFromTupleAssignment(
    target: PyExpression,
    parentTupleOrList: PySequenceExpression,
    assignedTupleType: PyTupleType,
  ): PyType? {
    val count = assignedTupleType.elementCount
    val elements = parentTupleOrList.elements
    if (elements.size == count || assignedTupleType.isHomogeneous) {
      val index = elements.indexOf(target)
      if (index >= 0) {
        return assignedTupleType.getElementType(index)
      }
      for (i in 0..<count) {
        val element = PyPsiUtils.flattenParens(elements[i])
        if (element is PyTupleExpression || element is PyListLiteralExpression) {
          val elementType = assignedTupleType.getElementType(i)
          if (elementType is PyTupleType) {
            val result = getTargetTypeFromTupleAssignment(target, element, elementType)
            if (result != null) {
              return result
            }
          }
        }
      }
    }
    return null
  }

  /**
   * Populates an existing generic type with given actual type parameters in their original order.
   *
   * @return a parameterized version of the original type or `null` if it cannot be meaningfully parameterized.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun parameterizeType(
    genericType: PyType,
    actualTypeParams: List<PyType?>,
    context: TypeEvalContext,
  ): PyType? {
    val typeParams = genericType.collectGenerics(context)
    if (!typeParams.isEmpty) {
      val expectedTypeParams = ArrayList(LinkedHashSet(typeParams.allTypeParameters))
      val substitutions = mapTypeParametersToSubstitutions(
        expectedTypeParams,
        actualTypeParams,
        PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
        PyTypeParameterMapping.Option.USE_DEFAULTS,
      ) ?: return null
      return substitute(genericType, substitutions, context)
    }
    else if (genericType is PyCollectionType) {
      return genericType
    }
    else if (genericType is PyClassType) {
      val cls = genericType.pyClass
      return PyCollectionTypeImpl(cls, false, actualTypeParams)
    }
    return null
  }

  @JvmStatic
  @ApiStatus.Internal
  fun mapTypeParametersToSubstitutions(
    expectedTypes: List<PyType?>,
    actualTypes: List<PyType?>,
    vararg options: PyTypeParameterMapping.Option,
  ): GenericSubstitutions? {
    return mapTypeParametersToSubstitutions(GenericSubstitutions(), expectedTypes, actualTypes, *options)
  }

  private fun mapTypeParametersToSubstitutions(
    substitutions: GenericSubstitutions,
    expectedTypes: List<PyType?>,
    actualTypes: List<PyType?>,
    vararg options: PyTypeParameterMapping.Option,
  ): GenericSubstitutions? {
    val mapping = PyTypeParameterMapping.mapByShape(expectedTypes, actualTypes, *options) ?: return null
    for (pair in mapping.mappedTypes) {
      val first = pair.first
      val second = pair.second
      when (first) {
        is PyTypeVarType -> substitutions.putTypeVar(first, Ref(second), KeyImpl)
        is PyTypeVarTupleType -> substitutions.putTypeVarTuple(first, second as? PyPositionalVariadicType, KeyImpl)
        is PyParamSpecType -> substitutions.putParamSpec(first, second as? PyCallableParameterVariadicType, KeyImpl)
      }
    }
    return substitutions
  }

  @JvmStatic
  @ApiStatus.Internal
  fun convertToType(type: PyType?, superType: PyClassType, context: TypeEvalContext): PyType? {
    val matchContext = MatchContext(context, GenericSubstitutions(), false)
    val matched = match(superType, type, matchContext)
    if (matched.orElse(false)) {
      // There is a tricky problem with handling type parameter binds to Any. Namely, during matching list[Any] to Iterable[T@Iterable],
      // we don't keep the bind T@Iterable -> Any (see the implementation of `match(PyTypeVarType, PyType, MatchContext)`).
      // As a workaround, until we migrate to type checking with CSP, we consider that
      // all parameter of the super types should be bound, and if they aren't, we fall back them to Any.
      val substitutions = substituteUnboundTypeVarsWithDefaultOrAny(superType, listOf(superType), matchContext.mySubstitutions, context)
      return substitute(superType, substitutions, context)
    }
    return null
  }

  private class MyMatchHelper(private val myKey: Any, private val myContext: MatchContext) {
    fun match(expected: PyType?, actual: PyType?): Boolean {
      val result = RecursionManager.doPreventingRecursion(
        myKey, false, if (myContext.reversedSubstitutions)
          Computable { match(actual, expected, myContext) }
        else
          Computable { match(expected, actual, myContext) }
      )
      return result != null && result.orElse(false)!!
    }
  }

  fun Generics(): Generics = GenericsImpl()

  @ApiStatus.Internal
  sealed interface Generics {
    val typeVars: Set<PyTypeVarType>
    val typeVarTuples: Set<PyTypeVarTupleType>
    val allTypeParameters: List<PyTypeParameterType>
    val paramSpecs: Set<PyParamSpecType>
    val self: PySelfType?
    val isEmpty: Boolean
  }

  private data class GenericsImpl(
    override val typeVars: MutableSet<PyTypeVarType> = LinkedHashSet(),
    override val typeVarTuples: MutableSet<PyTypeVarTupleType> = LinkedHashSet(),
    override val allTypeParameters: MutableList<PyTypeParameterType> = ArrayList(),
    override val paramSpecs: MutableSet<PyParamSpecType> = LinkedHashSet(),
    override var self: PySelfType? = null,
  ) : Generics {
    override val isEmpty: Boolean get() = typeVars.isEmpty() && typeVarTuples.isEmpty() && paramSpecs.isEmpty() && self == null
  }

  @ApiStatus.Experimental
  class GenericSubstitutions() {
    private val myTypeVars: MutableMap<PyTypeVarType, Ref<PyType?>?> = LinkedHashMap()
    val typeVars: Map<PyTypeVarType, Ref<PyType?>?>
      get() = Collections.unmodifiableMap(myTypeVars)

    private val myTypeVarTuples: MutableMap<PyTypeVarTupleType, PyPositionalVariadicType?> = LinkedHashMap()
    val typeVarTuples: Map<PyTypeVarTupleType, PyPositionalVariadicType?>
      get() = Collections.unmodifiableMap(myTypeVarTuples)

    private val myParamSpecs: MutableMap<PyParamSpecType, PyCallableParameterVariadicType?> = LinkedHashMap()
    val paramSpecs: Map<PyParamSpecType, PyCallableParameterVariadicType?>
      get() = Collections.unmodifiableMap(myParamSpecs)

    var qualifierType: PyType? = null
    private var frozenTypeVars: Set<PyTypeVarType> = emptySet()

    constructor(typeParameters: Map<out PyTypeParameterType, PyType?>) : this() {
      for ((key, value) in typeParameters) {
        when (key) {
          is PyTypeVarType -> myTypeVars[key] = Ref(value)
          is PyTypeVarTupleType -> if (value is PyPositionalVariadicType) myTypeVarTuples[key] = value
          is PyParamSpecType -> if (value is PyCallableParameterVariadicType) myParamSpecs[key] = value
        }
      }
    }

    constructor(typeVars: Map<PyTypeVarType, Ref<PyType?>?>, typeVarTuples: Map<PyTypeVarTupleType, PyPositionalVariadicType?>, paramSpecs: Map<PyParamSpecType, PyCallableParameterVariadicType?>, qualifierType: PyType?) : this() {
      this.myTypeVars.putAll(typeVars)
      this.myTypeVarTuples.putAll(typeVarTuples)
      this.myParamSpecs.putAll(paramSpecs)
      this.qualifierType = qualifierType
    }

    fun addToCopy(typeVars: Map<PyTypeVarType, Ref<PyType?>?>?, typeVarTuples: Map<PyTypeVarTupleType, PyPositionalVariadicType>?, paramSpecs: Map<PyParamSpecType, PyCallableParameterVariadicType>?) : GenericSubstitutions {
      val newTypeVars = LinkedHashMap(this.typeVars);
      if (typeVars != null) {
        newTypeVars.putAll(typeVars);
      }
      val newTypeVarTuples = LinkedHashMap(this.typeVarTuples);
      if (typeVarTuples != null) {
        newTypeVarTuples.putAll(typeVarTuples);
      }
      val newParamSpecs = LinkedHashMap(this.paramSpecs);
      if (paramSpecs != null) {
        newParamSpecs.putAll(paramSpecs);
      }
      return GenericSubstitutions(newTypeVars, newTypeVarTuples, newParamSpecs, qualifierType);
    }

    @ApiStatus.Internal
    fun getFrozenTypeVars(@Suppress("unused") key: Key): Set<PyTypeVarType> = frozenTypeVars

    @ApiStatus.Internal
    fun setFrozenTypeVars(value: Set<PyTypeVarType>, @Suppress("unused") key: Key) {
      frozenTypeVars = value
    }

    @ApiStatus.Internal
    fun putTypeVar(typeVar: PyTypeVarType, substitute: Ref<PyType?>?, @Suppress("unused") key: Key, ifAbsent: Boolean = false) {
      if (ifAbsent) myTypeVars.putIfAbsent(typeVar, substitute)
      else myTypeVars[typeVar] = substitute
    }

    @ApiStatus.Internal
    fun putTypeVarTuple(
      typeVarTuple: PyTypeVarTupleType,
      substitute: PyPositionalVariadicType?,
      @Suppress("unused") key: Key,
      ifAbsent: Boolean = false,
    ) {
      if (ifAbsent) myTypeVarTuples.putIfAbsent(typeVarTuple, substitute)
      else myTypeVarTuples[typeVarTuple] = substitute
    }

    @ApiStatus.Internal
    fun putParamSpec(
      paramSpec: PyParamSpecType,
      substitute: PyCallableParameterVariadicType?,
      @Suppress("unused") key: Key,
      ifAbsent: Boolean = false,
    ) {
      if (ifAbsent) myParamSpecs.putIfAbsent(paramSpec, substitute)
      else myParamSpecs[paramSpec] = substitute
    }

    override fun toString(): String =
      "GenericSubstitutions(typeVars=$typeVars, typeVarTuples$typeVarTuples, paramSpecs=$paramSpecs)"
  }

  sealed class Key
  private object KeyImpl : Key()

  private class MatchContext(
    val context: TypeEvalContext,
    val mySubstitutions: GenericSubstitutions,
    val reversedSubstitutions: Boolean,
  ) {
    fun reverseSubstitutions(): MatchContext {
      return MatchContext(context, mySubstitutions, !reversedSubstitutions)
    }

    fun resetSubstitutions(): MatchContext {
      return MatchContext(context, mySubstitutions, false)
    }
  }
}
