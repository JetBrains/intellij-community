// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.dynatrace.hash4j.hashing.HashValue128
import com.dynatrace.hash4j.hashing.Hashing
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ArrayUtil
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.ast.PyAstTypeParameter
import com.jetbrains.python.ast.impl.PyUtilCore
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation
import com.jetbrains.python.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotationFile
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintFile
import com.jetbrains.python.codeInsight.typeRepresentation.PyModuleTypeName
import com.jetbrains.python.codeInsight.typeRepresentation.psi.PyFunctionTypeRepresentation
import com.jetbrains.python.codeInsight.typing.PyTypeHintProvider.Companion.parseTypeHint
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Context
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.FutureFeature
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecoratable
import com.jetbrains.python.psi.PyDoubleStarExpression
import com.jetbrains.python.psi.PyEllipsisLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyForPart
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyNoneLiteralExpression
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeCommentOwner
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.PyWithAncestors
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableParameterListType
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyConcatenateType
import com.jetbrains.python.psi.types.PyInstantiableType
import com.jetbrains.python.psi.types.PyIntersectionType.Companion.intersection
import com.jetbrains.python.psi.types.PyLiteralStringType.Companion.create
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyNarrowedType
import com.jetbrains.python.psi.types.PyNarrowedType.Companion.create
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyParamSpecType
import com.jetbrains.python.psi.types.PyPositionalVariadicType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.collectGenerics
import com.jetbrains.python.psi.types.PyTypeParameterMapping
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypeUtil.convertToType
import com.jetbrains.python.psi.types.PyTypeUtil.toKeywordContainerType
import com.jetbrains.python.psi.types.PyTypeUtil.toPositionalContainerType
import com.jetbrains.python.psi.types.PyTypeVarTupleType
import com.jetbrains.python.psi.types.PyTypeVarTupleTypeImpl
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyTypeVarTypeImpl
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl
import com.jetbrains.python.psi.types.PyVariadicType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.Objects
import java.util.Optional
import java.util.function.UnaryOperator
import java.util.regex.Pattern

class PyTypingTypeProvider : PyTypeProviderWithCustomContext<Context?>() {
  public override fun getReferenceExpressionType(referenceExpression: PyReferenceExpression, context: Context): PyType? {
    // Check for the exact name in advance for performance reasons
    if ("Generic" == referenceExpression.name) {
      if (resolvesToQualifiedNames(referenceExpression, context.typeContext, GENERIC)) {
        return createTypingGenericType(referenceExpression)
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Protocol" == referenceExpression.name) {
      if (resolvesToQualifiedNames(referenceExpression, context.typeContext, PROTOCOL, PROTOCOL_EXT)) {
        return createTypingProtocolType(referenceExpression)
      }
    }
    // Check for the exact name in advance for performance reasons
    if ("Callable" == referenceExpression.name) {
      if (resolvesToQualifiedNames(referenceExpression, context.typeContext, CALLABLE, CALLABLE_EXT)) {
        return createTypingCallableType(referenceExpression)
      }
    }

    return null
  }

  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: Context): Ref<PyType?>? {
    var typeHint: PyExpression? = getAnnotationValue(param, context.typeContext)
    if (typeHint == null) {
      val paramTypeCommentHint = param.typeCommentAnnotation
      if (paramTypeCommentHint != null) {
        typeHint = PyUtil.createExpressionFromFragment(paramTypeCommentHint, param)
      }
    }
    if (typeHint == null) {
      val annotation: PyFunctionTypeAnnotation? = getFunctionTypeAnnotation(func)
      if (annotation != null) {
        val funcTypeCommentParamHint: PyExpression? = findParamTypeHintInFunctionTypeComment(annotation, param, func)
        if (funcTypeCommentParamHint == null) {
          return Ref()
        }
        typeHint = funcTypeCommentParamHint
      }
    }
    if (typeHint == null) {
      return null
    }
    if (param.isKeywordContainer) {
      val type: Ref<PyType?>? = getTypeFromUnpackOperator(typeHint, context.typeContext)
      if (type != null) {
        return if (type.get() is PyTypedDictType) type else null
      }
    }
    if (typeHint is PyReferenceExpression && typeHint.isQualified && (param.isPositionalContainer && "args" == typeHint.referencedName ||
                                                                      param.isKeywordContainer && "kwargs" == typeHint.referencedName
                                                                     )
    ) {
      typeHint = typeHint.qualifier!!
    }
    val type = Ref.deref<PyType?>(getType(typeHint, context))
    if (param.isPositionalContainer && type !is PyParamSpecType) {
      return Ref(param.toPositionalContainerType(type))
    }
    if (param.isKeywordContainer && type !is PyParamSpecType) {
      return Ref(param.toKeywordContainerType(type))
    }
    if (PyNames.NONE == param.defaultValueText) {
      return Ref(PyUnionType.union(type, getInstance(param).noneType))
    }
    return Ref(type)
  }

  override fun getReturnType(callable: PyCallable, context: Context): Ref<PyType?>? {
    if (callable is PyFunction) {
      val returnTypeAnnotation: PyExpression? = getReturnTypeAnnotation(callable, context.typeContext)
      if (returnTypeAnnotation != null) {
        val typeRef: Ref<PyType?>? = getType(returnTypeAnnotation, context)
        if (typeRef != null) {
          // Do not use toAsyncIfNeeded, as it also converts Generators. Here we do not need it.
          if (callable.isAsync && callable.isAsyncAllowed && !callable.isGenerator) {
            return Ref(wrapInCoroutineType(typeRef.get(), callable))
          }
          return typeRef
        }
        // Don't rely on other type providers if a type hint is present, but cannot be resolved.
        return Ref()
      }
    }
    return null
  }

  override fun getCallType(function: PyFunction, callSite: PyCallSiteExpression, context: Context): Ref<PyType?>? {
    val functionQName = function.qualifiedName

    if (CAST == functionQName || CAST_EXT == functionQName) {
      return Optional
        .ofNullable(callSite as PyCallExpression)
        .map { it.arguments }
        .filter { it.isNotEmpty() }
        .map { getType(it[0]!!, context) }
        .orElse(null)
    }

    if (functionReturningCallSiteAsAType(function)) {
      return getAsClassObjectType(callSite, context)
    }

    return null
  }

  override fun getReferenceType(referenceTarget: PsiElement, context: Context, anchor: PsiElement?): Ref<PyType?>? {
    var referenceTarget = referenceTarget
    if (referenceTarget is PyTargetExpression) {
      val targetQName = referenceTarget.qualifiedName

      // Depends on typing.Generic defined as a target expression
      if (GENERIC == targetQName) {
        return Ref(createTypingGenericType(referenceTarget))
      }
      // Depends on typing.Protocol defined as a target expression
      if (PROTOCOL == targetQName || PROTOCOL_EXT == targetQName) {
        return Ref(createTypingProtocolType(referenceTarget))
      }
      // Depends on typing.Callable defined as a target expression
      if (CALLABLE == targetQName || CALLABLE_EXT == targetQName) {
        return Ref(createTypingCallableType(referenceTarget))
      }

      val collection: PyType? = getCollection(referenceTarget, context.typeContext)
      if (collection is PyInstantiableType<*>) {
        return Ref(collection.toClass())
      }

      val typedDictType: PyType? = getTypedDictTypeForTarget(referenceTarget, context.typeContext)
      if (typedDictType != null) {
        return Ref(typedDictType)
      }

      val assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(referenceTarget)
      if (assignedValue is PyCallExpression && assignedValue.isCalleeText("TypeVar", "TypeVarTuple", "ParamSpec")) {
        for (element in tryResolving(assignedValue.callee!!, context.typeContext)) {
          if (
            element is PyClass &&
            (element.qualifiedName ?: "") in TYPE_PARAMETER_FACTORIES
          ) {
            (context.typeContext.getType(element) as? PyClassType)?.let {
              return Ref(it.toInstance())
            }
          }
        }
      }

      // Return a type from an immediate type hint, e.g. from a syntactic annotation for
      //
      // x: int = ...
      //
      // or find the "root" declaration and get a type from a type hint there, e.g.
      //
      // x: int
      // x = ...
      //
      // or
      //
      // class C:
      //     attr: int
      //     def __init__(self, x):
      //         self.attr = x
      //
      // self.attr = ...

      // assignments "inst.attr = ..." are not preserved in stubs anyway. See PyTargetExpressionElementType.shouldCreateStub.
      if (referenceTarget.isQualified && context.typeContext.maySwitchToAST(referenceTarget)) {
        val resolved = referenceTarget.getReference(PyResolveContext.defaultContext(context.typeContext)).resolve()
        if (resolved is PyTargetExpression && PyUtil.isAttribute(resolved)) {
          referenceTarget = resolved
        }
      }

      val annotatedType: Ref<PyType?>? = getTypeFromTypeHint<PyTargetExpression?>(referenceTarget, context)
      if (annotatedType != null) {
        return annotatedType
      }

      val name = referenceTarget.referencedName
      val scopeOwner = ScopeUtil.getScopeOwner(referenceTarget)
      if (name == null || scopeOwner == null) {
        return null
      }

      val pyClass = referenceTarget.containingClass

      if (referenceTarget.isQualified) {
        if (pyClass != null && scopeOwner is PyFunction) {
          val resolveContext = PyResolveContext.defaultContext(context.typeContext)

          val isInstanceAttribute: Boolean
          if (context.typeContext.maySwitchToAST(referenceTarget)) {
            isInstanceAttribute =
              StreamEx.of<PsiElement?>(PyUtil.multiResolveTopPriority(referenceTarget.qualifier!!, resolveContext))
                .select(PyParameter::class.java)
                .filter { obj: PyParameter? -> obj!!.isSelf }
                .anyMatch { p: PyParameter? ->
                  PsiTreeUtil.getParentOfType(
                    p,
                    PyFunction::class.java
                  ) === scopeOwner
                }
          }
          else {
            isInstanceAttribute = PyUtil.isInstanceAttribute(referenceTarget)
          }
          if (!isInstanceAttribute) {
            return null
          }
          // Set isDefinition=true to start searching right from the class level.
          val memberType: Ref<PyType?>? =
            getMemberTypeForClassType(context, referenceTarget, name, resolveContext, false, PyClassTypeImpl(pyClass, true))
          if (memberType != null) {
            return memberType
          }

          for (ancestor in pyClass.getAncestorClasses(resolveContext.typeEvalContext)) {
            val ancestorMemberType: Ref<PyType?>? =
              getMemberTypeForClassType(
                context,
                referenceTarget,
                name,
                resolveContext,
                true,
                PyClassTypeImpl(ancestor, false)
              )
            if (ancestorMemberType != null) {
              return ancestorMemberType
            }
          }

          return null
        }
      }
      else {
        if (context.typeContext.maySwitchToAST(referenceTarget)) {
          val resolveContext = PyResolveContext.defaultContext(context.typeContext)
          val visited: MutableSet<PyTargetExpression?> = HashSet()
          var current: PsiElement? = referenceTarget
          while (current is PyTargetExpression && visited.add(current)) {
            val resolveResults = current
              .getReference(resolveContext)
              .multiResolve(false)
              .filter { it.element !== current }

            current = PyUtil.filterTopPriorityElements(resolveResults).firstOrNull()

            if (current is PyTargetExpression) {
              val type = getTypeFromTypeHint(current, context)
              if (type != null) {
                return type
              }
            }
            else if (current is PyNamedParameter) {
              val type = getTypeFromTypeHint(current, context)
              if (type != null) {
                if (current.isPositionalContainer) {
                  return Ref.create(current.toPositionalContainerType(type.get()))
                }
                else if (current.isKeywordContainer) {
                  return Ref.create(current.toKeywordContainerType(type.get()))
                }
                return type
              }
            }
          }
        }
        else {
          val candidates =
            when (scopeOwner) {
              is PyFile -> scopeOwner.topLevelAttributes
              is PyClass -> scopeOwner.classAttributes
              else -> null
            }
          if (candidates != null) {
            return candidates
              .filter { name == it.name }
              .firstNotNullOfOrNull { getTypeFromTypeHint(it, context) }
          }
        }
      }
    }
    if (anchor is PyExpression && referenceTarget is PyClass && isGeneric(referenceTarget, context.typeContext)) {
      val parameterizedType = parameterizeClassDefaultAware(referenceTarget, listOf(), context)
      if (parameterizedType != null) {
        return Ref(parameterizedType.toClass())
      }
    }
    return null
  }

  override fun getGenericType(cls: PyClass, context: Context): PyType? {
    val typeParameters = collectTypeParameters(cls, context)
    return if (typeParameters.isEmpty()) null else PyCollectionTypeImpl(cls, false, typeParameters)
  }

  override fun getGenericSubstitutions(cls: PyClass, context: Context): Map<PyType?, PyType?> {
    return PyUtil.getParameterizedCachedValue(
      cls,
      context
    ) { calculateGenericSubstitutions(cls, it) }
  }

