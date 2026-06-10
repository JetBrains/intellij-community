// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyCallExpressionHelper")

package com.jetbrains.python.psi.impl

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.ProtectionLevel
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyPossibleClassMember
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyReferenceOwner
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.getCalleeType
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.mapArguments
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.getCalleeType
import com.jetbrains.python.psi.impl.PyCallExpressionHelper.mapArguments
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult
import com.jetbrains.python.psi.resolve.QualifiedResolveResult
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableParameterListType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyConcatenateType
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyInstantiableType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyParamSpecType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory
import com.jetbrains.python.psi.types.PyTypeMember
import com.jetbrains.python.psi.types.PyTypeUtil.components
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isAny
import com.jetbrains.python.psi.types.isNoneType
import com.jetbrains.python.psi.types.isUnknown
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.toolbox.Maybe
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min
import com.intellij.openapi.util.Pair as JBPair

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 */
object PyCallExpressionHelper {
  /**
   * Tries to interpret a call as a call to built-in `classmethod` or `staticmethod`.
   *
   * @param expression the possible call, generally a result of chasing a chain of assignments
   * @return a pair of wrapper name and wrapped function; for `staticmethod(foo)` it would be ("staticmethod", foo).
   */
  @JvmStatic
  fun interpretAsModifierWrappingCall(expression: PyCallExpression): JBPair<String, PyFunction>? {
    val redefiningCallee = expression.callee
    if (!expression.isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD)) return null
    val referenceExpr = redefiningCallee as? PyReferenceExpression? ?: return null
    val refName = referenceExpr.referencedName
    if (!(PyNames.CLASSMETHOD == refName || PyNames.STATICMETHOD == refName) || !PyBuiltinCache.isInBuiltins(referenceExpr)) return null
    // yes, really a case of "foo = classmethod(foo)"
    val argumentList = expression.argumentList ?: return null
    // really can't be any other way
    val possibleOriginalRef = argumentList.arguments.firstOrNull() as? PyReferenceExpression ?: return null
    val original = possibleOriginalRef.reference.resolve() as? PyFunction ?: return null
    // pinned down the original; replace our resolved callee with it and add flags.
    return JBPair.create(refName, original)
  }

  @JvmStatic
  fun resolveCalleeClass(expression: PyCallExpression): PyClass? {
    val callee = expression.callee

    val resolveResult: QualifiedResolveResult?
    val resolved: PsiElement? =
      if (callee is PyReferenceExpression) {
        // dereference
        resolveResult = callee.followAssignmentsChain(
          PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(callee.project)))
        resolveResult.element
      }
      else {
        callee
      }
    // analyze
    if (resolved is PyClass) {
      return resolved
    }
    else if (resolved is PyFunction) {
      return resolved.containingClass
    }

    return null
  }

  /**
   * This method should not be called directly,
   * please obtain its result via [TypeEvalContext.getType] with `call.getCallee()` as an argument.
   */
  @JvmStatic
  fun getCalleeType(expression: PyCallExpression, resolveContext: PyResolveContext): PyType? {

    val results = PyUtil.filterTopPriorityResults(
      forEveryScopeTakeOverloadsOtherwiseImplementations(multipleResolveCallee(expression.callee, resolveContext),
                                                         resolveContext.typeEvalContext) { it.element }
    )

    val callableTypes = mutableListOf<PyType?>()
    for (resolveResult in results) {
      val element = resolveResult.element
      val clarified = clarifyResolveResult(resolveResult, resolveContext)

      val typeFromProviders = if (element != null) {
        val typeFromProviders =
          PyReferenceExpressionImpl.getReferenceTypeFromProviders(element, resolveContext.typeEvalContext, expression)

        typeFromProviders?.get()
      }
      else {
        null
      }

      val result = mutableListOf<PyType?>()
      if (clarified != null) {
        typeFromProviders.toStream().forEach {
          ContainerUtil.addIfNotNull(result, toCallableType(expression, clarified, it, resolveContext.typeEvalContext))
        }

        if (result.isEmpty()) {
          val clarifiedResolved = clarified.clarifiedResolved as? PyTypedElement ?: continue
          val resolvedType = resolveContext.typeEvalContext.getType(clarifiedResolved)
          resolvedType.toStream().forEach { itemType ->
            ContainerUtil.addIfNotNull(
              result,
              toCallableType(expression, clarified, itemType, resolveContext.typeEvalContext)
            )
          }
        }
      }

      callableTypes.addAll(result)
    }

    return PyUnionType.union(callableTypes)
  }

  /**
   * It is not the same as [getCalleeType] since
   * this method returns callable types that would be actually called, the mentioned method returns type of underlying callee.
   * Compare:
   * ```
   * class A:
   *     pass
   * a = A()
   * b = a()  # callee type is A, resolved callee is A.__call__
   * ```
   */
  @JvmStatic
  fun multiResolveCallee(expression: PyCallExpression, resolveContext: PyResolveContext): List<PyCallableType> {
    return PyUtil.getParameterizedCachedValue(expression, resolveContext) {
      getExplicitResolveResults(expression, it) +
      getImplicitResolveResults(expression, it) +
      getRemoteResolveResults(expression, it)
    }
  }

  private fun multipleResolveCallee(owner: PyReferenceOwner, resolveContext: PyResolveContext): List<PyCallableType> {
    val context = resolveContext.typeEvalContext

    val results = forEveryScopeTakeOverloadsOtherwiseImplementations(owner.getReference(resolveContext)
                                                                       .multiResolve(false)
                                                                       .toList(), context)

    return results.selectCallableTypes(context)
  }

  private fun getExplicitResolveResults(expression: PyCallExpression, resolveContext: PyResolveContext): List<PyCallableType> {
    val callee = expression.callee
    if (callee == null) return listOf()

    val context = resolveContext.typeEvalContext
    val calleeType = context.getType(callee)

    val provided = PyTypeProvider.EP_NAME.extensionList.mapNotNull { it.prepareCalleeTypeForCall(calleeType, expression, context) }
    if (!provided.isEmpty()) {
      return provided.mapNotNull { Ref.deref(it) }
    }

    val result = mutableListOf<PyCallableType>()

    for (type in calleeType.toStream()) {
      when (type) {
        // When invoking cls(), turn type[Self] into Self.
        // Otherwise, we will delegate to __init__() of its scope class and return a concrete type class
        // as a call result, losing Self.
        // See e.g. Py3TypeCheckerInspectionTest.testSelfInClassMethods
        is PySelfType -> {
          result.add(type)
        }
        is PyClassType -> {
          result.addAll(createCallableFromClass(type, expression, resolveContext))
        }
        is PyCallableType -> {
          result.add(type)
        }
      }
    }

    return result
  }

  private fun getImplicitResolveResults(expression: PyCallExpression, resolveContext: PyResolveContext): List<PyCallableType> {
    if (!resolveContext.allowImplicits()) return listOf()

    val callee = expression.callee
    val context = resolveContext.typeEvalContext
    if (callee is PyQualifiedExpression) {
      val referencedName = callee.referencedName
      if (referencedName == null) return listOf()

      val qualifier = callee.qualifier
      if (qualifier == null || !canQualifyAnImplicitName(qualifier)) return listOf()

      val qualifierType = context.getType(qualifier)
      if (PyTypeChecker.isUnknown(qualifierType, context) ||
          qualifierType is PyStructuralType && qualifierType.isInferredFromUsages
      ) {
        val resolveResults = ResolveResultList()
        PyResolveUtil.addImplicitResolveResults(referencedName, resolveResults, callee)

        return forEveryScopeTakeOverloadsOtherwiseImplementations(resolveResults, context).selectCallableTypes(context)
      }
    }

    return listOf()
  }

  private fun getRemoteResolveResults(expression: PyCallExpression, resolveContext: PyResolveContext): List<PyCallableType> {
    if (!resolveContext.allowRemote()) return mutableListOf()
    val file = expression.containingFile
    if (file == null || !PythonRuntimeService.getInstance().isInPydevConsole(file)) return mutableListOf()
    val calleeType = getCalleeType(expression, resolveContext)
    return calleeType.components.filterIsInstance<PyCallableType>()
  }

  private fun List<PsiElement>.selectCallableTypes(context: TypeEvalContext): List<PyCallableType> {
    return this
      .filterIsInstance<PyTypedElement>()
      .map { context.getType(it) }
      .flatMap { it.toStream() }
      .filterIsInstance<PyCallableType>()
  }

  private fun multipleResolveCallee(expression: PyExpression?, resolveContext: PyResolveContext): List<QualifiedRatedResolveResult> {
    return when (expression) {
      is PyReferenceExpression -> expression.multiFollowAssignmentsChain(resolveContext)
      is PyLambdaExpression -> listOf(QualifiedRatedResolveResult(expression,
                                                                  listOf<PyExpression?>(),
                                                                  RatedResolveResult.RATE_NORMAL,
                                                                  false))
      else -> listOf()
    }
  }

  private fun clarifyResolveResult(result: QualifiedRatedResolveResult, resolveContext: PyResolveContext): ClarifiedResolveResult? {
    val resolved = result.element

    if (resolved is PyCallExpression) { // foo = classmethod(foo)

      val wrapperInfo = interpretAsModifierWrappingCall(resolved)
      if (wrapperInfo != null) {
        val wrapperName = wrapperInfo.first
        val wrappedModifier = if (PyNames.CLASSMETHOD == wrapperName)
          PyAstFunction.Modifier.CLASSMETHOD
        else
          if (PyNames.STATICMETHOD == wrapperName)
            PyAstFunction.Modifier.STATICMETHOD
          else
            null

        return ClarifiedResolveResult(result, wrapperInfo.second, wrappedModifier)
      }
    }
    else if (resolved is PyFunction) {
      val context = resolveContext.typeEvalContext

      val property = resolved.property
      if (property != null && property.getter.valueOrNull() == resolved && isQualifiedByInstance(resolved, result.qualifiers, context)) {
        val type = context.getReturnType(resolved)

        return if (type is PyFunctionType) ClarifiedResolveResult(result, type.callable, null) else null
      }
    }

    return if (resolved != null) ClarifiedResolveResult(result, resolved, null) else null
  }

  private fun toCallableType(
    callExpression: PyCallExpression,
    resolveResult: ClarifiedResolveResult,
    inferredType: PyType?,
    context: TypeEvalContext,
  ): PyCallableType? {
    val clarifiedResolved = resolveResult.clarifiedResolved as? PyTypedElement ?: return null

    val callableType = inferredType as? PyCallableType ?: return null

    if (clarifiedResolved is PyCallable) {
      val originalModifier = if (clarifiedResolved is PyFunction) clarifiedResolved.modifier else null
      val resolvedModifier = originalModifier ?: resolveResult.wrappedModifier

      val qualifiers = resolveResult.originalResolveResult.qualifiers

      val isByInstance = isQualifiedByInstance(clarifiedResolved, qualifiers, context)

      val lastQualifier = qualifiers.lastOrNull()
      val isByClass = lastQualifier != null && isQualifiedByClass(clarifiedResolved, lastQualifier, context)

      val resolvedImplicitOffset =
        getImplicitArgumentCount(clarifiedResolved, resolvedModifier, false, isByInstance, isByClass)

      if (callableType.modifier == resolvedModifier && callableType.implicitOffset == resolvedImplicitOffset) {
        return callableType
      }

      return PyCallableTypeImpl(
        callableType.getTypeParameters(context),
        callableType.getParametersType(context),
        callableType.getCallType(context, callExpression),
        clarifiedResolved,
        resolvedModifier,
        max(0, resolvedImplicitOffset)) // wrong source can trigger strange behaviour
    }

    return callableType
  }

  /**
   * Calls the [.getImplicitArgumentCount] (full version)
   * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
   *
   * @param expression@getImplicitArgumentCount the call site, where arguments are given.
   * @param function      resolved method which is being called; plain functions are OK but make little sense.
   * @return a non-negative number of parameters that are implicit to this call.
   */
  @JvmStatic
  fun getImplicitArgumentCount(expression: PyExpression, function: PyFunction, resolveContext: PyResolveContext): Int {
    val type = resolveContext.typeEvalContext.getType(expression)
    val isConstructorCall = type is PyClassType && type.isDefinition
    if (isConstructorCall) {
      return getImplicitArgumentCount(function, function.modifier, true, false, false)
    }
    // TODO (PY-89079): Incorrect offset when the callee is not a reference expression
    if (expression !is PyReferenceExpression) {
      return 0;
    }
    val followed = expression.followAssignmentsChain(resolveContext)
    val qualifiers = followed.qualifiers
    val firstQualifier = qualifiers.firstOrNull()
    val isByInstance = isQualifiedByInstance(function, qualifiers, resolveContext.typeEvalContext)
    val isByClass = firstQualifier != null && isQualifiedByClass(function, firstQualifier, resolveContext.typeEvalContext)
    return getImplicitArgumentCount(function, function.modifier, false, isByInstance, isByClass)
  }

  /**
   * Finds how many arguments are implicit in a given call.
   *
   * @param callable@getImplicitArgumentCount     resolved method which is being called; non-methods immediately return 0.
   * @param isByInstance true if the call is known to be by instance (not by class).
   * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
   * because one parameter ('self') is implicit.
   */
  private fun getImplicitArgumentCount(
    callable: PyCallable,
    modifier: PyAstFunction.Modifier?,
    isConstructorCall: Boolean,
    isByInstance: Boolean,
    isByClass: Boolean,
  ): Int {
    var implicitOffset = 0
    var firstIsArgsOrKwargs = false
    val parameters = callable.parameterList.parameters
    if (parameters.size > 0) {
      val first = parameters[0]
      val named = first.asNamed
      if (named != null && (named.isPositionalContainer || named.isKeywordContainer)) {
        firstIsArgsOrKwargs = true
      }
    }
    if (!firstIsArgsOrKwargs && (isByInstance || isConstructorCall)) {
      implicitOffset += 1
    }
    val method = callable.asMethod() ?: return implicitOffset

    if (PyUtil.isNewMethod(method)) {
      return if (isConstructorCall) 1 else 0
    }
    if (!isByInstance && !isByClass && PyUtil.isInitMethod(method)) {
      return 1
    }

    // decorators?
    if (modifier == PyAstFunction.Modifier.STATICMETHOD) {
      if (isByInstance && implicitOffset > 0) implicitOffset -= 1 // might have marked it as implicit 'self'
    }
    else if (modifier == PyAstFunction.Modifier.CLASSMETHOD) {
      if (!isByInstance) implicitOffset += 1 // Both Foo.method() and foo.method() have implicit the first arg
    }
    return implicitOffset
  }

  private fun isQualifiedByInstance(callable: PyCallable?, qualifiers: List<PyExpression?>, context: TypeEvalContext): Boolean {
    val owner = PsiTreeUtil.getStubOrPsiParentOfType(callable, PyDocStringOwner::class.java)
    if (owner !is PyClass) {
      return false
    }
    // true = call by instance
    if (qualifiers.isEmpty()) {
      return true // unqualified + method = implicit constructor call
    }
    for (qualifier in qualifiers) {
      if (qualifier == null) continue
      val byInstance = isQualifiedByInstance(callable, qualifier, context)
      if (byInstance != ThreeState.UNSURE) {
        return byInstance.toBoolean()
      }
    }
    return true
  }

  private fun isQualifiedByInstance(callable: PyCallable?, qualifier: PyExpression, context: TypeEvalContext): ThreeState {
    if (isQualifiedByClass(callable, qualifier, context)) {
      return ThreeState.NO
    }
    val qualifierType = context.getType(qualifier)
    // TODO: handle UnionType
    if (qualifierType is PyModuleType) {
      return ThreeState.UNSURE
    }
    return ThreeState.YES // NOTE. best guess: unknown qualifier is more probably an instance.
  }

  private fun isQualifiedByClass(callable: PyCallable?, qualifier: PyExpression, context: TypeEvalContext): Boolean {
    val qualifierType = context.getType(qualifier)

    if (qualifierType is PyClassType) {
      return qualifierType.isDefinition() && belongsToSpecifiedClassHierarchy(callable, qualifierType.pyClass, context)
    }
    else if (qualifierType is PyClassLikeType) {
      return qualifierType.isDefinition() // Any definition means callable is classmethod
    }
    else if (qualifierType is PyUnionType) {
      val members = qualifierType.members

      if (members.all { it == null || it.isNoneType || it is PyClassType }) {
        return members
          .filterIsInstance<PyClassType>()
          .filter { belongsToSpecifiedClassHierarchy(callable, it.pyClass, context) }
          .all { it.isDefinition() }
      }
    }

    return false
  }

  private fun belongsToSpecifiedClassHierarchy(element: PsiElement?, cls: PyClass, context: TypeEvalContext): Boolean {
    val parent = PsiTreeUtil.getStubOrPsiParentOfType(element, PyClass::class.java)
    return parent != null && (cls.isSubclass(parent, context) || parent.isSubclass(cls, context))
  }

  /**
   * This method should not be called directly,
   * please obtain its result via [TypeEvalContext.getType] with `call` as an argument.
   */
  @JvmStatic
  fun getCallType(expression: PyCallExpression, context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
    val callee = expression.callee
    if (callee is PyReferenceExpression) {
      // hardwired special cases
      if (PyNames.SUPER == callee.text) {
        val superCallType = getSuperCallType(expression, context)
        if (superCallType.isDefined) {
          return superCallType.value()
        }
      }
      if ("type" == callee.text) {
        val args = expression.arguments
        if (args.size == 1) {
          val arg = args[0]
          val argType = context.getType(arg)
          if (argType is PyClassType) {
            if (!argType.isDefinition()) {
              val cls = argType.pyClass
              return context.getType(cls)
            }
          }
          else {
            return null
          }
        }
      }
    }
    val resolveContext = PyResolveContext.defaultContext(context)
    return getCallType(expression.multiResolveCallee(resolveContext), expression, context)
  }

  /**
   * This method should not be called directly,
   * please obtain its result via [TypeEvalContext.getType] with `subscription` as an argument.
   */
  @JvmStatic
  fun getCallType(expression: PySubscriptionExpression, context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
    val resolveContext = PyResolveContext.defaultContext(context)
    return getCallType(multipleResolveCallee(expression as PyReferenceOwner, resolveContext), expression, context)
  }

  private fun getCallType(types: List<PyCallableType>, callSite: PyCallSiteOwner, context: TypeEvalContext): PyType? {
    return types.filter { it.isCallable }
      .groupBy {
        val callable = it.callable
        ScopeUtil.getScopeOwner(callable) to (callable != null && PyiUtil.isOverload(callable, context))
      }
      .values.flatMap {
        getSameScopeCallablesCallTypes(it, callSite, context)
      }
      .let(PyUnionType::union)
  }

  /**
   * CPython's runtime rule "prefer rhs.__r<op>__ if rhs is a strict subclass of lhs" is not reproduced,
   * as it relies on runtime classes rather than annotations, making annotation-based approximation unsound.
   *
   * @see <a href="https://github.com/astral-sh/ty/issues/1154">#1154</a>
   * @see <a href="https://github.com/astral-sh/ty/issues/630">#630</a>
   */
  @JvmStatic
  fun getCallType(expression: PyBinaryExpression, context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
    val leftExpr = expression.leftExpression ?: return null
    val rightExpr = expression.rightExpression ?: return null

    val resolveContext = PyResolveContext.defaultContext(context)
    val callableTypes = multipleResolveCallee(expression as PyReferenceOwner, resolveContext)
    val matchingCallableTypes = callableTypes.filter { callableType ->
      val callable = callableType.callable
      callable is PyFunction && matchesByArgumentTypes(callable, expression, context)
    }

    val leftType = context.getType(leftExpr)
    val rightType = context.getType(rightExpr)

    val isTypeUnionSyntax = expression.isOperator("|") &&
                            (leftType is PyClassType && leftType.isDefinition ||
                             rightType is PyClassType && rightType.isDefinition)

    if (PyTypingTypeProvider.isInsideTypeHint(expression, context) || isTypeUnionSyntax) {
      return getCallType(matchingCallableTypes.ifEmpty { callableTypes }, expression, context)
    }

    val normalOperators = matchingCallableTypes.filter { !expression.isRightOperator(it.callable) }

    return if (normalOperators.isNotEmpty() && areAllTypesCoveredByCandidates(leftExpr, normalOperators, context)) {
      getCallType(normalOperators, expression, context)
    }
    else {
      getCallType(matchingCallableTypes.ifEmpty { callableTypes }, expression, context)
    }
  }

  private fun getSameScopeCallablesCallTypes(
    types: List<PyCallableType>,
    callSite: PyCallSiteOwner,
    context: TypeEvalContext,
  ): List<PyType?> {
    val firstCallable = types[0].callable
    if (firstCallable != null && PyiUtil.isOverload(firstCallable, context)) {
      return listOf(resolveOverloadsCallType(types, callSite, context))
    }
    return types.map { it.getCallType(context, callSite) }
  }

  @JvmStatic
  fun getCallType(statement: PyAugAssignmentStatement, context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
    val resolveContext = PyResolveContext.defaultContext(context)
    val callableTypes = multipleResolveCallee(statement as PyReferenceOwner, resolveContext)

    val matchingCallableTypes = callableTypes.filter { callableType ->
      val callable = callableType.callable
      callable is PyFunction && matchesByArgumentTypes(callable, statement, context)
    }

    val inplaceOperators = matchingCallableTypes.filter { callableType ->
      val callable = callableType.callable
      callable is PyFunction && statement.isInplaceOperator(callable)
    }
    if (inplaceOperators.isNotEmpty() && areAllTypesCoveredByCandidates(statement.target, inplaceOperators, context)) {
      return getCallType(inplaceOperators, statement, context)
    }

    val normalOperators = matchingCallableTypes.filter { callableType ->
      val callable = callableType.callable
      callable is PyFunction &&
      !statement.isInplaceOperator(callable) &&
      !statement.isRightOperator(callable)
    }
    if (normalOperators.isNotEmpty() && areAllTypesCoveredByCandidates(statement.target, normalOperators, context)) {
      return getCallType(normalOperators, statement, context)
    }

    val leftOperators = (inplaceOperators + normalOperators).distinct()
    if (leftOperators.isNotEmpty() && areAllTypesCoveredByCandidates(statement.target, leftOperators, context)) {
      return getCallType(leftOperators, statement, context)
    }

    return getCallType(matchingCallableTypes.ifEmpty { callableTypes }, statement, context)
  }

  private fun areAllTypesCoveredByCandidates(
    operandExpression: PyExpression,
    candidates: List<PyCallableType>,
    context: TypeEvalContext,
  ): Boolean {
    val operandType = context.getType(operandExpression)
    val operandMemberTypes = operandType.toStream().toList()

    val candidateOwners = candidates
      .mapNotNull { ScopeUtil.getScopeOwner(it.callable) as? PyClass }
      .mapNotNull { it.getType(context)?.toInstance() }
      .toSet()

    return operandMemberTypes.all { member ->
      member is PyClassType && candidateOwners.any { owner ->
        PyTypeChecker.match(owner, member, context)
      }
    }
  }

  private fun resolveOverloadsCallType(types: List<PyCallableType>, callSite: PyCallSiteOwner, context: TypeEvalContext): PyType? {
    val arguments = callSite.getArguments(types[0].callable)
    val matchingOverloads = types.filter { matchesByArgumentTypes(it.callable as PyFunction, callSite, context) }
    if (matchingOverloads.isEmpty()) {
      return types
        .map { it.getCallType(context, callSite) }
        .let { PyUnionType.union(it) }
    }
    if (matchingOverloads.size == 1) {
      return matchingOverloads[0].getCallType(context, callSite)
    }
    val someArgumentsHaveUnknownType = arguments.any {
      context.getType(it).isUnknown
    }
    if (someArgumentsHaveUnknownType) {
      return matchingOverloads
        .map { it.getCallType(context, callSite) }
        .let { PyUnionType.union(it) }
    }
    return matchingOverloads.firstOrNull()?.getCallType(context, callSite) ?: PyAnyType.unknown
  }

  /**
   * [cls] is a class being constructed
   * [method] is either the metaclass `__call__` method or the class `__new__`/`__init__` method
   */
  private fun getConstructorCallType(
    cls: PyClassType,
    methodName: String,
    method: PyCallableType,
    callSite: PyCallExpression,
    context: TypeEvalContext,
  ): PyType? {
    if (methodName != PyNames.INIT && methodName != PyNames.NEW) {
      return method.getCallType(context, callSite)
    }

    var unknownType = false
    if (methodName == PyNames.NEW) {
      val newMethodCallType = method.getCallType(context, callSite)
      if (!newMethodCallType.isUnknown) {
        return newMethodCallType
      }
      unknownType = true
    }

    var callType: PyType? = PyAnyType.unknown
    val callee = callSite.callee
    val genericType = PyTypeChecker.findGenericDefinitionType(cls.pyClass, context)
    if (genericType != null) {
      val arguments = callSite.getArguments().asList()
      val parameters = method.getParameters(context) ?: emptyList()
      val self = parameters.firstOrNull()?.takeIf { it.isSimple }
      val explicitParameters = if (self != null) parameters.subList(1, parameters.size) else parameters

      val mappedArguments = mutableMapOf<PyExpression, PyCallableParameter>()
      if (callee != null && self != null) {
        mappedArguments[callee] = self
      }
      val mappingResults = analyzeArguments(arguments, explicitParameters, context)
      mappedArguments.putAll(mappingResults.mappedParameters)

      val substitutions = PyTypeInferenceCspFactory.unifyGenericCall(callSite, callee, method, mappedArguments, context)
      if (substitutions != null) {
        val finalSubstitutions = getSubstitutionsWithUnresolvedReturnGenerics(parameters, genericType, substitutions, context)
        callType = PyTypeChecker.substitute(genericType, finalSubstitutions, context)
      }
    }

    if (callType.isUnknown) {
      if (callee != null) {
        val calleeType = context.getType(callee)
        if (calleeType is PyInstantiableType<*>) {
          callType = calleeType.toInstance()
        }
      }
    }

    if (unknownType) {
      // Requires weak union. See PyUnresolvedReferencesInspectionTest.testCustomNewReturnInAnotherModule
      callType = PyUnionType.createWeakType(callType)
    }
    return callType
  }

  private fun getSuperCallType(expression: PyCallExpression, context: TypeEvalContext): Maybe<PyType?> {
    val callee = expression.callee
    if (callee !is PyReferenceExpression) return Maybe()
    val mustBeSuper = callee.reference.resolve()
    if (mustBeSuper !== PyBuiltinCache.getInstance(expression).getClass(PyNames.SUPER)) return Maybe()
    val args = expression.argumentList?.arguments ?: return Maybe()
    val containingClass = PsiTreeUtil.getParentOfType(expression, PyClass::class.java)
    if (containingClass != null && args.size > 1) {
      val firstArg = args[0]
      if (firstArg !is PyReferenceExpression) return Maybe()
      val qualifier = firstArg.qualifier
      if (qualifier != null && PyNames.__CLASS__ == firstArg.referencedName) {
        val element = qualifier.reference?.resolve()
        if (element is PyParameter) {
          val parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList::class.java)
          if (parameterList != null && element === parameterList.parameters[0]) {
            return Maybe(getSuperCallTypeForArguments(context, containingClass, args[1]))
          }
        }
      }
      val possibleClass = firstArg.reference.resolve()
      if (possibleClass is PyClass && possibleClass.isNewStyleClass(context)) {
        return Maybe(getSuperCallTypeForArguments(context, possibleClass, args[1]))
      }
      if (possibleClass is PyNamedParameter) {
        val paramType = context.getType(possibleClass)
        if (paramType is PyClassType) {
          return Maybe(getSuperCallTypeForArguments(context, paramType.pyClass, args[1]))
        }
      }
    }
    else if ((expression.containingFile as? PyFile)?.languageLevel?.isPy3K == true && containingClass != null) {
      return Maybe(getSuperClassUnionType(containingClass, context))
    }
    return Maybe()
  }

  private fun getSuperCallTypeForArguments(context: TypeEvalContext, firstClass: PyClass, secondArg: PyExpression?): PyType? {
    // check 2nd argument, too; it should be an instance
    if (secondArg != null) {
      val secondType = context.getType(secondArg)
      if (secondType is PyClassType) {
        // imitate isinstance(secondArg, possibleClass)
        val secondClass = secondType.pyClass
        if (CompletionUtilCoreImpl.getOriginalOrSelf(firstClass) === secondClass) {
          return getSuperClassUnionType(firstClass, context)
        }
        if (secondClass.isSubclass(firstClass, context)) {
          val nextAfterFirstInMro =
            secondClass.getAncestorClasses(context)
              .dropWhile { it !== firstClass }
              .drop(1)
              .firstOrNull()

          if (nextAfterFirstInMro != null) {
            return PyClassTypeImpl(nextAfterFirstInMro, false)
          }
        }
      }
    }
    return null
  }

  private fun getSuperClassUnionType(klass: PyClass, context: TypeEvalContext?): PyType? {
    // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
    // super can also delegate to sibling types
    // TODO handle __mro__ here
    val supers = klass.getSuperClasses(context)
    if (supers.size > 0) {
      if (supers.size == 1) {
        return PyClassTypeImpl(supers[0], false)
      }
      val superTypes = mutableListOf<PyType?>()
      for (aSuper in supers) {
        superTypes.add(PyClassTypeImpl(aSuper, false))
      }
      return PyUnsafeUnionType.unsafeUnion(superTypes)
    }
    return null
  }

  /**
   * `argument` can be (parenthesized) expression or a value of a [PyKeywordArgument]
   */
  @ApiStatus.Internal
  fun getMappedParameters(expression: PyExpression, resolveContext: PyResolveContext): List<PyCallableParameter>? {
    var argument = expression
    while (true) {
      val newArgument = argument.parent
      newArgument as? PyParenthesizedExpression ?: break
      argument = newArgument
    }

    (argument.parent as? PyKeywordArgument)?.let {
      assert(it.valueExpression === argument)
      argument = it
    }

    var parent = argument.parent
    if (parent is PyArgumentList) {
      parent = parent.parent
    }
    if (parent !is PyCallSiteExpression) {
      return null
    }

    val finalArgument = argument
    return mapArguments(parent, resolveContext).mapNotNull {
      it.mappedParameters[finalArgument]
    }
  }

  /**
   * Gets implicit offset from the `callableType`,
   * should be used with the methods below since they specify correct offset value.
   *
   * @see PyCallExpression.multiResolveCalleeFunction
   */
  @JvmStatic
  fun mapArguments(expression: PyCallSiteOwner, callableType: PyCallableType, context: TypeEvalContext): PyArgumentsMapping {
    val arguments = expression.getArguments(callableType.callable)
    val parameters = callableType.getParameters(context)
      ?.let { unpackParametersIfNeeded(it, arguments, context) }

    if (parameters == null) return PyArgumentsMapping.empty(expression)

    val safeImplicitOffset = min(callableType.implicitOffset, parameters.size)
    val explicitParameters = parameters.subList(safeImplicitOffset, parameters.size)
    val implicitParameters = parameters.subList(0, safeImplicitOffset)
    val mappingResults = analyzeArguments(arguments, explicitParameters, context)

    return PyArgumentsMapping(expression,
                              callableType,
                              implicitParameters,
                              mappingResults.mappedParameters,
                              mappingResults.unmappedParameters,
                              mappingResults.unmappedContainerParameters,
                              mappingResults.unmappedArguments,
                              mappingResults.parametersMappedToVariadicPositionalArguments,
                              mappingResults.parametersMappedToVariadicKeywordArguments,
                              mappingResults.mappedTupleParameters)
  }

  @JvmStatic
  fun mapArguments(expression: PyCallSiteOwner, resolveContext: PyResolveContext): List<PyArgumentsMapping> {
    val context = resolveContext.typeEvalContext
    return multiResolveCalleeFunction(expression, resolveContext).map {
      mapArguments(expression, it, context)
    }
  }

  private fun multiResolveCalleeFunction(expression: PyCallSiteOwner, resolveContext: PyResolveContext): List<PyCallableType> {
    when (expression) {
      is PyCallExpression -> {
        return expression.multiResolveCallee(resolveContext)
      }
      is PySubscriptionExpression -> {
        return multipleResolveCallee(expression as PyReferenceOwner, resolveContext)
      }
      else -> {
        val results = mutableListOf<PyCallableType>()

        for (result in PyUtil.multiResolveTopPriority(expression, resolveContext)) {
          if (result is PyTypedElement) {
            val resultType = resolveContext.typeEvalContext.getType(result)
            if (resultType is PyCallableType) {
              results.add(resultType)
              continue
            }
          }
          return emptyList()
        }

        return results
      }
    }
  }

  /**
   * Tries to infer implicit offset from the `callSite` and `callable`.
   *
   * @see mapArguments
   */
  @JvmStatic
  fun mapArguments(expression: PyCallSiteOwner, callable: PyCallable, context: TypeEvalContext): PyArgumentsMapping {
    val callableType = context.getType(callable) as? PyCallableType?
                       ?: return PyArgumentsMapping.empty(expression)

    val arguments = expression.getArguments(callable)
    val parameters = callableType.getParameters(context)
      ?.let { unpackParametersIfNeeded(it, arguments, context) }

    if (parameters == null) return PyArgumentsMapping.empty(expression)

    val resolveContext = PyResolveContext.defaultContext(context)
    val explicitParameters = filterExplicitParameters(parameters, callable, expression, resolveContext)
    val implicitParameters = parameters.subList(0, parameters.size - explicitParameters.size)

    val mappingResults = analyzeArguments(arguments, explicitParameters, context)

    return PyArgumentsMapping(expression,
                              callableType,
                              implicitParameters,
                              mappingResults.mappedParameters,
                              mappingResults.unmappedParameters,
                              mappingResults.unmappedContainerParameters,
                              mappingResults.unmappedArguments,
                              mappingResults.parametersMappedToVariadicPositionalArguments,
                              mappingResults.parametersMappedToVariadicKeywordArguments,
                              mappingResults.mappedTupleParameters)
  }

  @JvmStatic
  fun <T> getArgumentsMappedToPositionalContainer(map: Map<T, PyCallableParameter>): List<T> {
    return map.filterValues { it.isPositionalContainer }.keys.toList()
  }

  @JvmStatic
  fun <T> getArgumentsMappedToKeywordContainer(map: Map<T, PyCallableParameter>): List<T> {
    return map.filterValues { it.isKeywordContainer }.keys.toList()
  }

  @JvmStatic
  fun <T> getRegularMappedParameters(map: Map<T, PyCallableParameter>): Map<T, PyCallableParameter> {
    return map.filterValues { !it.isPositionalContainer && !it.isKeywordContainer }
  }

  @JvmStatic
  fun <T> getMappedPositionalContainer(map: Map<T, PyCallableParameter>): PyCallableParameter? {
    return map.values.find { it.isPositionalContainer }
  }

  @JvmStatic
  fun <T> getMappedKeywordContainer(map: Map<T, PyCallableParameter>): PyCallableParameter? {
    return map.values.find { it.isKeywordContainer }
  }

  /**
   * If [type] is a class definition, synthesizes the class constructor.
   * If [type] is a class instance, resolves its `__call__` method.
   * Returns a single function or a list of overloads.
   */
  private fun createCallableFromClass(
    type: PyClassType,
    call: PyCallExpression,
    resolveContext: PyResolveContext,
  ): List<PyCallableType> {
    val context = resolveContext.typeEvalContext
    val implicitlyInvokedMethods = resolveImplicitlyInvokedMethods(type, call, resolveContext)

    return forEveryScopeTakeOverloadsOtherwiseImplementations(implicitlyInvokedMethods, context)
      .mapNotNull { element ->
        if (element !is PyTypedElement) return@mapNotNull null
        val methodName = element.name ?: return@mapNotNull null
        val method = context.getType(element) as? PyCallableType ?: return@mapNotNull null

        val returnType = if (type.isDefinition)
          getConstructorCallType(type, methodName, method, call, context)
        else
          method.getCallType(context, call)

        val firstParam = method.getParameters(context)?.firstOrNull()
        val implicitOffset = if (firstParam != null && firstParam.isSimple) 1 else 0

        PyCallableTypeImpl(method.getParametersType(context),
                           returnType,
                           method.callable,
                           method.modifier,
                           implicitOffset)
      }
      .toList()
      .ifEmpty { listOf(type) }
  }

  @JvmStatic
  fun resolveImplicitlyInvokedMethods(
    type: PyClassType,
    callSite: PyCallSiteExpression?,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> {
    return if (type.isDefinition()) resolveConstructors(type, callSite, resolveContext)
    else type.resolveMember(PyNames.CALL, callSite, AccessDirection.READ, resolveContext) ?: emptyList()
  }

  fun getImplicitlyInvokedMethod(
    type: PyClassType,
    resolveContext: PyResolveContext,
  ): List<PyTypeMember> {
    return if (type.isDefinition()) getConstructorTypes(type, resolveContext)
    else type.findMember(PyNames.CALL, resolveContext)
  }

  private fun resolveConstructors(
    type: PyClassType,
    callSite: PyCallSiteExpression?,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> {
    // https://typing.python.org/en/latest/spec/constructors.html#metaclass-call-method
    val resolvedMetaClassCall = resolveMetaClassCallMethod(type, callSite, resolveContext)
    val context = resolveContext.typeEvalContext
    val skipNewAndInitEvaluation = resolvedMetaClassCall.any { isReturnTypeIncompatibleWithClass(type, it, callSite, context) }
    if (skipNewAndInitEvaluation) {
      return resolvedMetaClassCall
    }

    if (!type.pyClass.isNewStyleClass(context)) {
      return type.resolveMember(PyNames.INIT, callSite, AccessDirection.READ, resolveContext) ?: emptyList()
    }

    // https://typing.python.org/en/latest/spec/constructors.html#new-method
    val resolvedNew = resolveNewMethod(type, callSite, resolveContext)
    val skipInitEvaluation = resolvedNew.any { isReturnTypeIncompatibleWithClass(type, it, callSite, context) }
    if (skipInitEvaluation) {
      return resolvedNew
    }

    // The most derived `__init__` (preferred) or `__new__` is returned.
    // The signature of the returned method is later used to solve generic parameters to infer constructor call type.
    val mro = sequenceOf(type) + type.getAncestorTypes(context).asSequence().filterNotNull()
    for (current in mro) {
      val init = current.resolveMember(PyNames.INIT, callSite, AccessDirection.READ, resolveContext, false)
      if (!init.isNullOrEmpty()) return init

      val new = current.resolveMember(PyNames.NEW, callSite, AccessDirection.READ, resolveContext, false)
      if (!new.isNullOrEmpty()) return new
    }

    return emptyList()
  }

  private fun getConstructorTypes(type: PyClassType, resolveContext: PyResolveContext): List<PyTypeMember> {
    val initFunc = type.findMember(PyNames.INIT, resolveContext)
    if (initFunc.isNotEmpty()) {
      return initFunc
    }

    val newFunc = type.findMember(PyNames.NEW, resolveContext)
    if (newFunc.isNotEmpty()) {
      return newFunc
    }

    return emptyList()
  }

  private fun isReturnTypeAnnotated(type: PyCallableType, context: TypeEvalContext): Boolean {
    val callable = type.callable
    if (callable is PyFunction) {
      val returnTypeAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(callable, context)
      return returnTypeAnnotation != null
    }
    return false
  }

  private fun resolveMetaClassCallMethod(
    type: PyClassType,
    location: PyCallSiteExpression?,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> {
    val metaClassType = type.getMetaClassType(resolveContext.typeEvalContext, true) ?: return emptyList()
    val typeType = PyBuiltinCache.getInstance(type.pyClass).typeType
    if (metaClassType === typeType) return emptyList()
    return type.resolveMember(PyNames.CALL, location, AccessDirection.READ, resolveContext)
      .orEmpty()
      .filterNot { isClassMember(typeType, it.element) }
  }

  private fun resolveNewMethod(
    type: PyClassType,
    location: PyCallSiteExpression?,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> {
    val resolved = type.resolveMember(PyNames.NEW, location, AccessDirection.READ, resolveContext) ?: return emptyList()
    val objectType = PyBuiltinCache.getInstance(type.pyClass).objectType
    return resolved.filterNot { isClassMember(objectType, it.element) }
  }

  private fun isClassMember(classType: PyClassType?, element: PsiElement?): Boolean {
    return classType != null && element is PyPossibleClassMember && element.containingClass === classType.pyClass
  }

  private fun isReturnTypeIncompatibleWithClass(
    classType: PyClassType,
    resolvedCallable: RatedResolveResult,
    callSite: PyCallSiteExpression?,
    context: TypeEvalContext,
  ): Boolean {
    val callable = resolvedCallable.element
    if (callable !is PyTypedElement) return false

    val callableType = context.getType(callable)
    if (callableType !is PyCallableType) return false

    if (isReturnTypeAnnotated(callableType, context)) {
      val callType = if (callSite != null) callableType.getCallType(context, callSite)
      else callableType.getReturnType(context)
      val instanceType = classType.toInstance()
      return callType.toStream().any {
        it.isAny || it is PyNeverType || !PyTypeChecker.match(instanceType, it, context)
      }
    }
    return false
  }

  private fun isLegacyPositionalOnly(parameter: PyCallableParameter): Boolean = !parameter.isSelf && parameter.protectionLevel == ProtectionLevel.PRIVATE

  @JvmStatic
  fun analyzeArguments(
    arguments: List<PyExpression>,
    parametersType: PyCallableParameterListType,
    context: TypeEvalContext,
  ): ArgumentMappingResults {
    val parameters = unpackParametersIfNeeded(parametersType.parameters, arguments, context)
    return analyzeArguments(arguments, parameters, context)
  }

  @JvmStatic
  fun analyzeArguments(
    arguments: List<PyExpression>,
    parameters: List<PyCallableParameter>,
  context: TypeEvalContext,
): ArgumentMappingResults {
    val hasSlashParameter = parameters.any { it.isPositionOnlySeparator }
    val firstExplicitParam = parameters.dropWhile { it.isSelf }.firstOrNull()
    val oldStylePositionalOnly = firstExplicitParam != null && isLegacyPositionalOnly(firstExplicitParam)
    var positionalOnlyMode = hasSlashParameter || oldStylePositionalOnly
    var keywordOnlyMode = false
    var mappedVariadicArgumentsToParameters = false
    val mappedParameters = LinkedHashMap<PyExpression, PyCallableParameter>()
    val unmappedParameters = mutableListOf<PyCallableParameter?>()
    val unmappedContainerParameters = mutableListOf<PyCallableParameter?>()
    val unmappedArguments = mutableListOf<PyExpression?>()
    val parametersMappedToVariadicKeywordArguments = mutableListOf<PyCallableParameter?>()
    val parametersMappedToVariadicPositionalArguments = mutableListOf<PyCallableParameter?>()
    val tupleMappedParameters = mutableMapOf<PyExpression?, PyCallableParameter?>()

    val positionalResults = filterPositionalAndVariadicArguments(arguments)
    val keywordArguments = arguments.filterIsInstanceTo(mutableListOf<PyKeywordArgument>())
    val variadicPositionalArguments = positionalResults.variadicPositionalArguments
    val positionalComponentsOfVariadicArguments = positionalResults.componentsOfVariadicPositionalArguments.toSet()
    val variadicKeywordArguments = filterVariadicKeywordArguments(arguments)

    val allPositionalArguments = positionalResults.allPositionalArguments
    val reservedForPostArgs = mutableListOf<PyExpression?>()

    for ((index, parameter) in parameters.withIndex()) {
      val psi = parameter.parameter

      if (parameter.isPositionOnlySeparator) {
        positionalOnlyMode = false
      }
      else if (parameter.isKeywordOnlySeparator) {
        keywordOnlyMode = true
      }
      else if (psi is PyNamedParameter || psi == null) {
        val parameterName = parameter.name
        if (!parameter.isSelf && !hasSlashParameter && !isLegacyPositionalOnly(parameter)) {
          positionalOnlyMode = false
        }
        if (parameter.isPositionalContainer) {
          // In a normal Python signature `*args` swallows all remaining positional arguments,
          // since nothing positional can follow it. However, `*tuple[T1, *tuple[U, ...], T2]`
          // expansion produces synthetic positional-only parameters AFTER `*args`
          // (e.g. `[__p0: T1, *args: U, __p1: T2]`).
          // Reserve trailing positional arguments here so they end up bound to
          // those post-`*args` parameters in subsequent iterations.
          val postArgsPositionalCount = parameters.subList(index + 1, parameters.size)
            .count { it.parameter == null && isLegacyPositionalOnly(it) }
          val toReserve = minOf(postArgsPositionalCount, allPositionalArguments.size)
          if (toReserve > 0) {
            val reserveFrom = allPositionalArguments.size - toReserve
            reservedForPostArgs.addAll(allPositionalArguments.subList(reserveFrom, allPositionalArguments.size))
            repeat(toReserve) {
              allPositionalArguments.removeLast()
            }
          }

          for (argument in allPositionalArguments) {
            if (argument != null) {
              mappedParameters[argument] = parameter
            }
          }
          if (variadicPositionalArguments.size == 1) {
            mappedParameters[variadicPositionalArguments[0]] = parameter
          }
          if (variadicPositionalArguments.size != 1 && allPositionalArguments.isEmpty()) {
            unmappedContainerParameters.add(parameter)
          }
          allPositionalArguments.clear()
          variadicPositionalArguments.clear()
          keywordOnlyMode = true
        }
        else if (parameter.isKeywordContainer) {
          for (argument in keywordArguments) {
            mappedParameters[argument] = parameter
          }
          for (variadicKeywordArg in variadicKeywordArguments) {
            mappedParameters[variadicKeywordArg] = parameter
          }
          keywordArguments.clear()
          variadicKeywordArguments.clear()
        }
        else if (keywordOnlyMode && psi == null && isLegacyPositionalOnly(parameter) || positionalOnlyMode) {
          // In a normal Python signature, positional-only parameters cannot follow `*args`
          // (anything after `*args` is keyword-only), so the `keywordOnlyMode` branch only fires
          // for synthetic positional-only parameters produced by `*tuple[T1, *tuple[U, ...], T2]`
          // expansion. For those, consume from arguments reserved at the `*args` branch above;
          // otherwise (no preceding `*args`) consume from the regular positional pool.
          val source = if (keywordOnlyMode) reservedForPostArgs else allPositionalArguments
          val positionalArgument = source.removeFirstOrNull()
          if (positionalArgument != null) {
            mappedParameters[positionalArgument] = parameter
          }
          else if (!variadicPositionalArguments.isEmpty()) {
            parametersMappedToVariadicPositionalArguments.add(parameter)
            mappedVariadicArgumentsToParameters = true
          }
          else if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
        else if (keywordOnlyMode) {
          val keywordArgument = removeKeywordArgument(keywordArguments, parameterName)
          if (keywordArgument != null) {
            mappedParameters[keywordArgument] = parameter
          }
          else if (!variadicKeywordArguments.isEmpty()) {
            parametersMappedToVariadicKeywordArguments.add(parameter)
            mappedVariadicArgumentsToParameters = true
          }
          else if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
        else if (isParamSpecOrConcatenate(parameter, context)) {
          for (argument in arguments) {
            mappedParameters[argument] = parameter
          }
          allPositionalArguments.clear()
          keywordArguments.clear()
          variadicPositionalArguments.clear()
          variadicKeywordArguments.clear()
        }
        else if (!allPositionalArguments.isEmpty()) {
          val positionalArgument = allPositionalArguments.removeFirstOrNull()
          require(positionalArgument != null)
          mappedParameters[positionalArgument] = parameter
          if (positionalComponentsOfVariadicArguments.contains(positionalArgument)) {
            parametersMappedToVariadicPositionalArguments.add(parameter)
          }
        }
        else {
          val keywordArgument = removeKeywordArgument(keywordArguments, parameterName)
          if (keywordArgument != null) {
            mappedParameters[keywordArgument] = parameter
          }
          else if (!variadicPositionalArguments.isEmpty() || !variadicKeywordArguments.isEmpty()) {
            if (!variadicPositionalArguments.isEmpty()) {
              parametersMappedToVariadicPositionalArguments.add(parameter)
            }
            if (!variadicKeywordArguments.isEmpty()) {
              parametersMappedToVariadicKeywordArguments.add(parameter)
            }
            mappedVariadicArgumentsToParameters = true
          }
          else if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
      }
      else if (psi is PyTupleParameter) {
        val positionalArgument = allPositionalArguments.removeFirstOrNull()
        if (positionalArgument != null) {
          tupleMappedParameters[positionalArgument] = parameter
          val tupleMappingResults = mapComponentsOfTupleParameter(positionalArgument, psi)
          mappedParameters.putAll(tupleMappingResults.parameters)
          unmappedParameters.addAll(tupleMappingResults.unmappedParameters)
          unmappedArguments.addAll(tupleMappingResults.unmappedArguments)
        }
        else if (variadicPositionalArguments.isEmpty()) {
          if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
        else {
          mappedVariadicArgumentsToParameters = true
        }
      }
      else if (!parameter.hasDefaultValue()) {
        unmappedParameters.add(parameter)
      }
    }

    if (mappedVariadicArgumentsToParameters) {
      variadicPositionalArguments.clear()
      variadicKeywordArguments.clear()
    }

    unmappedArguments.addAll(allPositionalArguments)
    unmappedArguments.addAll(keywordArguments)
    unmappedArguments.addAll(variadicPositionalArguments)
    unmappedArguments.addAll(variadicKeywordArguments)

    return ArgumentMappingResults(mappedParameters, unmappedParameters, unmappedContainerParameters, unmappedArguments,
                                  parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments,
                                  tupleMappedParameters)
  }

  private fun isParamSpecOrConcatenate(parameter: PyCallableParameter, context: TypeEvalContext): Boolean {
    val type = parameter.getType(context)
    return type is PyParamSpecType || type is PyConcatenateType
  }

  private fun forEveryScopeTakeOverloadsOtherwiseImplementations(results: List<ResolveResult>, context: TypeEvalContext): List<PsiElement> {
    return PyUtil.filterTopPriorityElements(forEveryScopeTakeOverloadsOtherwiseImplementations(results, context) { it.element })
  }

  private fun <E : ResolveResult> forEveryScopeTakeOverloadsOtherwiseImplementations(
    es: List<E>,
    context: TypeEvalContext,
    mapper: (E) -> PsiElement?,
  ): List<E> {
    if (!containsOverloadsAndImplementations(es, context, mapper)) {
      return es
    }
    return es.groupBy {
      ScopeUtil.getScopeOwner(mapper(it))
    }
      .values
      .flatMap {
        takeOverloadsOtherwiseImplementations(it, context, mapper)
      }
  }

  private fun <E : ResolveResult> containsOverloadsAndImplementations(
    es: Collection<E>,
    context: TypeEvalContext,
    mapper: (E) -> PsiElement?,
  ): Boolean {
    var containsOverloads = false
    var containsImplementations = false

    for (element in es) {
      val mapped = mapper(element)
      if (mapped == null) continue

      val overload = PyiUtil.isOverload(mapped, context)
      containsOverloads = containsOverloads or overload
      containsImplementations = containsImplementations or !overload

      if (containsOverloads && containsImplementations) return true
    }

    return false
  }

  private fun <E : ResolveResult> takeOverloadsOtherwiseImplementations(
    es: List<E>,
    context: TypeEvalContext,
    mapper: (E) -> PsiElement?,
  ): Sequence<E> {
    if (!containsOverloadsAndImplementations(es, context, mapper)) {
      return es.asSequence()
    }

    return es.asSequence()
      .filter {
        val mapped = mapper(it)
        mapped != null && (PyiUtil.isInsideStub(mapped) || PyiUtil.isOverload(mapped, context))
      }
  }

  private fun matchesByArgumentTypes(function: PyFunction, callSite: PyCallSiteOwner, context: TypeEvalContext): Boolean {
    val fullMapping = mapArguments(callSite, function, context)
    if (!fullMapping.isComplete) return false

    // TODO properly handle bidirectional operator methods, such as __eq__ and __neq__.
    //  Based only on its name, it's impossible to which operand is the receiver and which one is the argument.
    val receiver = callSite.getReceiver(function)
    val mappedExplicitParameters = fullMapping.mappedParameters

    val allMappedParameters = LinkedHashMap<PyExpression, PyCallableParameter>()
    val firstImplicit = fullMapping.implicitParameters.firstOrNull()
    if (receiver != null && firstImplicit != null) {
      allMappedParameters[receiver] = firstImplicit
    }
    allMappedParameters.putAll(mappedExplicitParameters)

    return PyTypeChecker.unifyGenericCall(receiver, allMappedParameters, context) != null
  }

  private fun mapComponentsOfTupleParameter(argument: PyExpression?, parameter: PyTupleParameter): TupleMappingResults {
    var argument = argument
    val unmappedParameters = mutableListOf<PyCallableParameter?>()
    val unmappedArguments = mutableListOf<PyExpression?>()
    val mappedParameters = mutableMapOf<PyExpression, PyCallableParameter>()
    argument = PyPsiUtils.flattenParens(argument)
    if (argument is PySequenceExpression) {
      val argumentComponents = argument.elements
      val parameterComponents = parameter.contents
      for (i in parameterComponents.indices) {
        val param = parameterComponents[i]
        if (i < argumentComponents.size) {
          val arg = argumentComponents[i]
          if (arg != null) {
            when (param) {
              is PyNamedParameter -> {
                mappedParameters[arg] = PyCallableParameterImpl.psi(param)
              }
              is PyTupleParameter -> {
                val nestedResults = mapComponentsOfTupleParameter(arg, param)
                mappedParameters.putAll(nestedResults.parameters)
                unmappedParameters.addAll(nestedResults.unmappedParameters)
                unmappedArguments.addAll(nestedResults.unmappedArguments)
              }
              else -> {
                unmappedArguments.add(arg)
              }
            }
          }
          else {
            unmappedParameters.add(PyCallableParameterImpl.psi(param))
          }
        }
        else {
          unmappedParameters.add(PyCallableParameterImpl.psi(param))
        }
      }
      if (argumentComponents.size > parameterComponents.size) {
        for (i in parameterComponents.size..<argumentComponents.size) {
          val arg = argumentComponents[i]
          if (arg != null) {
            unmappedArguments.add(arg)
          }
        }
      }
    }
    return TupleMappingResults(mappedParameters, unmappedParameters, unmappedArguments)
  }

  private fun removeKeywordArgument(arguments: MutableList<PyKeywordArgument>, name: String?): PyKeywordArgument? {
    name ?: return null
    var result: PyKeywordArgument? = null
    for (argument in arguments) {
      val keyword = argument.keyword
      if (keyword != null && keyword == name) {
        result = argument
        break
      }
    }
    if (result != null) {
      arguments.remove(result)
    }
    return result
  }

  private fun filterPositionalAndVariadicArguments(expressions: List<PyExpression>): PositionalArgumentsAnalysisResults {
    val variadicArguments = ArrayList<PyExpression>()
    val allPositionalArguments = ArrayList<PyExpression?>()
    val componentsOfVariadicPositionalArguments = ArrayList<PyExpression?>()
    var seenVariadicPositionalArgument = false
    var seenVariadicKeywordArgument = false
    var seenKeywordArgument = false
    for (argument in expressions) {
      if (argument is PyStarArgument) {
        if (argument.isKeyword) {
          seenVariadicKeywordArgument = true
        }
        else {
          seenVariadicPositionalArgument = true
          val expr: PsiElement? = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfType(argument, PyExpression::class.java))
          if (expr is PySequenceExpression) {
            val elements = expr.elements.toList()
            allPositionalArguments.addAll(elements)
            componentsOfVariadicPositionalArguments.addAll(elements)
          }
          else {
            variadicArguments.add(argument)
          }
        }
      }
      else if (argument is PyKeywordArgument) {
        seenKeywordArgument = true
      }
      else {
        if (seenKeywordArgument || seenVariadicKeywordArgument || seenVariadicPositionalArgument) {
          continue
        }
        allPositionalArguments.add(argument)
      }
    }
    return PositionalArgumentsAnalysisResults(allPositionalArguments, componentsOfVariadicPositionalArguments, variadicArguments)
  }

  private fun filterVariadicKeywordArguments(expressions: List<PyExpression?>): MutableList<PyExpression> {
    val results = mutableListOf<PyExpression>()
    for (argument in expressions) {
      if (argument != null && isVariadicKeywordArgument(argument)) {
        results.add(argument)
      }
    }
    return results
  }

  private fun unpackParametersIfNeeded(
    parameters: List<PyCallableParameter>,
    arguments: List<PyExpression>,
    context: TypeEvalContext,
  ): List<PyCallableParameter> {
    // Has **kwargs unpack in arguments -> expand only tuples (keep **kwargs container for unpacked TypedDict as is)
    return if (filterVariadicKeywordArguments(arguments).isEmpty()) {
      ParamHelper.unpackContainerParameters(parameters, context)
    }
    else {
      ParamHelper.unpackPositionalContainerParameters(parameters, context)
    }
  }

  @JvmStatic
  fun isVariadicKeywordArgument(expression: PyExpression): Boolean {
    return expression is PyStarArgument && expression.isKeyword
  }

  @JvmStatic
  fun isVariadicPositionalArgument(expression: PyExpression): Boolean {
    return expression is PyStarArgument && !expression.isKeyword
  }

  private fun filterExplicitParameters(
    parameters: List<PyCallableParameter>,
    callable: PyCallable?,
    callSite: PyCallSiteOwner,
    resolveContext: PyResolveContext,
  ): List<PyCallableParameter> {
    val implicitOffset: Int
    if (callSite is PyCallExpression) {
      val callee = callSite.callee
      if (callee != null && callable is PyFunction) {
        implicitOffset = getImplicitArgumentCount(callee, callable, resolveContext)
      }
      else {
        implicitOffset = 0
      }
    }
    else {
      implicitOffset = 1
    }
    return parameters.subList(min(implicitOffset, parameters.size), parameters.size)
  }

  /**
   * Returns the overload declaration of this function that best matches the given call expression,
   * or null if no overload matches or overloads are unavailable in the current context.
   */
  @ApiStatus.Internal
  fun selectMatchingOverload(
    function: PyFunction,
    callExpression: PyCallExpression,
    context: TypeEvalContext,
  ): PyFunction? {
    val overloads = PyiUtil.getOverloads(function, context)
    if (overloads.isEmpty()) return null

    val arguments = callExpression.getArguments(function)
    val matchingOverloads = overloads.filter { matchesByArgumentTypes(it, callExpression, context) }

    if (matchingOverloads.isEmpty()) {
      return null
    }

    if (matchingOverloads.size == 1) {
      return matchingOverloads[0]
    }

    val someArgumentsHaveUnknownType = arguments.any { argument ->
      context.getType(argument) == null
    }
    if (someArgumentsHaveUnknownType) {
      return null
    }

    return matchingOverloads.firstOrNull()
  }

  @JvmStatic
  fun canQualifyAnImplicitName(expression: PyExpression): Boolean {
    if (expression !is PyCallExpression) return true
    val callee = expression.callee
    if (callee is PyReferenceExpression && PyNames.SUPER == callee.name) {
      val target = callee.reference.resolve()
      if (target != null && PyBuiltinCache.getInstance(expression).isBuiltin(target)) return false // super() of unresolved type
    }
    return true
  }

  private val PyCallableParameter.isSimple: Boolean get() =
    !(isPositionalContainer || isPositionOnlySeparator || isKeywordContainer || isKeywordOnlySeparator)
}

class ArgumentMappingResults internal constructor(
  val mappedParameters: Map<PyExpression, PyCallableParameter>,
  val unmappedParameters: List<PyCallableParameter?>,
  val unmappedContainerParameters: List<PyCallableParameter?>,
  val unmappedArguments: List<PyExpression?>,
  val parametersMappedToVariadicPositionalArguments: List<PyCallableParameter?>,
  val parametersMappedToVariadicKeywordArguments: List<PyCallableParameter?>,
  val mappedTupleParameters: Map<PyExpression?, PyCallableParameter?>,
)

private class TupleMappingResults(
  val parameters: Map<PyExpression, PyCallableParameter>,
  val unmappedParameters: List<PyCallableParameter?>,
  val unmappedArguments: List<PyExpression?>,
)

private class PositionalArgumentsAnalysisResults(
  val allPositionalArguments: MutableList<PyExpression?>,
  val componentsOfVariadicPositionalArguments: List<PyExpression?>,
  val variadicPositionalArguments: MutableList<PyExpression>,
)

private class ClarifiedResolveResult(
  val originalResolveResult: QualifiedRatedResolveResult,
  val clarifiedResolved: PsiElement,
  val wrappedModifier: PyAstFunction.Modifier?,
)