  private fun calculateGenericSubstitutions(cls: PyClass, context: Context): Map<PyType?, PyType?> {
    if (!isGeneric(cls, context.typeContext)) {
      return emptyMap()
    }
    val results = HashMap<PyType?, PyType?>()
    for (superClassType in evaluateSuperClassesAsTypeHints(cls, context.typeContext)) {
      RecursionManager.doPreventingRecursion(
        superClassType.pyClass,
        false) {
        getGenericSubstitutions(superClassType.pyClass, context)
      }?.let { superSubstitutions ->
        results.putAll(superSubstitutions)
      }
      // TODO Share this logic with PyTypeChecker.collectTypeSubstitutions
      val superTypeParameters = collectTypeParameters(superClassType.pyClass, context)
      val superTypeArguments = if (superClassType is PyCollectionType) superClassType.elementTypes else mutableListOf<PyType?>()
      val mapping =
        PyTypeParameterMapping.mapByShape(
          superTypeParameters, superTypeArguments, PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
          PyTypeParameterMapping.Option.USE_DEFAULTS
        )
      if (mapping != null) {
        for (pair in mapping.mappedTypes) {
          val expectedType = pair.getFirst()
          val actualType = pair.getSecond()
          if (expectedType != actualType) {
            results[expectedType] = actualType
          }
        }
      }
    }
    return results
  }

  @JvmRecord
  data class GeneratorTypeDescriptor(
    @JvmField val yieldType: PyType?,
    @JvmField val sendType: PyType?,
    @JvmField val returnType: PyType?,
    @JvmField val isAsync: Boolean,
  ) {
    companion object {
      /**
       * Extracts type parameters from typing.Generator and typing.AsyncGenerator
       */
      fun fromGenerator(type: PyType?): GeneratorTypeDescriptor? {
        if (type !is PyClassType) return null

        val qName = type.classQName
        if (qName == null) return null

        val isAsync = ASYNC_GENERATOR == qName
        if (!isAsync && GENERATOR != qName) return null

        val noneType: PyType? = getInstance(type.pyClass).noneType

        var yieldType: PyType? = null
        var sendType = noneType
        var returnType = if (isAsync) null else noneType
        if (type is PyCollectionType) {
          yieldType = type.elementTypes.getOrNull(0)
          sendType = type.elementTypes.getOrElse(1) { sendType }
          returnType = type.elementTypes.getOrElse(2) { returnType }
        }
        return GeneratorTypeDescriptor(yieldType, sendType, returnType, isAsync)
      }

      /**
       * Unlike [.fromGenerator], this method can also extract yield type from Protocol types like typing.Iterable
       */
      @JvmStatic
      fun fromGeneratorOrProtocol(type: PyType?, context: TypeEvalContext): GeneratorTypeDescriptor? {
        if (type !is PyClassType) return null

        val desc = fromGenerator(type)
        if (desc != null) {
          return desc
        }

        if (type.isProtocol(context)) {
          var yieldType: PyType?

          val syncUpcast = type.convertToType("typing.Iterable", type.pyClass, context)
          if (syncUpcast is PyCollectionType) {
            yieldType = syncUpcast.iteratedItemType
            return GeneratorTypeDescriptor(yieldType, null, null, false)
          }
          val asyncUpcast = type.convertToType("typing.AsyncIterable", type.pyClass, context)
          if (asyncUpcast is PyCollectionType) {
            yieldType = asyncUpcast.iteratedItemType
            return GeneratorTypeDescriptor(yieldType, null, null, true)
          }

          // Here we try to understand a yield type by return type of __next__ method of protocol specified in annotation.
          // We cannot use convertToType with typing.Iterator here, as it inherits from typing.Iterable
          // and requires both __iter__ and __next__, while it should be possible to decide the yield type only by __next__.
          // TODO: unify logic with PyTargetExpressionImpl.getIterationType (PY-82453)
          val next = type.pyClass.findMethodByName(PyNames.DUNDER_NEXT, true, context)
          if (next != null) {
            yieldType = context.getReturnType(next)
            yieldType = PyTypeChecker.substitute(yieldType, PyTypeChecker.unifyReceiver(type, context), context)
            return GeneratorTypeDescriptor(yieldType, null, null, false)
          }

          val anext = type.pyClass.findMethodByName(PyNames.ANEXT, true, context)
          if (anext != null) {
            yieldType = Ref.deref<PyType?>(unwrapCoroutineReturnType(context.getReturnType(anext)))
            yieldType = PyTypeChecker.substitute(yieldType, PyTypeChecker.unifyReceiver(type, context), context)
            return GeneratorTypeDescriptor(yieldType, null, null, true)
          }
        }
        return null
      }
    }
  }

  override fun <T> withCustomContext(context: TypeEvalContext, delegate: java.util.function.Function<Context, T?>): T? {
    return staticWithCustomContext(context, delegate::apply)
  }

  @ApiStatus.Internal
  class Context(val typeContext: TypeEvalContext, val typeRepresentationMode: Boolean = false) {
    val typeAliasStack: Stack<PyQualifiedNameOwner?> = Stack()
    private val myClassSet: MutableSet<PyClass?> = HashSet()
    var isComputeTypeParameterScopeEnabled: Boolean = true
      private set

    init {
      recomputeStrongHashValue()
    }

    // Explicit API for manipulating type alias stack
    fun containsTypeAlias(alias: PyQualifiedNameOwner): Boolean {
      return alias in typeAliasStack
    }

    fun addTypeAlias(alias: PyQualifiedNameOwner) {
      typeAliasStack.add(alias)
      recomputeStrongHashValue()
    }

    fun removeTypeAlias(alias: PyQualifiedNameOwner) {
      typeAliasStack.remove(alias)
      recomputeStrongHashValue()
    }

    val isTypeAliasStackEmpty: Boolean
      get() = typeAliasStack.isEmpty()

    fun peekTypeAlias(): PyQualifiedNameOwner? {
      return if (typeAliasStack.isEmpty()) null else typeAliasStack.peek()
    }

    fun pushTypeAlias(alias: PyQualifiedNameOwner) {
      typeAliasStack.push(alias)
      recomputeStrongHashValue()
    }

    fun popTypeAlias(): PyQualifiedNameOwner? {
      val res = if (typeAliasStack.isEmpty()) null else typeAliasStack.pop()
      recomputeStrongHashValue()
      return res
    }

    fun addClassDeclaration(pyClass: PyClass): Boolean {
      return myClassSet.add(pyClass)
    }

    fun removeClassDeclaration(pyClass: PyClass) {
      myClassSet.remove(pyClass)
    }

    fun setComputeTypeParameterScopeEnabled(value: Boolean): Boolean {
      val prev = isComputeTypeParameterScopeEnabled
      isComputeTypeParameterScopeEnabled = value
      recomputeStrongHashValue()
      return prev
    }

    fun getKnownType(expression: PyExpression): PyType? {
      return typeContext.getContextTypeCache()[expression to contextStrongHashValue]
    }

    fun assumeType(expression: PyExpression, type: PyType) {
      typeContext.getContextTypeCache()[expression to contextStrongHashValue] = type
    }

    private var myContextStrongHashValue: HashValue128? = null

    private fun recomputeStrongHashValue() {
      myContextStrongHashValue = Hashing.xxh3_128().hashCharsTo128Bits(
        buildList {
          add(if (isComputeTypeParameterScopeEnabled) "1" else "0")
          add(typeAliasStack.map { it!!.qualifiedName })
          add(myClassSet.map { it!!.qualifiedName })
        }.joinToString("#")
      )
    }

    private val contextStrongHashValue: HashValue128
      get() = myContextStrongHashValue!!

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val context = other as Context
      return typeContext == context.typeContext
    }

    override fun hashCode(): Int {
      return Objects.hash(typeContext)
    }
  }

  companion object {
    @NlsSafe
    const val TYPING: @NlsSafe String = "typing"

    const val GENERATOR: String = "typing.Generator"
    const val ASYNC_GENERATOR: String = "typing.AsyncGenerator"
    const val COROUTINE: String = "typing.Coroutine"
    const val AWAITABLE: String = "typing.Awaitable"
    const val NAMEDTUPLE: String = "typing.NamedTuple"
    const val TYPED_DICT: String = "typing.TypedDict"
    const val TYPED_DICT_EXT: String = "typing_extensions.TypedDict"
    const val TYPE_GUARD: String = "typing.TypeGuard"
    const val TYPE_GUARD_EXT: String = "typing_extensions.TypeGuard"
    const val TYPE_IS: String = "typing.TypeIs"
    const val TYPE_IS_EXT: String = "typing_extensions.TypeIs"
    const val GENERIC: String = "typing.Generic"
    const val PROTOCOL: String = "typing.Protocol"
    const val PROTOCOL_EXT: String = "typing_extensions.Protocol"
    const val TYPE: String = "typing.Type"
    const val ANY: String = "typing.Any"
    const val NEW_TYPE: String = "typing.NewType"
    const val CALLABLE: String = "typing.Callable"
    const val CALLABLE_EXT: String = "typing_extensions.Callable"
    const val MAPPING: String = "typing.Mapping"
    const val MAPPING_GET: String = "typing.Mapping.get"
    private const val LIST = "typing.List"
    private const val DICT = "typing.Dict"
    private const val DEFAULT_DICT = "typing.DefaultDict"
    private const val ORDERED_DICT = "typing.OrderedDict"
    private const val SET = "typing.Set"
    private const val FROZEN_SET = "typing.FrozenSet"
    private const val COUNTER = "typing.Counter"
    private const val DEQUE = "typing.Deque"
    private const val TUPLE = "typing.Tuple"
    const val CLASS_VAR: String = "typing.ClassVar"
    const val TYPE_VAR: String = "typing.TypeVar"
    const val TYPE_VAR_EXT: String = "typing_extensions.TypeVar"
    const val TYPE_VAR_TUPLE: String = "typing.TypeVarTuple"
    const val TYPE_VAR_TUPLE_EXT: String = "typing_extensions.TypeVarTuple"
    const val PARAM_SPEC: String = "typing.ParamSpec"
    const val PARAM_SPEC_EXT: String = "typing_extensions.ParamSpec"
    private const val CHAIN_MAP = "typing.ChainMap"
    const val UNION: String = "typing.Union"
    const val CONCATENATE: String = "typing.Concatenate"
    const val CONCATENATE_EXT: String = "typing_extensions.Concatenate"
    const val OPTIONAL: String = "typing.Optional"
    const val NO_RETURN: String = "typing.NoReturn"
    const val NEVER: String = "typing.Never"
    const val NO_RETURN_EXT: String = "typing_extensions.NoReturn"
    const val NEVER_EXT: String = "typing_extensions.Never"
    const val FINAL: String = "typing.Final"
    const val FINAL_EXT: String = "typing_extensions.Final"
    const val LITERAL: String = "typing.Literal"
    const val LITERAL_EXT: String = "typing_extensions.Literal"
    const val LITERALSTRING: String = "typing.LiteralString"
    const val LITERALSTRING_EXT: String = "typing_extensions.LiteralString"
    const val ANNOTATED: String = "typing.Annotated"
    const val ANNOTATED_EXT: String = "typing_extensions.Annotated"
    const val TYPE_ALIAS: String = "typing.TypeAlias"
    const val TYPE_ALIAS_EXT: String = "typing_extensions.TypeAlias"
    const val TYPE_ALIAS_TYPE: String = "typing.TypeAliasType"
    const val SPECIAL_FORM: String = "typing._SpecialForm"
    const val SPECIAL_FORM_EXT: String = "typing_extensions._SpecialForm"
    const val REQUIRED: String = "typing.Required"
    const val REQUIRED_EXT: String = "typing_extensions.Required"
    const val NOT_REQUIRED: String = "typing.NotRequired"
    const val NOT_REQUIRED_EXT: String = "typing_extensions.NotRequired"
    const val READONLY: String = "typing.ReadOnly"
    const val READONLY_EXT: String = "typing_extensions.ReadOnly"
    const val ITERABLE: String = "typing.Iterable"

    val TYPE_PARAMETER_FACTORIES: Set<String> = setOf(
      TYPE_VAR, TYPE_VAR_EXT,
      PARAM_SPEC, PARAM_SPEC_EXT,
      TYPE_VAR_TUPLE, TYPE_VAR_TUPLE_EXT
    )

    val TYPE_DICT_QUALIFIERS: Set<String?> =
      setOf(REQUIRED, REQUIRED_EXT, NOT_REQUIRED, NOT_REQUIRED_EXT, READONLY, READONLY_EXT)

    const val UNPACK: String = "typing.Unpack"
    const val UNPACK_EXT: String = "typing_extensions.Unpack"

    const val SELF: String = "typing.Self"
    const val SELF_EXT: String = "typing_extensions.Self"

    val TYPE_IGNORE_PATTERN: Pattern = Pattern.compile("#\\s*type:\\s*ignore\\s*(\\[[^]#]*])?($|(\\s.*))", Pattern.CASE_INSENSITIVE)

    const val ASSERT_TYPE: String = "typing.assert_type"
    const val REVEAL_TYPE: String = "typing.reveal_type"
    const val REVEAL_TYPE_EXT: String = "typing_extensions.reveal_type"
    const val CAST: String = "typing.cast"
    const val CAST_EXT: String = "typing_extensions.cast"

    val BUILTIN_COLLECTION_CLASSES: ImmutableMap<String, String> = ImmutableMap.builder<String, String>()
      .put(LIST, "list")
      .put(DICT, "dict")
      .put(SET, PyNames.SET)
      .put(FROZEN_SET, "frozenset")
      .put(TUPLE, PyNames.TUPLE)
      .build()

    private val COLLECTIONS_CLASSES = ImmutableMap.builder<String, String>()
      .put(DEFAULT_DICT, "collections.defaultdict")
      .put(ORDERED_DICT, "collections.OrderedDict")
      .put(COUNTER, "collections.Counter")
      .put(DEQUE, "collections.deque")
      .put(CHAIN_MAP, "collections.ChainMap")
      .build()

    @JvmField
    val TYPING_COLLECTION_CLASSES: ImmutableMap<String, String> = ImmutableMap.builder<String, String>()
      .put("list", "List")
      .put("dict", "Dict")
      .put("set", "Set")
      .put("frozenset", "FrozenSet")
      .build()

    val TYPING_BUILTINS_GENERIC_ALIASES: ImmutableMap<String, String> = ImmutableMap.builder<String, String>()
      .putAll(TYPING_COLLECTION_CLASSES.entries)
      .put("type", "Type")
      .put("tuple", "Tuple")
      .build()

    @JvmField
    val GENERIC_CLASSES: ImmutableSet<String> = ImmutableSet.builder<String>() // special forms
      .add(
        TUPLE,
        GENERIC,
        PROTOCOL,
        CALLABLE,
        CALLABLE_EXT,
        TYPE,
        CLASS_VAR,
        FINAL,
        LITERAL,
        ANNOTATED,
        REQUIRED,
        NOT_REQUIRED,
        READONLY
      ) // type aliases
      .add(UNION, OPTIONAL, LIST, DICT, DEFAULT_DICT, ORDERED_DICT, SET, FROZEN_SET, COUNTER, DEQUE, CHAIN_MAP)
      .add(PROTOCOL_EXT, FINAL_EXT, LITERAL_EXT, ANNOTATED_EXT, REQUIRED_EXT, NOT_REQUIRED_EXT, READONLY_EXT)
      .build()

    /**
     * For the following names we shouldn't go further to the RHS of assignments,
     * since they are not type aliases already and in typing.pyi are assigned to
     * some synthetic values.
     */
    val OPAQUE_NAMES: ImmutableSet<String> = ImmutableSet.builder<String>()
      .add(PyKnownDecorator.TYPING_OVERLOAD.qualifiedName.toString())
      .add(ANY)
      .add(TYPE_VAR)
      .add(TYPE_VAR_EXT)
      .add(TYPE_VAR_TUPLE)
      .add(TYPE_VAR_TUPLE_EXT)
      .add(GENERIC)
      .add(PARAM_SPEC)
      .add(PARAM_SPEC_EXT)
      .add(CONCATENATE)
      .add(CONCATENATE_EXT)
      .add(TUPLE)
      .add(CALLABLE)
      .add(CALLABLE_EXT)
      .add(TYPE)
      .add(PyKnownDecorator.TYPING_NO_TYPE_CHECK.qualifiedName.toString())
      .add(PyKnownDecorator.TYPING_NO_TYPE_CHECK_EXT.qualifiedName.toString())
      .add(UNION)
      .add(OPTIONAL)
      .add(LIST)
      .add(DICT)
      .add(DEFAULT_DICT)
      .add(ORDERED_DICT)
      .add(SET)
      .add(FROZEN_SET)
      .add(PROTOCOL, PROTOCOL_EXT)
      .add(CLASS_VAR)
      .add(COUNTER)
      .add(DEQUE)
      .add(CHAIN_MAP)
      .add(NO_RETURN, NO_RETURN_EXT)
      .add(NEVER, NEVER_EXT)
      .add(FINAL, FINAL_EXT)
      .add(LITERAL, LITERAL_EXT)
      .add(TYPED_DICT, TYPED_DICT_EXT)
      .add(ANNOTATED, ANNOTATED_EXT)
      .add(TYPE_ALIAS, TYPE_ALIAS_EXT)
      .add(REQUIRED, REQUIRED_EXT)
      .add(NOT_REQUIRED, NOT_REQUIRED_EXT)
      .add(READONLY, READONLY_EXT)
      .add(SELF, SELF_EXT)
      .add(LITERALSTRING, LITERALSTRING_EXT)
      .build()

    private val TYPE_HINT_EVAL_CONTEXT = Key.create<Context?>("TYPE_HINT_EVAL_CONTEXT")

    private fun findParamTypeHintInFunctionTypeComment(
      annotation: PyFunctionTypeAnnotation,
      param: PyNamedParameter,
      func: PyFunction,
    ): PyExpression? {
      val paramTypes = annotation.parameterTypeList.parameterTypes
      if (paramTypes.size == 1 && paramTypes[0] is PyEllipsisLiteralExpression) {
        return null
      }
      val startOffset = if (omitFirstParamInTypeComment(func, annotation)) 1 else 0
      val funcParams = listOf(*func.parameterList.parameters)
      val i = funcParams.indexOf(param) - startOffset
      if (i >= 0 && i < paramTypes.size) {
        val paramTypeHint = paramTypes[i]
        when (paramTypeHint) {
          is PyStarExpression -> {
            return paramTypeHint.expression
          }
          is PyDoubleStarExpression -> {
            return paramTypeHint.expression
          }
          else -> {
            return paramTypeHint
          }
        }
      }
      return null
    }

    @JvmStatic
    fun isGenerator(type: PyType): Boolean {
      return type is PyCollectionType && GENERATOR == type.classQName
    }

    private fun createTypingGenericType(anchor: PsiElement): PyType {
      return PyCustomType(GENERIC, null, false, true, getInstance(anchor).objectType)
    }

    private fun createTypingProtocolType(anchor: PsiElement): PyType {
      return PyCustomType(PROTOCOL, null, false, true, getInstance(anchor).objectType)
    }

    fun createTypingCallableType(anchor: PsiElement): PyType {
      return PyCustomType(CALLABLE, null, false, true, getInstance(anchor).objectType)
    }

    private fun omitFirstParamInTypeComment(func: PyFunction, annotation: PyFunctionTypeAnnotation): Boolean {
      return func.containingClass != null && func.modifier != PyAstFunction.Modifier.STATICMETHOD && annotation.parameterTypeList
        .parameterTypes.size < func.parameterList.parameters.size
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getReturnTypeAnnotation(function: PyFunction, context: TypeEvalContext): PyExpression? {
      val returnAnnotation: PyExpression? = getAnnotationValue(function, context)
      if (returnAnnotation != null) {
        return returnAnnotation
      }
      val functionAnnotation: PyFunctionTypeAnnotation? = getFunctionTypeAnnotation(function)
      if (functionAnnotation != null) {
        return functionAnnotation.returnType
      }
      return null
    }

    fun getFunctionTypeAnnotation(function: PyFunction): PyFunctionTypeAnnotation? {
      val comment = function.typeCommentAnnotation
      if (comment == null) {
        return null
      }
      val file = CachedValuesManager.getCachedValue(
        function,
        CachedValueProvider {
          CachedValueProvider.Result.create(
            PyFunctionTypeAnnotationFile(
              function.typeCommentAnnotation!!,
              function
            ), function
          )
        })
      return file.annotation
    }

    private fun functionReturningCallSiteAsAType(function: PyFunction): Boolean {
      val name = function.name

      if (PyNames.CLASS_GETITEM == name) return true
      if (PyNames.GETITEM == name) {
        val cls = function.containingClass
        if (cls != null) {
          val qualifiedName = cls.qualifiedName
          return SPECIAL_FORM == qualifiedName || SPECIAL_FORM_EXT == qualifiedName
        }
      }

      return false
    }

    private fun getTypedDictTypeForTarget(referenceTarget: PyTargetExpression, context: TypeEvalContext): PyType? {
      if (PyTypedDictTypeProvider.Helper.isTypedDict(referenceTarget, context)) {
        return PyCustomType(
          TYPED_DICT, null, false, true,
          getInstance(referenceTarget).dictType
        )
      }

      return null
    }

    private fun getMemberTypeForClassType(
      context: Context,
      target: PyTargetExpression?,
      name: String,
      resolveContext: PyResolveContext,
      isInherited: Boolean,
      classType: PyClassTypeImpl,
    ): Ref<PyType?>? {
      val classAttrs =
        classType.resolveMember(name, target, AccessDirection.READ, resolveContext, isInherited)
      if (classAttrs == null) {
        return null
      }
      return StreamEx.of(classAttrs)
        .map<PsiElement?> { obj: RatedResolveResult? -> obj!!.element }
        .select(PyTargetExpression::class.java)
        .filter { x: PyTargetExpression? ->
          val owner = ScopeUtil.getScopeOwner(x)
          owner is PyClass || owner is PyFunction
        }
        .map<Ref<PyType?>?> { x: PyTargetExpression? -> getTypeFromTypeHint(x, context) }
        .collect(PyTypeUtil.toUnionFromRef())
    }

    private fun <T>
      getTypeFromTypeHint(element: T, context: Context): Ref<PyType?>? where T : PyAnnotationOwner?, T : PyTypeCommentOwner? {
      val annotation: PyExpression? = getAnnotationValue(element!!, context.typeContext)
      if (annotation != null) {
        return getType(annotation, context)
      }
      val comment = element.typeCommentAnnotation
      if (comment != null) {
        return getVariableTypeCommentType(comment, element, context)
      }
      return null
    }

    /**
     * Checks that text of a comment starts with "# type:" prefix and returns trimmed type hint after it.
     * The trailing part is supposed to contain type annotation in PEP 484 compatible format and an optional
     * plain text comment separated from it with another "#".
     *
     *
     * For instance, for `# type: List[int]# comment` it returns `List[int]`.
     *
     *
     * This method cannot return an empty string.
     *
     * @see .getTypeCommentValueRange
     */
    @JvmStatic
    fun getTypeCommentValue(text: String): String? {
      return PyUtilCore.getTypeCommentValue(text)
    }

    /**
     * Returns the corresponding text range for a type hint as returned by [.getTypeCommentValue].
     *
     * @see .getTypeCommentValue
     */
    fun getTypeCommentValueRange(text: String): TextRange? {
      return PyUtilCore.getTypeCommentValueRange(text)
    }

    private fun evaluateSuperClassesAsTypeHints(pyClass: PyClass, context: TypeEvalContext): MutableList<PyClassType> {
      val results: MutableList<PyClassType> = ArrayList()
      for (superClassExpression in getSuperClassExpressions(pyClass)) {
        val type = Ref.deref<PyType?>(getType(superClassExpression, context))
        if (type is PyClassType) {
          results.add(type)
        }
      }
      return results
    }

    private fun collectTypeParameters(cls: PyClass, context: Context): List<PyTypeParameterType?> {
      if (!isGeneric(cls, context.typeContext)) {
        return emptyList()
      }
      if (cls.typeParameterList != null) {
        val typeParameters = cls.typeParameterList!!.typeParameters
        return StreamEx.of(typeParameters)
          .map {
            getTypeParameterTypeFromTypeParameter(
              it!!,
              context
            )
          }
          .nonNull()
          .toList()
      }
      // See https://mypy.readthedocs.io/en/stable/generics.html#defining-sub-classes-of-generic-classes
      val parameterizedSuperClassExpressions = getSuperClassExpressions(cls).filterIsInstance<PySubscriptionExpression>()
      val genericAsSuperClass = parameterizedSuperClassExpressions.find {
        resolvesToQualifiedNames(it.operand,
                                 context.typeContext,
                                 GENERIC)
      }
      return StreamEx.of(
        if (genericAsSuperClass != null) listOf(genericAsSuperClass)
        else parameterizedSuperClassExpressions
      )
        .map { it!!.indexExpression }
        .flatMap {
          val tupleExpr = it as? PyTupleExpression
          if (tupleExpr != null) StreamEx.of(*tupleExpr.elements) else StreamEx.of(it)
        }
        .nonNull()
        .map { getType(it!!, context) }
        .map { Ref.deref(it) }
        .flatMap {
          val typeParams = it.collectGenerics(context.typeContext)
          StreamEx.of<PyType>(typeParams.typeVars).append(typeParams.typeVarTuples)
            .append(StreamEx.of(typeParams.paramSpecs))
        }
        .select(PyTypeParameterType::class.java)
        .distinct()
        .toList()
    }

    /**
     * If the class' stub is present, return expressions in the base classes list, converting
     * their saved text chunks into [PyExpressionCodeFragment] and extracting top-level expressions
     * from them. Otherwise, get superclass expressions directly from AST.
     */
    private fun getSuperClassExpressions(pyClass: PyClass): List<PyExpression> {
      val classStub = pyClass.stub
      if (classStub == null) {
        return pyClass.superClassExpressions.toList()
      }
      return CachedValuesManager.getCachedValue(pyClass, CachedValueProvider {
        CachedValueProvider.Result.create(
          classStub.superClassesText.mapNotNull { PyUtil.createExpressionFromFragment(it, pyClass) },
          PsiModificationTracker.MODIFICATION_COUNT,
        )
      }
      )
    }

    private fun collectTypeParametersFromTypeAliasStatement(
      typeAliasStatement: PyTypeAliasStatement,
      context: Context,
    ): MutableList<PyTypeParameterType?> {
      val typeParameterList = typeAliasStatement.typeParameterList
      if (typeParameterList != null) {
        val typeParameters = typeParameterList.typeParameters
        return StreamEx.of(typeParameters)
          .map<PyTypeParameterType?> { typeParameter: PyTypeParameter? ->
            getTypeParameterTypeFromTypeParameter(
              typeParameter!!,
              context
            )
          }
          .nonNull()
          .toList()
      }
      return mutableListOf()
    }

    @JvmStatic
    fun isGeneric(descendant: PyWithAncestors, context: TypeEvalContext): Boolean {
      if (descendant is PyClass && descendant.typeParameterList != null ||
          descendant is PyClassType && descendant.pyClass.typeParameterList != null
      ) {
        return true
      }
      for (ancestor in descendant.getAncestorTypes(context)) {
        if (ancestor != null) {
          if (ancestor.classQName in GENERIC_CLASSES) {
            return true
          }
          else if (ancestor is PyClassType &&
                   ancestor.pyClass.typeParameterList != null
          ) {
            return true
          }
        }
      }
      return false
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getType(expression: PyExpression, context: TypeEvalContext, useFqn: Boolean): Ref<PyType?>? {
      return staticWithCustomContext(
        context,
        useFqn
      ) { customContext: Context? -> getType(expression, customContext!!) }
    }

    @JvmStatic
    fun getType(expression: PyExpression, context: TypeEvalContext): Ref<PyType?>? {
      return staticWithCustomContext(
        context
      ) { customContext: Context? -> getType(expression, customContext!!) }
    }

    @JvmStatic
    fun getTypeForTypeHint(expression: PyExpression, context: TypeEvalContext): Ref<PyType?>? {
      return staticWithCustomContext(
        context,
        true
      ) { getTypeForResolvedElement(expression, null, expression, it) }
    }

    private fun getType(expression: PyExpression, context: Context): Ref<PyType?>? {
      context.getKnownType(expression)?.let {
        return Ref(it)
      }
      for (pair in tryResolvingWithAliases(expression, context.typeContext)) {
        val typeRef = getTypeForResolvedElement(expression, pair.first, pair.second!!, context)
        if (typeRef != null) {
          if (typeRef.get() != null) {
            context.assumeType(expression, typeRef.get()!!)
          }
          return typeRef
        }
      }
      return null
    }

    private fun typeHasOverloadedBitwiseOr(
      type: PyType, expression: PyExpression,
      context: Context,
    ): Boolean {
      if (type !is PyClassType) {
        return false
      }
      val typeContext = context.typeContext
      val metaClassType = type.getMetaClassType(typeContext, true)
      if (metaClassType == null) {
        return false
      }
      val resolved = metaClassType
        .resolveMember("__or__", expression, AccessDirection.READ, PyResolveContext.defaultContext(typeContext))
      if (resolved.isNullOrEmpty()) return false

      return StreamEx.of(resolved)
        .map<PsiElement?> { it: RatedResolveResult? -> it!!.element }
        .nonNull()
        .noneMatch { it: PsiElement? -> getInstance(it).isBuiltin(it) }
    }

    @JvmStatic
    fun isBitwiseOrUnionAvailable(context: TypeEvalContext): Boolean {
      val originFile = context.origin
      return originFile == null || isBitwiseOrUnionAvailable(originFile)
    }

    @JvmStatic
    fun isBitwiseOrUnionAvailable(element: PsiElement): Boolean {
      if (LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON310)) return true

      val file = element.containingFile
      if (file is PyFile && file.hasImportFromFuture(FutureFeature.ANNOTATIONS)) {
        return file === element || PsiTreeUtil.getParentOfType(
          element,
          PyAnnotation::class.java,
          false,
          PyStatement::class.java
        ) != null
      }

      return false
    }

    private fun getTypeForResolvedElement(
      typeHint: PyExpression,
      alias: PyQualifiedNameOwner?,
      resolved: PsiElement,
      context: Context,
    ): Ref<PyType?>? {
      if (alias != null) {
        if (context.containsTypeAlias(alias)) {
          // Recursive types are not yet supported
          return null
        }
        context.addTypeAlias(alias)
      }
      if (resolved is PyClass && !context.addClassDeclaration(resolved)) {
        // Resolving to normal classes shouldn't cause recursive evaluation of type hints,
        // but constructing recursive PyTypedDictTypes will trigger that.
        return null
      }
      try {
        val typeHintFromProvider = parseTypeHint(
          typeHint,
          alias,
          resolved,
          context.typeContext
        )
        if (typeHintFromProvider != null) {
          return typeHintFromProvider
        }
        val typeFromParenthesizedExpression: Ref<PyType?>? = getTypeFromParenthesizedExpression(resolved, context)
        if (typeFromParenthesizedExpression != null) {
          return typeFromParenthesizedExpression
        }
        // We perform chained resolve only for actual aliases as tryResolvingWithAliases() returns the passed-in
        // expression both when it's not a reference expression and when it's failed to resolve it, hence we might
        // hit SOE for mere unresolved references in the latter case.
        if (alias != null) {
          val typeFromTypeAlias: Ref<PyType?>? = getTypeFromTypeAlias(alias, typeHint, resolved, context)
          if (typeFromTypeAlias != null) {
            return typeFromTypeAlias
          }
        }
        val neverType: PyType? = getNeverType(resolved)
        if (neverType != null) {
          return Ref(neverType)
        }
        val unionType: Ref<PyType?>? = getUnionType(resolved, context)
        if (unionType != null) {
          return unionType
        }
        val intersectionType: Ref<PyType?>? = getIntersectionType(resolved, context)
        if (intersectionType != null) {
          return intersectionType
        }
        val concatenateType: PyType? = getConcatenateType(resolved, context)
        if (concatenateType != null) {
          return Ref(concatenateType)
        }
        val optionalType: Ref<PyType?>? = getOptionalType(resolved, context)
        if (optionalType != null) {
          return optionalType
        }
        val callableType: PyType? = getCallableType(resolved, context)
        if (callableType != null) {
          return Ref(callableType)
        }
        val classVarType: Ref<PyType?>? = unwrapTypeModifier(resolved, context, CLASS_VAR)
        if (classVarType != null) {
          return classVarType
        }
        val classObjType: Ref<PyType?>? = getClassObjectType(resolved, context)
        if (classObjType != null) {
          return classObjType
        }
        val finalType: Ref<PyType?>? = unwrapTypeModifier(resolved, context, FINAL, FINAL_EXT)
        if (finalType != null) {
          return finalType
        }
        val annotatedType: Ref<PyType?>? = getAnnotatedType(resolved, context)
        if (annotatedType != null) {
          return annotatedType
        }
        val requiredOrNotRequiredType: Ref<PyType?>? = getTypedDictSpecialItemType(resolved, context)
        if (requiredOrNotRequiredType != null) {
          return requiredOrNotRequiredType
        }
        val literalStringType: Ref<PyType?>? = getLiteralStringType(resolved, context)
        if (literalStringType != null) {
          return literalStringType
        }
        val literalType: Ref<PyType?>? = getLiteralType(resolved, context)
        if (literalType != null) {
          return literalType
        }
        val typeAliasType: Ref<PyType?>? = getExplicitTypeAliasType(resolved)
        if (typeAliasType != null) {
          return typeAliasType
        }
        val narrowedType: Ref<PyType?>? = getNarrowedType(resolved, context)
        if (narrowedType != null) {
          return narrowedType
        }
        val parameterizedType: PyType? = getParameterizedType(resolved, context)
        if (parameterizedType != null) {
          return Ref(parameterizedType)
        }
        val collection: PyType? = getCollection(resolved, context.typeContext)
        if (collection != null) {
          return Ref(collection)
        }
        val typeParameter: PyType? = getTypeParameterTypeFromDeclaration(resolved, context)
        if (typeParameter != null) {
          return Ref(anchorTypeParameter(typeHint, typeParameter, context))
        }
        val unpackedType: PyType? = getUnpackedType(resolved, context.typeContext)
        if (unpackedType != null) {
          return Ref(unpackedType)
        }
        val typeParameterType: PyType? = getTypeParameterTypeFromTypeParameter(resolved, context)
        if (typeParameterType != null) {
          return Ref(typeParameterType)
        }
        val callableParameterListType: PyType? = getCallableParameterListType(resolved, context)
        if (callableParameterListType != null) {
          return Ref(callableParameterListType)
        }
        val stringBasedType: PyType? = getStringLiteralType(resolved, context)
        if (stringBasedType != null) {
          return Ref(stringBasedType)
        }
        val anyType: Ref<PyType?>? = getAnyType(resolved, context)
        if (anyType != null) {
          return anyType
        }
        val typedDictType: PyType? = PyTypedDictTypeProvider.Helper.getTypedDictTypeForResolvedElement(
          resolved,
          context.typeContext
        )
        if (typedDictType != null) {
          return Ref(typedDictType)
        }
        val selfType: Ref<PyType?>? = getSelfType(resolved, typeHint, context)
        if (selfType != null) {
          return selfType
        }
        val noneType: Ref<PyType?>? = getNoneType(typeHint, resolved)
        if (noneType != null) {
          return noneType
        }
        val newType = PyTypingNewTypeTypeProvider.getNewTypeForResolvedElement(resolved, context.typeContext)
        if (newType != null) {
          return Ref(newType.toInstance())
        }
        val classType: Ref<PyType?>? = getClassType(typeHint, resolved, context)
        if (classType != null) {
          return classType
        }
        val typeEngineType = getTypeEngineType(typeHint, context)
        if (typeEngineType != null) {
          return typeEngineType
        }
        return null
      }
      finally {
        if (resolved is PyClass) {
          context.removeClassDeclaration(resolved)
        }
        if (alias != null) {
          context.removeTypeAlias(alias)
        }
      }
    }

    private fun getTypeEngineType(typeHint: PyExpression, context: Context): Ref<PyType?>? {
      if (!context.typeRepresentationMode) return null
      if (typeHint.text == "Unknown") {
        return Ref()
      }
      if (typeHint is PyFunctionTypeRepresentation) {
        val result = context.typeContext.getType(typeHint)
        if (result != null) {
          return Ref(result)
        }
      }
      if (typeHint is PySubscriptionExpression) {
        val moduleType: Ref<PyType?>? = getModuleType(typeHint)
        if (moduleType != null) {
          return moduleType
        }
      }
      if (typeHint is PyBinaryExpression && typeHint.operator === PyTokenTypes.AT) {
        val name: String = typeHint.leftExpression.text
        val scopeExpression = typeHint.rightExpression!!
        if (name == SELF) {
          val type = getType(scopeExpression, context) ?: return null

          val scopeType = type.get()
          if (scopeType is PyClassType) {
            return Ref(PySelfType(scopeType))
          }
        }
        val scopeOwner = if (scopeExpression is PyReferenceExpression) {
          scopeExpression.asQualifiedName()?.let { qualifiedName ->
            val scopeElement = PyResolveUtil.resolveFullyQualifiedName(qualifiedName, scopeExpression, context.typeContext)
            scopeElement as? PyQualifiedNameOwner
          }
        }
        else null
        val result = PyTypeVarTypeImpl(name, null).withScopeOwner(scopeOwner)
        return Ref(result)
      }
      return null
    }

    private fun getModuleType(moduleDefinition: PySubscriptionExpression): Ref<PyType?>? {
      val name = moduleDefinition.rootOperand.name
      if (name == null || name != PyModuleTypeName) {
        return null
      }
      val moduleReferenceExpression = moduleDefinition.indexExpression as? PyReferenceExpression ?: return null
      val moduleName = moduleReferenceExpression.name ?: return null
      val project = moduleDefinition.project
      val moduleInitFiles = PyModuleNameIndex.findByQualifiedName(QualifiedName.fromDottedString(moduleName), project, GlobalSearchScope.everythingScope(project))
      val skeletons = PythonSdkUtil.getSkeletonsRootPath(PathManager.getSystemDir().toString())
      val firstModuleInitFile =
        moduleInitFiles.find { it != null && !it.virtualFile.path.toNioPathOrNull()!!.startsWith(skeletons) }
        ?: moduleInitFiles.find { it != null }
        ?: return null

      return Ref(PyModuleType(firstModuleInitFile))
    }

    private fun getIntersectionType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PyBinaryExpression && resolved.operator === PyTokenTypes.AND) {
        val left = resolved.leftExpression
        val right = resolved.rightExpression
        if (left == null || right == null) return null

        val leftTypeRef: Ref<PyType?>? = getType(left, context)
        val rightTypeRef: Ref<PyType?>? = getType(right, context)
        if (leftTypeRef == null || rightTypeRef == null) return null

        val intersection = intersection(leftTypeRef.get(), rightTypeRef.get())
        return if (intersection != null) Ref(intersection) else null
      }
      return null
    }

    private fun getNoneType(typeHint: PyExpression, resolved: PsiElement): Ref<PyType?>? {
      if (typeHint is PyNoneLiteralExpression ||
          typeHint is PyReferenceExpression && PyNames.NONE == typeHint.text
      ) {
        return Ref(getInstance(resolved).noneType)
      }
      return null
    }

    private fun getCallableParameterListType(resolved: PsiElement, context: Context): PyType? {
      if (resolved is PyListLiteralExpression) {
        val argumentTypes =
          resolved.elements.map {
            Ref.deref(getType(it!!, context))
          }
        return PyCallableParameterListTypeImpl(
          argumentTypes.map { PyCallableParameterImpl.nonPsi(it) }
        )
      }
      return null
    }

    private fun getNarrowedType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PySubscriptionExpression) {
        val names = resolveToQualifiedNames(
          resolved.operand,
          context.typeContext
        )
        val isTypeIs = TYPE_IS in names || TYPE_IS_EXT in names
        val isTypeGuard = TYPE_GUARD in names || TYPE_GUARD_EXT in names
        if (isTypeIs || isTypeGuard) {
          val indexTypes: MutableList<PyType?> = getIndexTypes(resolved, context)
          if (indexTypes.size == 1) {
            val narrowedType = create(resolved, isTypeIs, indexTypes[0])
            if (narrowedType != null) {
              return Ref(narrowedType)
            }
          }
        }
      }
      return null
    }

    private fun getSelfType(resolved: PsiElement, typeHint: PyExpression, context: Context): Ref<PyType?>? {
      if (resolved is PyQualifiedNameOwner &&
          (SELF == resolved.qualifiedName ||
           SELF_EXT == resolved.qualifiedName)
      ) {
        val typeHintContext: PsiElement = getStubRetainedTypeHintContext(typeHint)

        val containingClass = typeHintContext as? PyClass ?: PsiTreeUtil.getStubOrPsiParentOfType(typeHintContext, PyClass::class.java)
        if (containingClass == null) return null

        val scopeClassType = PyUtil.`as`(containingClass.getType(context.typeContext), PyClassType::class.java)
        if (scopeClassType == null) return null

        return Ref(PySelfType(scopeClassType).toInstance())
      }
      return null
    }

    private fun getTypeFromParenthesizedExpression(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PyParenthesizedExpression) {
        val containedExpression = PyPsiUtils.flattenParens(resolved as PyExpression)
        return if (containedExpression != null) getType(containedExpression, context) else null
      }
      return null
    }

    private fun getExplicitTypeAliasType(resolved: PsiElement): Ref<PyType?>? {
      if (resolved is PyQualifiedNameOwner) {
        val qualifiedName = resolved.qualifiedName
        if (TYPE_ALIAS == qualifiedName || TYPE_ALIAS_EXT == qualifiedName) {
          return Ref()
        }
      }
      return null
    }

    private fun anchorTypeParameter(typeHint: PyExpression, type: PyType?, context: Context): PyType? {
      val typeParamDefinitionFromStack = if (context.isTypeAliasStackEmpty) null else context.peekTypeAlias()
      assert(typeParamDefinitionFromStack == null || typeParamDefinitionFromStack is PyTargetExpression)
      val targetExpr = typeParamDefinitionFromStack as PyTargetExpression?
      if (type is PyTypeVarTypeImpl) {
        return type.withScopeOwner(getTypeParameterScope(type.name, typeHint, context)).withDeclarationElement(targetExpr)
      }
      if (type is PyParamSpecType) {
        return type.withScopeOwner(getTypeParameterScope(type.name, typeHint, context)).withDeclarationElement(targetExpr)
      }
      if (type is PyTypeVarTupleTypeImpl) {
        return type.withScopeOwner(getTypeParameterScope(type.name, typeHint, context)).withDeclarationElement(targetExpr)
      }
      return type
    }

    private fun getClassObjectType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PySubscriptionExpression) {
        val operand = resolved.operand
        if (resolvesToQualifiedNames(
            operand,
            context.typeContext, TYPE, PyNames.TYPE
          )
        ) {
          val indexExpr = resolved.indexExpression
          if (indexExpr != null) {
            if (resolvesToQualifiedNames(indexExpr, context.typeContext, ANY)) {
              return Ref(getInstance(resolved).typeType)
            }
            return getAsClassObjectType(indexExpr, context)
          }
          // Map Type[Something] with unsupported type parameter to Any, instead of generic type for the class "type"
          return Ref()
        }
      }
      else if (TYPE == getQualifiedName(resolved)) {
        return Ref(getInstance(resolved).typeType)
      }
      return null
    }

    private fun getAsClassObjectType(expression: PyExpression, context: Context): Ref<PyType?> {
      val type = Ref.deref<PyType?>(getType(expression, context))
      val classType = type as? PyClassType
      if (classType != null && !classType.isDefinition) {
        return Ref(classType.toClass())
      }
      val typeVar = type as? PyTypeVarType
      if (typeVar != null && !typeVar.isDefinition) {
        return Ref(typeVar.toClass())
      }
      val selfType = type as? PySelfType
      if (selfType != null) {
        return Ref(selfType.toClass())
      }
      // Represent Type[Union[str, int]] internally as Union[Type[str], Type[int]]
      if (type is PyUnionType &&
          type.members.all { it is PyClassType && !it.isDefinition }
      ) {
        return Ref(type.map { (it as PyClassType).toClass() })
      }
      return Ref()
    }

    private fun getAnyType(element: PsiElement, context: Context): Ref<PyType?>? {
      if (ANY == getQualifiedName(element)) {
        return Ref()
      }
      if (context.typeRepresentationMode && ANY == element.text) {
        return Ref()
      }
      return null
    }

    private fun getClassType(typeHint: PyExpression, element: PsiElement, context: Context): Ref<PyType?>? {
      if (typeHint is PyReferenceExpression && element is PyTypedElement) {
        val typeContext = context.typeContext
        val type: PyType?
        if (context.typeRepresentationMode && element is PyReferenceExpression) {
          val qualifiedName = element.asQualifiedName()
          val project = element.project
          val class_ = if (qualifiedName != null) PyPsiFacade.getInstance(project)
            .createClassByQName(qualifiedName.toString(), element)
          else null
          type = class_?.getType(typeContext)
        }
        else {
          type = typeContext.getType(element)
        }
        if (type is PyClassLikeType) {
          if (type.isDefinition) {
            // If we're interpreting a type hint like "MyGeneric" that is not followed by a list of type arguments (e.g. MyGeneric[int]),
            // we want to parameterize it with its type parameters defaults already here.
            // We need this check for the type argument list because getParameterizedType() relies on getClassType() for
            // getting the type corresponding to the subscription expression operand.
            val stubRetainedContext: PsiElement = getStubRetainedTypeHintContext(typeHint)
            if (
              type is PyClassType
              && !(stubRetainedContext is PyClass ||
                   PsiTreeUtil.getStubOrPsiParentOfType(
                     stubRetainedContext,
                     ScopeOwner::class.java
                   ) is PyClass)
              && (typeHint.parent as? PySubscriptionExpression)?.operand != typeHint &&
              isGeneric(type, context.typeContext)
            ) {
              val parameterized =
                parameterizeClassDefaultAware(type.pyClass, listOf(), context)
              if (parameterized != null) {
                return Ref(parameterized.toInstance())
              }
            }
            val instanceType: PyType = type.toInstance()
            return Ref(instanceType)
          }
        }
      }
      return null
    }

    private fun getOptionalType(element: PsiElement, context: Context): Ref<PyType?>? {
      if (element is PySubscriptionExpression) {
        if (resolvesToQualifiedNames(element.operand, context.typeContext, OPTIONAL)) {
          val indexExpr = PyPsiUtils.flattenParens(element.indexExpression)
          val argExpr: PyExpression? = when (indexExpr) {
            is PyTupleExpression -> indexExpr.elements.singleOrNull() ?: indexExpr
            else -> indexExpr
          }

          if (argExpr != null) {
            val typeRef: Ref<PyType?>? = getType(argExpr, context)
            if (typeRef != null) {
              return Ref(PyUnionType.union(typeRef.get(), getInstance(element).noneType))
            }
          }
          return Ref()
        }
      }
      return null
    }

    private fun getLiteralStringType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PyTargetExpression) {
        if (resolvesToQualifiedNames(
            resolved,
            context.typeContext, LITERALSTRING, LITERALSTRING_EXT
          )
        ) {
          return Ref(create(resolved))
        }
      }

      return null
    }

    private fun getLiteralType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PySubscriptionExpression) {
        if (resolvesToQualifiedNames(resolved.operand, context, LITERAL, LITERAL_EXT)) {
          return Optional
            .ofNullable<PyExpression?>(resolved.indexExpression)
            .map<PyType?>(Function { index: PyExpression? ->
              PyLiteralType.fromLiteralParameter(
                index!!,
                context.typeContext
              )
            })
            .map(Function { value: PyType? -> Ref.create(value) })
            .orElse(null)
        }
      }

      return null
    }

    private fun getAnnotatedType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PySubscriptionExpression) {
        val operand = resolved.operand
        if (resolvesToQualifiedNames(
            operand,
            context.typeContext, ANNOTATED, ANNOTATED_EXT
          )
        ) {
          val indexExpr = resolved.indexExpression
          val type = if (indexExpr is PyTupleExpression) indexExpr.elements[0] else indexExpr
          if (type != null) {
            return getType(type, context)
          }
        }
      }

      return null
    }

    private fun getTypedDictSpecialItemType(resolved: PsiElement, context: Context): Ref<PyType?>? {
      if (resolved is PySubscriptionExpression) {
        val operand = resolved.operand
        if (resolvesToQualifiedNames(
            operand, context.typeContext,
            REQUIRED, REQUIRED_EXT,
            NOT_REQUIRED, NOT_REQUIRED_EXT,
            READONLY, READONLY_EXT
          )
        ) {
          val indexExpr = resolved.indexExpression
          val type = if (indexExpr is PyTupleExpression) indexExpr.elements[0] else indexExpr
          if (type != null) {
            return getType(type, context)
          }
        }
      }

      return null
    }

    private fun unwrapTypeModifier(resolved: PsiElement, context: Context, vararg type: String?): Ref<PyType?>? {
      if (resolved is PySubscriptionExpression) {
        if (resolvesToQualifiedNames(resolved.operand, context.typeContext, *type)) {
          val indexExpr = resolved.indexExpression
          if (indexExpr != null) {
            return getType(indexExpr, context)
          }
        }
      }

      return null
    }

    private fun <T> typeHintedWithName(
      owner: T,
      context: TypeEvalContext,
      vararg names: String?,
    ): Boolean where T : PyTypeCommentOwner?, T : PyAnnotationOwner? {
      return names.any { it in resolveTypeHintsToQualifiedNames(owner, context) }
    }

    private fun <T> resolveTypeHintsToQualifiedNames(
      owner: T,
      context: TypeEvalContext,
    ): Collection<String> where T : PyTypeCommentOwner?, T : PyAnnotationOwner? {
      var annotation: PyExpression? = getAnnotationValue(owner!!, context)
      if (annotation is PyStringLiteralExpression) {
        val annotationText = annotation.stringValue
        annotation = PyUtil.createExpressionFromFragment(annotationText, owner)
        if (annotation == null) return mutableListOf()
      }

      if (annotation is PySubscriptionExpression) {
        return resolveToQualifiedNames(annotation.operand, context)
      }
      else if (annotation is PyReferenceExpression) {
        return resolveToQualifiedNames(annotation, context)
      }

      val typeCommentValue = owner.typeCommentAnnotation
      val typeComment = if (typeCommentValue == null) null else PyUtil.createExpressionFromFragment(typeCommentValue, owner)
      if (typeComment is PySubscriptionExpression) {
        return resolveToQualifiedNames(typeComment.operand, context)
      }
      else if (typeComment is PyReferenceExpression) {
        return resolveToQualifiedNames(typeComment, context)
      }

      return mutableListOf()
    }

    fun isFinal(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
      return PyKnownDecoratorUtil
        .getKnownDecorators(decoratable, context)
        .any { it === PyKnownDecorator.TYPING_FINAL || it === PyKnownDecorator.TYPING_FINAL_EXT }
    }

    @JvmStatic
    fun <T> isFinal(owner: T, context: TypeEvalContext): Boolean where T : PyTypeCommentOwner?, T : PyAnnotationOwner? {
      return PyUtil.getParameterizedCachedValue(owner!!, context) {
        typeHintedWithName(owner, context, FINAL, FINAL_EXT)
      }
    }

    @JvmStatic
    fun <T> isReadOnly(owner: T, context: TypeEvalContext): Boolean where T : PyTypeCommentOwner?, T : PyAnnotationOwner? {
      return PyUtil.getParameterizedCachedValue(owner!!, context) {
        typeHintedWithName(owner, context, READONLY, READONLY_EXT)
      }
    }

    @JvmStatic
    fun <T> isClassVar(owner: T, context: TypeEvalContext): Boolean where T : PyAnnotationOwner?, T : PyTypeCommentOwner? {
      return PyUtil.getParameterizedCachedValue(owner!!, context) {
        typeHintedWithName(owner, context, CLASS_VAR)
      }
    }

    private fun resolvesToQualifiedNames(expression: PyExpression, context: Context, vararg names: String?): Boolean {
      if (!context.typeRepresentationMode) return resolvesToQualifiedNames(expression, context.typeContext, *names)
      if (expression !is PyReferenceExpression) return false
      val qualifier = expression.qualifier ?: return false
      val qName = qualifier.name + "." + expression.name
      return names.any { it == qName }
    }

    private fun resolvesToQualifiedNames(expression: PyExpression, context: TypeEvalContext, vararg names: String?): Boolean {
      val qualifiedNames = resolveToQualifiedNames(expression, context)
      return names.any { it in qualifiedNames }
    }

    @ApiStatus.Internal
    fun getAnnotationValue(owner: PyAnnotationOwner, context: TypeEvalContext): PyExpression? {
      if (context.maySwitchToAST(owner)) {
        val annotation = owner.annotation
        if (annotation != null) {
          return annotation.value
        }
      }
      else {
        val annotationText = owner.annotationValue
        if (annotationText != null) {
          return PyUtil.createExpressionFromFragment(annotationText, owner)
        }
      }
      return null
    }

    @JvmStatic
    fun getStringBasedType(
      contents: String,
      anchor: PsiElement,
      context: TypeEvalContext,
    ): Ref<PyType?>? {
      return staticWithCustomContext(
        context
      ) { getStringBasedType(contents, anchor, it) }
    }

    private fun getStringBasedType(contents: String, anchor: PsiElement, context: Context): Ref<PyType?>? {
      return RecursionManager.doPreventingRecursion(
        anchor to contents,
        true,
        Computable {
          val expr = PyUtil.createExpressionFromFragment(contents, anchor)
          if (expr != null) getType(expr, context) else null
        })
    }

    private fun getStringLiteralType(element: PsiElement, context: Context): PyType? {
      if (element is PyStringLiteralExpression) {
        val contents = element.stringValue
        // A multiline string literal can contain a type expression unparsable without parentheses
        return Ref.deref<PyType?>(
          getStringBasedType(
            if ("\n" in contents) "($contents)" else contents,
            element,
            context
          )
        )
      }
      return null
    }

    private fun getVariableTypeCommentType(
      contents: String,
      element: PsiElement,
      context: Context,
    ): Ref<PyType?>? {
      val expr = PyPsiUtils.flattenParens(PyUtil.createExpressionFromFragment(contents, element))
      if (expr != null) {
        if (element is PyTargetExpression && expr is PyTupleExpression) {
          // Such syntax is specific to "# type:" comments, unpacking in type hints is not allowed anywhere else
          // XXX: Switches stub to AST
          val topmostTarget: PyExpression? = findTopmostTarget(element)
          if (topmostTarget != null) {
            val targetToExpr = mapTargetsToAnnotations(topmostTarget, expr)
            val typeExpr = targetToExpr[element]
            if (typeExpr != null) {
              return getType(typeExpr, context)
            }
          }
        }
        else {
          return getType(expr, context)
        }
      }
      return null
    }

    private fun findTopmostTarget(target: PyTargetExpression): PyExpression? {
      val validTargetParent = PsiTreeUtil.getParentOfType(
        target,
        PyForPart::class.java,
        PyWithItem::class.java,
        PyAssignmentStatement::class.java
      )
      if (validTargetParent == null) {
        return null
      }
      val topmostTarget = PsiTreeUtil.findPrevParent(validTargetParent, target) as? PyExpression
      if (validTargetParent is PyForPart && topmostTarget !== validTargetParent.target) {
        return null
      }
      if (validTargetParent is PyWithItem && topmostTarget !== validTargetParent.target) {
        return null
      }
      if (validTargetParent is PyAssignmentStatement &&
          validTargetParent.rawTargets.indexOf(topmostTarget) < 0
      ) {
        return null
      }
      return topmostTarget
    }

    @JvmStatic
    fun mapTargetsToAnnotations(
      targetExpr: PyExpression,
      typeExpr: PyExpression,
    ): Map<PyTargetExpression, PyExpression> {
      val targetsNoParen = PyPsiUtils.flattenParens(targetExpr)
      val typesNoParen = PyPsiUtils.flattenParens(typeExpr)
      if (targetsNoParen == null || typesNoParen == null) {
        return mutableMapOf()
      }
      if (targetsNoParen is PySequenceExpression && typesNoParen is PySequenceExpression) {
        val result = Ref<MutableMap<PyTargetExpression?, PyExpression?>>(LinkedHashMap())
        mapTargetsToExpressions(targetsNoParen, typesNoParen, result)
        return if (result.isNull) mutableMapOf()
        else Collections.unmodifiableMap<PyTargetExpression, PyExpression>(
          result.get()
        )
      }
      else if (targetsNoParen is PyTargetExpression && typesNoParen !is PySequenceExpression) {
        return ImmutableMap.of(targetsNoParen, typesNoParen)
      }
      return emptyMap()
    }

    private fun mapTargetsToExpressions(
      targetSequence: PySequenceExpression,
      valueSequence: PySequenceExpression,
      result: Ref<MutableMap<PyTargetExpression?, PyExpression?>>,
    ) {
      val targets = targetSequence.elements
      val values = valueSequence.elements

      if (targets.size != values.size) {
        result.set(null)
        return
      }

      for (i in targets.indices) {
        val target = PyPsiUtils.flattenParens(targets[i])
        val value = PyPsiUtils.flattenParens(values[i])

        if (target == null || value == null) {
          result.set(null)
          return
        }

        if (target is PySequenceExpression && value is PySequenceExpression) {
          mapTargetsToExpressions(target, value, result)
          if (result.isNull) {
            return
          }
        }
        else if (target is PyTargetExpression && value !is PySequenceExpression) {
          val map = checkNotNull(result.get())
          map[target] = value
        }
        else {
          result.set(null)
          return
        }
      }
    }

    private fun getCallableType(resolved: PsiElement, context: Context): PyType? {
      if (resolved is PySubscriptionExpression) {
        if (resolvesToQualifiedNames(
            resolved.operand,
            context.typeContext, CALLABLE, CALLABLE_EXT
          )
        ) {
          val indexExpr = resolved.indexExpression
          if (indexExpr is PyTupleExpression) {
            val elements = indexExpr.elements
            if (elements.size == 2) {
              val parametersExpr = elements[0]
              val returnTypeExpr = elements[1]
              var returnType = Ref.deref<PyType?>(getType(returnTypeExpr, context))
              if (returnType is PyVariadicType) {
                returnType = null
              }
              if (parametersExpr is PyEllipsisLiteralExpression) {
                return PyCallableTypeImpl(null as PyCallableParameterVariadicType?, returnType)
              }
              val parametersType = Ref.deref<PyType?>(getType(parametersExpr, context))
              if (parametersType is PyCallableParameterListType) {
                return PyCallableTypeImpl(parametersType.parameters, returnType)
              }
              if (parametersType is PyCallableParameterVariadicType) {
                return PyCallableTypeImpl(parametersType, returnType)
              }
            }
          }
        }
      }
      else if (resolved is PyTargetExpression) {
        if (resolvesToQualifiedNames(
            resolved,
            context.typeContext, CALLABLE, CALLABLE_EXT
          )
        ) {
          return PyCallableTypeImpl(null as PyCallableParameterListType?, null)
        }
      }
      return null
    }

    private fun getNeverType(element: PsiElement): PyType? {
      val qName: String? = getQualifiedName(element)
      if (qName == null) return null
      if (qName in listOf(NEVER, NEVER_EXT)) {
        return PyNeverType.NEVER
      }
      if (qName in listOf(NO_RETURN, NO_RETURN_EXT)) {
        return PyNeverType.NO_RETURN
      }
      return null
    }

    private fun getUnionType(element: PsiElement, context: Context): Ref<PyType?>? {
      if (element is PySubscriptionExpression) {
        if (resolvesToQualifiedNames(element.operand, context.typeContext, UNION)) {
          val union = PyUnionType.unionOrNever(getIndexTypes(element, context))
          return if (union != null) Ref(union) else null
        }
      }
      else if (element is PyBinaryExpression && element.operator === PyTokenTypes.OR) {
        val left = element.leftExpression
        val right = element.rightExpression
        if (left == null || right == null) return null

        val leftTypeRef: Ref<PyType?>? = getType(left, context)
        val rightTypeRef: Ref<PyType?>? = getType(right, context)
        if (leftTypeRef == null || rightTypeRef == null) return null

        val leftType = leftTypeRef.get()
        if (leftType != null && typeHasOverloadedBitwiseOr(leftType, left, context)) return null

        val union = PyUnionType.union(leftType, rightTypeRef.get())
        return if (union != null) Ref(union) else null
      }
      return null
    }

    private fun getConcatenateType(element: PsiElement, context: Context): PyType? {
      if (element !is PySubscriptionExpression) return null
      if (!resolvesToQualifiedNames(element.operand, context.typeContext, CONCATENATE, CONCATENATE_EXT)) return null
      val tupleExpression = (element.indexExpression as? PyTupleExpression) ?: return null

      val arguments = tupleExpression.elements.toList()
      if (arguments.size < 2) return null

      val prefixTypeExprs = arguments.subList(0, arguments.size - 1)
      val prefixTypes = prefixTypeExprs.map { Ref.deref(getType(it!!, context.typeContext)) }

      val lastTypeExpr = arguments[arguments.size - 1]
      val paramSpecType = if (lastTypeExpr is PyEllipsisLiteralExpression) {
        null
      }
      else {
        Ref.deref(getType(lastTypeExpr, context.typeContext)) as? PyParamSpecType
        ?: return null
      }
      return PyConcatenateType(prefixTypes, paramSpecType)
    }

    private fun getTypeParameterTypeFromDeclaration(element: PsiElement, context: Context): PyTypeParameterType? {
      if (element is PyCallExpression) {
        val typeParameterKind: PyAstTypeParameter.Kind? = getTypeParameterKindFromDeclaration(
          element,
          context.typeContext
        )
        if (typeParameterKind != null) {
          val arguments = element.arguments
          val nameArgument = arguments.firstOrNull() as? PyStringLiteralExpression
          if (nameArgument is PyStringLiteralExpression) {
            val name: String = nameArgument.stringValue
            val defaultExpression = element.getKeywordArgument("default")
            val defaultType: Ref<PyType?>? = if (defaultExpression != null) getType(defaultExpression, context) else null
            when (typeParameterKind) {
              PyAstTypeParameter.Kind.TypeVarTuple -> {
                return PyTypeVarTupleTypeImpl(name)
                  .withDefaultType(
                    (Ref.deref(defaultType) as? PyPositionalVariadicType)?.let { Ref(it) }
                  )
              }

              PyAstTypeParameter.Kind.TypeVar -> {
                // TypeVar __init__ parameters:
                // (name, *constraints, bound = None, contravariant = False, covariant = False, infer_variance = False, default = ...)
                val constraints = arguments
                  .drop(1)
                  .takeWhile { it !is PyKeywordArgument }
                  .map { Ref.deref(getType(it, context)) }
                val boundExpression = element.getKeywordArgument("bound")
                val bound = if (boundExpression == null) null else Ref.deref(getType(boundExpression, context))
                val variance: PyTypeVarType.Variance = getTypeVarVarianceFromDeclaration(element)
                val assignStmt = element.getParent() as? PyAssignmentStatement
                val mappingPair = assignStmt?.targetsToValuesMapping?.firstOrNull { pair -> pair.second == element }
                val declarationElement = mappingPair?.first as? PyQualifiedNameOwner
                return PyTypeVarTypeImpl(name, constraints, bound, defaultType, variance).withDeclarationElement(declarationElement)
              }

              PyAstTypeParameter.Kind.ParamSpec -> {
                return PyParamSpecType(name)
                  .withDefaultType(
                    (Ref.deref(defaultType) as? PyCallableParameterVariadicType)?.let { Ref(it) }
                  )
              }
            }
          }
        }
      }
      return null
    }

    @Suppress("KotlinConstantConditions") // caused by systematical if conditions
    private fun getTypeVarVarianceFromDeclaration(assignedCall: PyCallExpression): PyTypeVarType.Variance {
      val covariant = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("covariant"), false)
      val contravariant = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("contravariant"), false)
      val inferVariance = PyEvaluator.evaluateAsBooleanNoResolve(assignedCall.getKeywordArgument("infer_variance"), false)

      if (covariant && !contravariant) {
        return PyTypeVarType.Variance.COVARIANT
      }
      else if (contravariant && !covariant) {
        return PyTypeVarType.Variance.CONTRAVARIANT
      }
      else if (contravariant && covariant) {
        // Note that Python does not officially support bivariance. Change this to invariant if necessary.
        return PyTypeVarType.Variance.BIVARIANT
      }
      else if (inferVariance) {
        return PyTypeVarType.Variance.INFER_VARIANCE
      }
      else {
        return PyTypeVarType.Variance.INVARIANT
      }
    }

    @ApiStatus.Internal
    fun getTypeParameterKindFromDeclaration(
      callExpression: PyCallExpression,
      context: TypeEvalContext,
    ): PyAstTypeParameter.Kind? {
      val callee = callExpression.callee
      if (callee != null) {
        val calleeQNames = resolveToQualifiedNames(callee, context)
        if (TYPE_VAR_TUPLE in calleeQNames || TYPE_VAR_TUPLE_EXT in calleeQNames) return PyAstTypeParameter.Kind.TypeVarTuple
        if (TYPE_VAR in calleeQNames || TYPE_VAR_EXT in calleeQNames) return PyAstTypeParameter.Kind.TypeVar
        if (PARAM_SPEC in calleeQNames || PARAM_SPEC_EXT in calleeQNames) return PyAstTypeParameter.Kind.ParamSpec
      }
      return null
    }

    @ApiStatus.Internal
    fun getTypeParameterTypeFromTypeParameter(
      typeParameter: PyTypeParameter,
      context: TypeEvalContext,
    ): PyTypeParameterType? {
      return staticWithCustomContext(
        context
      ) { getTypeParameterTypeFromTypeParameter(typeParameter, it) }
    }

    private fun getTypeParameterTypeFromTypeParameter(
      element: PsiElement,
      context: Context,
    ): PyTypeParameterType? {
      if (element is PyTypeParameter) {
        val name = element.name
        if (name == null) {
          return null
        }

        val typeParameterOwner = ScopeUtil.getScopeOwner(element)
        val scopeOwner: PyQualifiedNameOwner? = typeParameterOwner as? PyQualifiedNameOwner

        val defaultExpressionText = element.defaultExpressionText
        val defaultExpression = if (defaultExpressionText != null)
          PyUtil.createExpressionFromFragment(defaultExpressionText, element)
        else
          null
        var defaultType: Ref<PyType?>? = null
        if (defaultExpression != null) {
          val defaultExprWithoutParens = PyPsiUtils.flattenParens(defaultExpression)
          defaultType = if (defaultExprWithoutParens != null)
            getTypePreventingRecursion(defaultExprWithoutParens, context)
          else
            Ref()
        }

        val declarationElement = PyUtil.`as`(element, PyQualifiedNameOwner::class.java)

        when (element.kind) {
          PyAstTypeParameter.Kind.TypeVar -> {
            var constraints = listOf<PyType?>()
            var boundType: PyType? = null
            val boundExpressionText = element.boundExpressionText
            val boundExpression = if (boundExpressionText != null)
              PyPsiUtils.flattenParens(PyUtil.createExpressionFromFragment(boundExpressionText, element))
            else
              null
            if (boundExpression is PyTupleExpression) {
              constraints = boundExpression.elements.map {
                Ref.deref(
                  getTypePreventingRecursion(it!!, context)
                )
              }
            }
            else if (boundExpression != null) {
              boundType = Ref.deref<PyType?>(getTypePreventingRecursion(boundExpression, context))
            }
            val variance = if (scopeOwner is PyFunction) PyTypeVarType.Variance.INVARIANT else PyTypeVarType.Variance.INFER_VARIANCE
            return PyTypeVarTypeImpl(name, constraints, boundType, defaultType, variance)
              .withScopeOwner(scopeOwner)
              .withDeclarationElement(declarationElement)
          }

          PyAstTypeParameter.Kind.ParamSpec -> {
            return PyParamSpecType(name)
              .withScopeOwner(scopeOwner)
              .withDefaultType(
                (Ref.deref(defaultType) as? PyCallableParameterVariadicType)?.let { Ref(it) }
              )
              .withDeclarationElement(declarationElement)
          }

          PyAstTypeParameter.Kind.TypeVarTuple -> {
            return PyTypeVarTupleTypeImpl(name)
              .withScopeOwner(scopeOwner)
              .withDefaultType(
                (Ref.deref(defaultType) as? PyPositionalVariadicType)?.let { Ref(it) }
              )
              .withDeclarationElement(declarationElement)
          }
        }
      }
      return null
    }

    private fun getTypePreventingRecursion(expression: PyExpression, context: Context): Ref<PyType?>? {
      return RecursionManager.doPreventingRecursion<Ref<PyType?>?>(expression, false, Computable { getType(expression, context) })
    }

    // See https://peps.python.org/pep-0484/#scoping-rules-for-type-variables
    private fun getTypeParameterScope(
      name: String,
      typeHint: PyExpression,
      context: Context,
    ): PyQualifiedNameOwner? {
      if (!context.isComputeTypeParameterScopeEnabled) return null

      val typeHintContext: PsiElement = getStubRetainedTypeHintContext(typeHint)
      val typeParamOwnerCandidates =
        StreamEx.iterate<PsiElement?>(
          typeHintContext,
          { Objects.nonNull(it) },
          UnaryOperator { owner: PsiElement? ->
            PsiTreeUtil.getStubOrPsiParentOfType(
              owner,
              ScopeOwner::class.java
            )
          })
          .filter { owner: PsiElement? -> owner is PyFunction || owner is PyClass }
          .select(PyQualifiedNameOwner::class.java)
          .toList()

      val closestOwner = typeParamOwnerCandidates.firstOrNull()
      if (closestOwner is PyFunction) {
        val typeParameterType = StreamEx.of(typeParamOwnerCandidates)
          .skip(1)
          .map {
            findSameTypeParameterInDefinition(
              it,
              name,
              context
            )
          }
          .nonNull()
          .findFirst()
        if (typeParameterType.isPresent) {
          return typeParameterType.get().scopeOwner
        }
      }
      if (closestOwner != null) {
        val prevComputeTypeParameterScope = context.setComputeTypeParameterScopeEnabled(false)
        try {
          return if (findSameTypeParameterInDefinition(closestOwner, name, context) != null) closestOwner else null
        }
        finally {
          context.setComputeTypeParameterScopeEnabled(prevComputeTypeParameterScope)
        }
      }

      // old-style type aliases of form `ListOf = list[T]` or `ListOf: TypeAlias = list[T]`
      val assignment = PsiTreeUtil.getParentOfType(
        typeHintContext,
        PyAssignmentStatement::class.java,
        false,
        PyStatement::class.java
      )
      if (assignment != null) {
        val assignedValue = PyPsiUtils.flattenParens(assignment.assignedValue)
        if (PsiTreeUtil.isAncestor(assignedValue, typeHintContext, false)) {
          val target = PyPsiUtils.flattenParens(assignment.leftHandSideExpression)
          if (target is PyTargetExpression) {
            val isTypeParamDeclaration = assignedValue is PyCallExpression &&
                                         getTypeParameterKindFromDeclaration(assignedValue, context.typeContext) != null
            if (!isTypeParamDeclaration && PyTypingAliasStubType.looksLikeTypeHint(assignedValue!!)) {
              return target
            }
          }
        }
      }

      return null
    }

    private fun findSameTypeParameterInDefinition(
      owner: PyQualifiedNameOwner,
      name: String,
      context: Context,
    ): PyTypeParameterType? {
      // At this moment, the definition of the TypeVar should be the type alias at the top of the stack.
      // While evaluating type hints of enclosing functions' parameters, resolving to the same TypeVar
      // definition shouldn't trigger the protection against recursive aliases, so we manually remove
      // it from the top for the time being.
      if (context.isTypeAliasStackEmpty) {
        return null
      }
      val typeVarDeclaration = context.popTypeAlias()
      assert(typeVarDeclaration is PyTargetExpression)
      try {
        val typeParameters: Iterable<PyTypeParameterType?>
        when (owner) {
          is PyClass -> {
            typeParameters = collectTypeParameters(owner, context)
          }
          is PyFunction -> {
            typeParameters = collectTypeParameters(owner, context.typeContext)
          }
          else -> {
            typeParameters = mutableListOf()
          }
        }
        return typeParameters.find { name == it!!.name }
      }
      finally {
        context.pushTypeAlias(typeVarDeclaration!!)
      }
    }

    @ApiStatus.Internal
    fun collectTypeParameters(
      function: PyFunction,
      context: TypeEvalContext,
    ): Iterable<PyTypeParameterType> {
      return StreamEx.of(*function.parameterList.parameters)
        .select(PyNamedParameter::class.java)
        .map {
          PyTypingTypeProvider().getParameterType(
            it!!,
            function,
            context
          )
        }
        .append(PyTypingTypeProvider().getReturnType(function, context))
        .map { Ref.deref(it) }
        .map { it.collectGenerics(context) }
        .flatMap {
          StreamEx.of<PyTypeParameterType>(it.typeVars)
            .append(it.paramSpecs)
            .append(it.typeVarTuples)
        }
    }

    private fun getStubRetainedTypeHintContext(typeHintExpression: PsiElement): PsiElement {
      val containingFile = typeHintExpression.containingFile
      // Values from PSI stubs and regular type comments
      val fragmentOwner = containingFile.context
      if (fragmentOwner != null) {
        return fragmentOwner
      }
      else if (containingFile is PyFunctionTypeAnnotationFile || containingFile is PyTypeHintFile) {
        return PyPsiUtils.getRealContext(typeHintExpression)
      }
      else {
        return typeHintExpression
      }
    }

    fun getUnpackedType(element: PsiElement, context: TypeEvalContext): PyPositionalVariadicType? {
      var typeRef: Ref<PyType?>? = getTypeFromStarExpression(element, context)
      if (typeRef == null) {
        typeRef = getTypeFromUnpackOperator(element, context)
      }
      if (typeRef == null) {
        return null
      }
      val expressionType = typeRef.get()
      if (expressionType is PyTupleType) {
        return PyUnpackedTupleTypeImpl(expressionType.elementTypes, expressionType.isHomogeneous)
      }
      if (expressionType is PyTypeVarTupleType) {
        return expressionType
      }
      return null
    }

    private fun getTypeFromUnpackOperator(element: PsiElement, context: TypeEvalContext): Ref<PyType?>? {
      if (element !is PySubscriptionExpression ||
          !resolvesToQualifiedNames(element.operand, context, UNPACK, UNPACK_EXT)
      ) {
        return null
      }
      val indexExpression = element.indexExpression
      if (!(indexExpression is PyReferenceExpression || indexExpression is PySubscriptionExpression)) return null
      return Ref(Ref.deref<PyType?>(getType(indexExpression, context)))
    }

    private fun getTypeFromStarExpression(element: PsiElement, context: TypeEvalContext): Ref<PyType?>? {
      if (element !is PyStarExpression) return null
      val starredExpression = element.expression
      if (!(starredExpression is PyReferenceExpression || starredExpression is PySubscriptionExpression)) return null
      return Ref(Ref.deref<PyType?>(getType(starredExpression, context)))
    }

    private fun getIndexTypes(expression: PySubscriptionExpression, context: Context): MutableList<PyType?> {
      val types: MutableList<PyType?> = ArrayList()
      val indexExpr = PyPsiUtils.flattenParens(expression.indexExpression)
      if (indexExpr is PyTupleExpression) {
        for (expr in indexExpr.elements) {
          types.add(Ref.deref<PyType?>(getType(expr, context)))
        }
      }
      else if (indexExpr != null) {
        types.add(Ref.deref<PyType?>(getType(indexExpr, context)))
      }
      return types
    }

    private fun parameterizeClassDefaultAware(
      pyClass: PyClass,
      actualTypeParams: List<PyType?>,
      context: Context,
    ): PyCollectionType? {
      val genericDefinitionType =
        RecursionManager.doPreventingRecursion<PyCollectionType?>(pyClass, false, Computable {
          PyTypeChecker.findGenericDefinitionType(
            pyClass,
            context.typeContext
          )
        })
      if (genericDefinitionType != null && genericDefinitionType.elementTypes.any {
          it is PyTypeParameterType && it.defaultType != null
        }
      ) {
        val parameterizedType = PyTypeChecker.parameterizeType(genericDefinitionType, actualTypeParams, context.typeContext)
        if (parameterizedType is PyCollectionType) {
          return parameterizedType
        }
      }
      return null
    }

    private fun getTypeFromTypeAlias(
      alias: PyQualifiedNameOwner,
      typeHint: PsiElement,
      element: PsiElement,
      context: Context,
    ): Ref<PyType?>? {
      if (element is PyExpression) {
        if (alias is PyTypeAliasStatement) {
          return getTypeFromTypeAliasStatement(alias, typeHint, element, context)
        }

        val assignedTypeRef: Ref<PyType?>? = getType(element, context)
        if (assignedTypeRef != null) {
          val assignedType = assignedTypeRef.get()
          if (assignedType == null) {
            return assignedTypeRef
          }
          if (typeHint is PySubscriptionExpression) {
            val indexTypes: MutableList<PyType?> = getIndexTypes(typeHint, context)
            return Ref(PyTypeChecker.parameterizeType(assignedType, indexTypes, context.typeContext))
          }
          if (typeHint is PyReferenceExpression) {
            if (assignedType !is PyTypeParameterType) {
              val typeAliasTypeParams =
                assignedType.collectGenerics(context.typeContext).allTypeParameters
              if (!typeAliasTypeParams.isEmpty()) {
                return Ref(
                  PyTypeChecker.parameterizeType(
                    assignedType,
                    mutableListOf<PyType?>(),
                    context.typeContext
                  )
                )
              }
              return Ref(assignedType)
            }
          }
        }
      }
      return null
    }

    private fun getTypeFromTypeAliasStatement(
      typeAliasStatement: PyTypeAliasStatement,
      typeHint: PsiElement,
      assignedExpression: PyExpression,
      context: Context,
    ): Ref<PyType?>? {
      val assignedTypeRef: Ref<PyType?>? = getType(assignedExpression, context)
      if (assignedTypeRef != null) {
        val assignedType = assignedTypeRef.get()
        if (assignedType == null) {
          return assignedTypeRef
        }
        val indexTypes = if (typeHint is PySubscriptionExpression)
          getIndexTypes(typeHint, context)
        else mutableListOf()

        val typeAliasTypeParams: MutableList<PyTypeParameterType?> =
          collectTypeParametersFromTypeAliasStatement(typeAliasStatement, context)
        if (!typeAliasTypeParams.isEmpty()) {
          val substitutions =
            PyTypeChecker.mapTypeParametersToSubstitutions(
              typeAliasTypeParams,
              indexTypes,
              PyTypeParameterMapping.Option.USE_DEFAULTS,
              PyTypeParameterMapping.Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY
            )

          return if (substitutions != null) Ref(
            PyTypeChecker.substitute(
              assignedType,
              substitutions,
              context.typeContext
            )
          )
          else null
        }
        return assignedTypeRef
      }
      return null
    }

    private fun getParameterizedType(element: PsiElement, context: Context): PyType? {
      if (element is PySubscriptionExpression) {
        val operand = element.operand
        val indexExpr = element.indexExpression
        if (indexExpr != null) {
          val operandType = Ref.deref<PyType?>(getType(operand, context))
          val indexTypes: MutableList<PyType?> = getIndexTypes(element, context)
          if (operandType != null) {
            if (operandType is PyClassType) {
              if (operandType !is PyTupleType && PyNames.TUPLE == operandType.pyClass.qualifiedName) {
                if (indexExpr is PyTupleExpression) {
                  val arguments = indexExpr.elements.map { PyPsiUtils.flattenParens(it) }

                  if (arguments.lastOrNull() is PyEllipsisLiteralExpression) {
                    if (arguments.size != 2) return null
                    if (arguments.first() is PyEllipsisLiteralExpression) return null

                    val indexType = indexTypes.first()
                    if (indexType is PyPositionalVariadicType) return null

                    return PyTupleType.createHomogeneous(element, indexType)
                  }
                  else {
                    for (argument in arguments) {
                      if (argument is PyEllipsisLiteralExpression) return null
                      if (argument is PyTupleExpression && argument.elements.isEmpty()) {
                        if (arguments.size != 1) return null
                      }
                    }
                    return PyTupleType.create(element, indexTypes)
                  }
                }
                else {
                  if (indexExpr is PyEllipsisLiteralExpression) return null
                  return PyTupleType.create(element, indexTypes)
                }
              }

              if (isGeneric(operandType, context.typeContext)) {
                parameterizeClassDefaultAware(operandType.pyClass, indexTypes, context)?.let {
                  return it.toInstance()
                }
              }
              else {
                return null
              }
            }
            return PyTypeChecker.parameterizeType(operandType, indexTypes, context.typeContext)
          }
        }
      }
      return null
    }

    private fun getCollection(element: PsiElement, context: TypeEvalContext): PyType? {
      val typingName: String? = getQualifiedName(element)

      val builtinName: String? = BUILTIN_COLLECTION_CLASSES[typingName]
      if (builtinName != null) return PyTypeParser.getTypeByName(element, builtinName, context)

      val collectionName: String? = COLLECTIONS_CLASSES[typingName]
      if (collectionName != null) return PyTypeParser.getTypeByName(element, collectionName, context)

      return null
    }

    private fun tryResolving(expression: PyExpression, context: TypeEvalContext): List<PsiElement> {
      return tryResolvingWithAliases(expression, context).map { it.second!! }
    }

    private fun tryResolvingWithAliases(
      expression: PyExpression,
      context: TypeEvalContext,
    ): List<Pair<PyQualifiedNameOwner?, PsiElement?>> {
      val elements: MutableList<Pair<PyQualifiedNameOwner?, PsiElement?>> = ArrayList()
      if (expression is PyReferenceExpression) {
        val results: MutableList<PsiElement?>
        if (context.maySwitchToAST(expression)) {
          val resolveContext = PyResolveContext.defaultContext(context)
          results = PyUtil.multiResolveTopPriority(expression, resolveContext)
        }
        else {
          results = tryResolvingOnStubs(expression, context)
        }
        for (element in results) {
          val cls = PyUtil.turnConstructorIntoClass(element as? PyFunction)
          if (cls != null) {
            elements.add(null to cls)
            continue
          }
          val name: String? = if (element != null) getQualifiedName(element) else null
          if (name != null && name in OPAQUE_NAMES) {
            elements.add(null to element)
            continue
          }
          // Presumably, a TypeVar definition or a type alias
          if (element is PyTargetExpression) {
            val assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(element)
            if (assignedValue != null) {
              elements.add(element to assignedValue)
              continue
            }
          }
          if (element is PyTypeAliasStatement) {
            val assignedValue: PyExpression?
            if (context.maySwitchToAST(element)) {
              assignedValue = element.typeExpression
            }
            else {
              val assignedTypeText = element.typeExpressionText
              assignedValue =
                if (assignedTypeText != null) PyUtil.createExpressionFromFragment(assignedTypeText, element) else null
            }
            if (assignedValue != null) {
              elements.add(element to assignedValue)
              continue
            }
          }
          if (element != null) {
            elements.add(null to element)
          }
        }
      }
      if (expression is PySubscriptionExpression) {
        // Possibly a parameterized type alias
        val operandExpression = expression.operand
        val results = tryResolvingWithAliases(operandExpression, context)
        for (pair in results) {
          // If the parameterized type is a type alias
          if (pair.first != null && pair.second != null) {
            elements.add(pair.first to pair.second)
          }
        }
      }
      return elements.ifEmpty { listOf(null to expression) }
    }

    private fun tryResolvingOnStubs(
      expression: PyReferenceExpression,
      context: TypeEvalContext,
    ): MutableList<PsiElement?> {
      val qualifiedName = expression.asQualifiedName()
      val pyFile = PyUtil.`as`(FileContextUtil.getContextFile(expression), PyFile::class.java)

      val anchor = expression.containingFile.context
      val scopeOwner: ScopeOwner?

      when (anchor) {
        null -> {
          scopeOwner = pyFile
        }
        is ScopeOwner -> {
          scopeOwner = anchor
        }
        else -> {
          scopeOwner = ScopeUtil.getScopeOwner(anchor)
        }
      }

      if (scopeOwner != null && qualifiedName != null) {
        return PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context)
      }
      return mutableListOf(expression)
    }

    fun resolveToQualifiedNames(expression: PyExpression, context: TypeEvalContext): Collection<String> {
      return buildSet {
        for (resolved in tryResolving(expression, context)) {
          val name: String? = getQualifiedName(resolved)
          if (name != null) {
            add(name)
          }
        }
      }
    }

    private fun getQualifiedName(element: PsiElement): String? {
      if (element is PyQualifiedNameOwner) {
        return element.qualifiedName
      }
      return null
    }

    @JvmStatic
    fun toAsyncIfNeeded(function: PyFunction, returnType: PyType?): PyType? {
      if (function.isAsync && function.isAsyncAllowed) {
        if (!function.isGenerator) {
          return wrapInCoroutineType(returnType, function)
        }
        val desc = GeneratorTypeDescriptor.fromGenerator(returnType)
        if (desc != null) {
          val classType = PyPsiFacade.getInstance(function.project).createClassByQName(ASYNC_GENERATOR, function)
          val generics = listOf(desc.yieldType, desc.sendType)
          return if (classType != null) PyCollectionTypeImpl(classType, false, generics) else null
        }
      }
      return returnType
    }

    /**
     * Bound narrowed types shouldn't leak out of its scope, since it is bound to a particular call site.
     */
    @JvmStatic
    fun removeNarrowedTypeIfNeeded(type: PyType?): PyType? {
      if (type is PyNarrowedType && type.isBound()) {
        return getInstance(type.original).boolType
      }
      else {
        return type
      }
    }

    private fun wrapInCoroutineType(returnType: PyType?, resolveAnchor: PsiElement): PyType? {
      val coroutine = PyPsiFacade.getInstance(resolveAnchor.project).createClassByQName(COROUTINE, resolveAnchor)
      return if (coroutine != null) PyCollectionTypeImpl(coroutine, false, listOf(null, null, returnType)) else null
    }

    @JvmStatic
    fun wrapInGeneratorType(
      elementType: PyType?,
      sendType: PyType?,
      returnType: PyType?,
      anchor: PsiElement,
    ): PyType? {
      val generator = PyPsiFacade.getInstance(anchor.project).createClassByQName(GENERATOR, anchor)
      return if (generator != null) PyCollectionTypeImpl(
        generator,
        false,
        listOf(elementType, sendType, returnType)
      )
      else null
    }

    @JvmStatic
    fun unwrapCoroutineReturnType(coroutineType: PyType?): Ref<PyType?>? {
      val genericType = PyUtil.`as`(coroutineType, PyCollectionType::class.java)

      if (genericType != null) {
        val qName = genericType.classQName

        if (AWAITABLE == qName) {
          return Ref(ContainerUtil.getOrElse<PyType?>(genericType.elementTypes, 0, null))
        }

        if (COROUTINE == qName) {
          return Ref(ContainerUtil.getOrElse<PyType?>(genericType.elementTypes, 2, null))
        }
      }

      return null
    }

    @JvmStatic
    fun coroutineOrGeneratorElementType(coroutineOrGeneratorType: PyType?): Ref<PyType?>? {
      val genericType = PyUtil.`as`(coroutineOrGeneratorType, PyCollectionType::class.java)
      val classType = PyUtil.`as`(coroutineOrGeneratorType, PyClassType::class.java)

      if (genericType != null && classType != null) {
        val qName = classType.classQName

        if (AWAITABLE == qName) {
          return Ref(ContainerUtil.getOrElse<PyType?>(genericType.elementTypes, 0, null))
        }

        if (ArrayUtil.contains(qName, COROUTINE, GENERATOR)) {
          return Ref(ContainerUtil.getOrElse<PyType?>(genericType.elementTypes, 2, null))
        }
      }

      return null
    }

    /**
     * Checks whether the given assignment is type hinted with `typing.TypeAlias`.
     *
     *
     * It can be done either with a variable annotation or a type comment.
     */
    @JvmStatic
    fun isExplicitTypeAlias(assignment: PyAssignmentStatement, context: TypeEvalContext): Boolean {
      val target = assignment.targets.firstOrNull() as? PyTargetExpression ?: return false
      return isExplicitTypeAlias(target, context)
    }

    fun isExplicitTypeAlias(targetExpression: PyTargetExpression, context: TypeEvalContext): Boolean {
      val annotationValue: PyExpression? = getAnnotationValue(targetExpression, context)
      if (annotationValue is PyReferenceExpression) {
        return resolvesToQualifiedNames(annotationValue, context, TYPE_ALIAS, TYPE_ALIAS_EXT)
      }
      val typeCommentAnnotation = targetExpression.typeCommentAnnotation
      if (typeCommentAnnotation != null) {
        val commentValue = PyUtil.createExpressionFromFragment(typeCommentAnnotation, targetExpression)
        if (commentValue is PyReferenceExpression) {
          return resolvesToQualifiedNames(commentValue, context, TYPE_ALIAS, TYPE_ALIAS_EXT)
        }
      }
      return false
    }

    /**
     * Detects whether the given element belongs to a self-evident type hint. Namely, these are:
     *
     *  * function and variable annotations
     *  * type comments
     *  * explicit type aliases marked with `TypeAlias`
     *
     * Note that `element` can belong to their AST directly or be a part of an injection inside one of such elements.
     */
    @JvmStatic
    fun isInsideTypeHint(element: PsiElement, context: TypeEvalContext): Boolean {
      val realContext = PyPsiUtils.getRealContext(element)

      if (PsiTreeUtil.getParentOfType(realContext, PyAnnotation::class.java, false, PyStatement::class.java) != null) {
        return true
      }

      val comment = PsiTreeUtil.getParentOfType(realContext, PsiComment::class.java, false, PyStatement::class.java)
      if (comment != null && getTypeCommentValue(comment.text) != null) {
        return true
      }

      val assignment = PsiTreeUtil.getParentOfType(
        realContext,
        PyAssignmentStatement::class.java,
        false,
        PyStatement::class.java
      )
      if (assignment != null &&
          PsiTreeUtil.isAncestor(assignment.assignedValue, realContext, false) &&
          isExplicitTypeAlias(assignment, context)
      ) {
        return true
      }

      val typeAlias = PsiTreeUtil.getParentOfType(
        realContext,
        PyTypeAliasStatement::class.java,
        false,
        PyStatement::class.java
      )
      return typeAlias != null && PsiTreeUtil.isAncestor(typeAlias.typeExpression, realContext, false)
    }

    private fun <T> staticWithCustomContext(context: TypeEvalContext, delegate: (Context) -> T): T {
      return staticWithCustomContext(context, false, delegate)
    }

    private fun <T> staticWithCustomContext(
      context: TypeEvalContext,
      useFqn: Boolean,
      delegate: (Context) -> T,
    ): T {
      var customContext = context.processingContext.get(TYPE_HINT_EVAL_CONTEXT)
      val firstEntrance = customContext == null
      if (firstEntrance) {
        customContext = Context(context, useFqn)
        context.processingContext.put(TYPE_HINT_EVAL_CONTEXT, customContext)
      }
      try {
        return delegate(customContext)
      }
      finally {
        if (firstEntrance) {
          context.processingContext.put<Context?>(TYPE_HINT_EVAL_CONTEXT, null)
        }
      }
    }
  }
}
