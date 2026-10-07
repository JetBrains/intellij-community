// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyCallExpressionHelper")

package com.jetbrains.python.psi.impl

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.ProtectionLevel
import com.jetbrains.python.PyNames
import com.jetbrains.python.ast.PyAstBinaryExpression
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.inspections.PyInspectionMessages.ProblemMessage
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyQualifiedElement
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import com.jetbrains.python.psi.impl.references.PyOperatorReference.OperatorSide
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.QualifiedResolveResult
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableArgument
import com.jetbrains.python.psi.types.PyCallableParameter
import com.jetbrains.python.psi.types.PyCallableParameterImpl
import com.jetbrains.python.psi.types.PyCallableParameterListType
import com.jetbrains.python.psi.types.PyCallableParameterListTypeImpl
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyCallableTypeImpl
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyCompositeType
import com.jetbrains.python.psi.types.PyConcatenateType
import com.jetbrains.python.psi.types.PyIntersectionType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyNeverType.Companion.convertNoReturnToNever
import com.jetbrains.python.psi.types.PyOverloadType
import com.jetbrains.python.psi.types.PyParamSpecType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeParameterType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyTypeUtil.compositeMap
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnpackedTupleType
import com.jetbrains.python.psi.types.PyUnsafeUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isAny
import com.jetbrains.python.psi.types.isAnyOrUnknown
import com.jetbrains.python.psi.types.isNoneType
import com.jetbrains.python.psi.types.isUnknown
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.toolbox.Maybe
import org.jetbrains.annotations.ApiStatus
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
   * If the callee type is [PyClassType], returns a callable type that is either a class
   * constructor or a `__call__` method. Otherwise, returns the callee type as is.
   */
  @JvmStatic
  fun getCalleeType(callee: PyExpression, resolveContext: PyResolveContext): PyType? {
    return PyUtil.getNullableParameterizedCachedValue(callee, resolveContext) {
      PyUnionType.unionOrUnknown(
        buildList {
          add(getExplicitCalleeType(callee, it))
          addIfNotNull(getImplicitCalleeType(callee, it))
        }
      )
    }
  }

  @Deprecated(message = "Use `getCalleeType(PyExpression, PyResolveContext)`")
  @JvmStatic
  fun multiResolveCallee(callExpression: PyCallExpression, resolveContext: PyResolveContext): List<PyCallableType> {
    val calleeType = callExpression.callee?.let { getCalleeType(it, resolveContext) } ?: PyAnyType.unknown
    return calleeType.flattenToCallables()
  }

  private fun PyType?.flattenToCallables(): List<PyCallableType> {
    return when (this) {
      is PyCompositeType -> members.flatMap { it.flattenToCallables() }
      else -> PyTypeUtil.getCallableItems(this)
    }
  }

  @JvmStatic
  fun multiResolveOperator(
    operatorOwner: PyQualifiedElement,
    resolveContext: PyResolveContext,
  ): List<ResolvedOperator> {
    val context = resolveContext.typeEvalContext

    return PyOperatorReference(operatorOwner, resolveContext)
      .resolveOperator()
      .map {
        val selfType = it.selfType
        val resolveResults = it.resolveResults
        val boundType = if (selfType is PyClassType) {
          PyTypeUtil.getTypeOfBoundMember(selfType, resolveResults, context)
        }
        else {
          // Workaround for `CythonBuiltinType`. Since it doesn't implement `PyInstantiableType`,
          // methods cannot be bound to it. Just drop the first function parameter.
          // See `CythonTypeCheckerInspectionTest.testTypeCheckInCython`
          val type = PyTypeUtil.getTypeOfMember(resolveResults, context)
          PyTypeUtil.mapCallableType(type) { subType ->
            val parameters = subType.getParameters(context) ?: return@mapCallableType subType
            PyCallableTypeImpl(
              ParamHelper.dropSelf(parameters),
              subType.getReturnType(context),
              subType.callable,
              subType.modifier
            )
          }
        }
        ResolvedOperator(selfType, boundType, it.arguments, it.side)
      }
  }

  private fun getExplicitCalleeType(callee: PyExpression, resolveContext: PyResolveContext): PyType? {
    val context = resolveContext.typeEvalContext
    return context.getType(callee).compositeMap { type ->
      val provided = PyTypeProvider.EP_NAME.computeSafeIfAny { it.prepareCalleeTypeForCall(type, callee, context) }
      if (provided != null) {
        provided.get() ?: PyAnyType.unknown
      }
      // When invoking cls(), turn type[Self] into Self.
      // Otherwise, we will delegate to __init__() of its scope class and return a concrete type class
      // as a call result, losing Self.
      // See e.g. PyTypeAliasAndFormsTest.SelfTypeCheckerInspections.`Self in class methods`
      else if (type is PyClassType && type !is PySelfType) {
        createCallableFromClass(type, resolveContext)
      }
      else {
        type
      }
    }
  }

  private fun getImplicitCalleeType(callee: PyExpression, resolveContext: PyResolveContext): PyType? {
    if (!resolveContext.allowImplicits()) return null

    val context = resolveContext.typeEvalContext
    if (callee is PyQualifiedExpression) {
      val referencedName = callee.referencedName
      if (referencedName == null) return null

      val qualifier = callee.qualifier
      if (qualifier == null || !canQualifyAnImplicitName(qualifier)) return null

      val qualifierType = context.getType(qualifier)
      if (PyTypeChecker.isUnknown(qualifierType, context) ||
          qualifierType is PyStructuralType && qualifierType.isInferredFromUsages
      ) {
        val resolveResults = ResolveResultList()
        PyResolveUtil.addImplicitResolveResults(referencedName, resolveResults, callee)

        val callables =
          PyUtil.filterTopPriorityElements(forEveryScopeTakeOverloadsOtherwiseImplementations(resolveResults, context) { it.element })
            .asSequence()
            .filterIsInstance<PyTypedElement>()
            .map { context.getType(it) }
            .flatMap { it.toStream() }
            .filterIsInstance<PyCallableType>()
            .toList()
        return if (callables.isEmpty()) null else PyUnionType.unionOrUnknown(callables)
      }
    }

    return null
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
    if (expression !is PyReferenceExpression) {
      return 0
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
            return PyAnyType.unknown
          }
        }
      }
    }
    val resolveContext = PyResolveContext.defaultContext(context)
    val calleeType = callee?.let { getCalleeType(it, resolveContext) } ?: PyAnyType.unknown
    return call(calleeType, expression, expression.arguments.map { PyCallableArgument(it) }, context).getType()
  }

  /**
   * This method should not be called directly,
   * please obtain its result via [TypeEvalContext.getType] with `subscription` as an argument.
   */
  @JvmStatic
  fun getCallType(expression: PySubscriptionExpression, context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
    return getCallType(resolveOperatorCalls(expression, context))
  }

  private fun getCallType(calls: List<OperatorCall>): PyType? {
    return calls.map { it.getType() }.let(PyUnionType::unionOrUnknown)
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
    val leftExpr = PyAstBinaryExpression.getChainedComparisonAwareLeftExpression(expression) as? PyExpression ?: return PyAnyType.unknown
    val rightExpr = expression.rightExpression ?: return PyAnyType.unknown

    val operators = resolveOperatorCalls(expression, context)
    val matchingOperators = operators.filter { it.matched }

    val leftType = context.getType(leftExpr)
    val rightType = context.getType(rightExpr)

    val isTypeUnionSyntax = expression.isOperator("|") &&
                            (leftType is PyClassType && leftType.isDefinition ||
                             rightType is PyClassType && rightType.isDefinition)

    if (PyTypingTypeProvider.isInsideTypeHint(expression, context) || isTypeUnionSyntax) {
      return getCallType(matchingOperators.ifEmpty { operators })
    }

    val normalOperators = matchingOperators.filter { it.side != OperatorSide.RIGHT }

    return if (normalOperators.isNotEmpty() && areAllTypesCoveredByCandidates(leftExpr, normalOperators, context)) {
      getCallType(normalOperators)
    }
    else {
      getCallType(matchingOperators.ifEmpty { operators })
    }
  }

  @JvmStatic
  fun getCallType(statement: PyAugAssignmentStatement, context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
    val operators = resolveOperatorCalls(statement, context)

    val matchingOperators = operators.filter { it.matched }

    val inplaceOperators = matchingOperators.filter { it.side == OperatorSide.INPLACE }
    if (inplaceOperators.isNotEmpty() && areAllTypesCoveredByCandidates(statement.target, inplaceOperators, context)) {
      return getCallType(inplaceOperators)
    }

    val normalOperators = matchingOperators.filter { it.side == OperatorSide.LEFT }
    if (normalOperators.isNotEmpty() && areAllTypesCoveredByCandidates(statement.target, normalOperators, context)) {
      return getCallType(normalOperators)
    }

    val leftOperators = (inplaceOperators + normalOperators).distinct()
    if (leftOperators.isNotEmpty() && areAllTypesCoveredByCandidates(statement.target, leftOperators, context)) {
      return getCallType(leftOperators)
    }

    return getCallType(matchingOperators.ifEmpty { operators })
  }

  private fun areAllTypesCoveredByCandidates(
    operandExpression: PyExpression,
    candidates: List<OperatorCall>,
    context: TypeEvalContext,
  ): Boolean {
    return context.getType(operandExpression).toStream().allMatch { member ->
      member is PyClassType && candidates.any { it.selfType == member }
    }
  }

  private fun resolveOperatorCalls(operator: PyQualifiedElement, context: TypeEvalContext): List<OperatorCall> {
    return multiResolveOperator(operator, PyResolveContext.defaultContext(context)).map { op ->
      val arguments = op.arguments.map { PyCallableArgument(it) }
      val call = call(op.method, operator, arguments, context)
      OperatorCall(op.selfType, op.side, call.matched, call.getType)
    }
  }

  private class OperatorCall(
    val selfType: PyType,
    val side: OperatorSide,
    val matched: Boolean,
    val getType: () -> PyType?,
  )

  @ApiStatus.Internal
  @JvmStatic
  @JvmOverloads
  fun getSpecialMethodCallType(
    objectType: PyType?,
    methodName: String,
    arguments: List<PyCallableArgument>,
    context: TypeEvalContext,
    onlyMatched: Boolean = false,
    skipObject: Boolean = false,
  ): Ref<PyType?>? {
    if (objectType !is PyClassLikeType) return null
    val classType = if (objectType.isDefinition) {
      objectType.getMetaClassType(context, true) ?: return null
    }
    else {
      // An implicitly invoked special method is looked up on the type of the object. `resolveMember`
      // searches both the object and its type (the metaclass when `PyClassLikeType.isDefinition` is true).
      // To exclude metaclass attributes from the resolve result, we pass the instance and accept that
      // instance attributes may shadow the class ones.
      objectType
    }
    val resolveContext = PyResolveContext.defaultContext(context)
    val resolveResults = classType.resolveMember(methodName, null, AccessDirection.READ, resolveContext)
    if (resolveResults.isNullOrEmpty()) return null
    // TODO re-use resolveMemberSkippingClass
    if (skipObject) {
      val containingClass = PyTypeUtil.getContainingClass(resolveResults)
      if (containingClass?.qualifiedName == PyNames.FQN.OBJECT) {
        return null
      }
    }
    val memberType = PyTypeUtil.getTypeOfBoundMember(classType, resolveResults, context, selfType = objectType)
    val call = call(memberType, null, arguments, context)
    return if (onlyMatched && !call.matched) null else Ref(call.getType())
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getCallType(type: PyType?, arguments: List<PyCallableArgument>, context: TypeEvalContext): PyType? {
    return call(type, null, arguments, context).getType()
  }

  private fun call(
    type: PyType?,
    callSite: PyElement?,
    arguments: List<PyCallableArgument>,
    context: TypeEvalContext,
  ): CallResult {
    return when (type) {
      is PyIntersectionType -> {
        val members = type.members.map { call(it, callSite, arguments, context) }
        val matchingMembers = members.filter { it.matched }
        CallResult(matchingMembers.isNotEmpty()) {
          PyIntersectionType.intersectionOrTop(matchingMembers.ifEmpty { members }.map { it.getType() })
        }
      }
      is PyUnsafeUnionType -> {
        val members = type.members.map { call(it, callSite, arguments, context) }
        CallResult(members.any { it.matched }) {
          PyUnsafeUnionType.unsafeUnion(members.map { it.getType() })
        }
      }
      is PyUnionType -> {
        val members = type.members.map { call(it, callSite, arguments, context) }
        CallResult(members.all { it.matched }) {
          PyUnionType.unionOrUnknown(members.map { it.getType() })
        }
      }
      is PyOverloadType, is PyCallableType -> {
        val overloads = PyTypeUtil.getCallableItems(type)
        // An overloaded function whose overloads have no callable type yet, e.g. while a type engine starts
        if (overloads.isEmpty()) return CallResult(false) { PyAnyType.unknown }
        val matchingOverloads = selectMatchingOverloads(overloads, arguments, context)
        CallResult(matchingOverloads.isNotEmpty()) {
          matchingOverloads.ifEmpty { overloads }
            .map { it.getCallType(context, callSite, arguments).convertNoReturnToNever() }
            .let(PyUnsafeUnionType::unsafeUnion)
        }
      }
      else -> CallResult(false) { PyAnyType.unknown }
    }
  }

  private class CallResult(val matched: Boolean, val getType: () -> PyType?)

  @ApiStatus.Internal
  @JvmStatic
  fun selectMatchingOverloads(
    overloads: List<PyCallableType>,
    arguments: List<PyCallableArgument>,
    context: TypeEvalContext,
  ): List<PyCallableType> {
    val matchingOverloads = overloads.filter { matchesByArgumentTypes(it, arguments, context) }
    if (matchingOverloads.size <= 1) return matchingOverloads
    val someArgumentsHaveUnknownType = arguments.any { it.getType(context).isAnyOrUnknown }
    return if (someArgumentsHaveUnknownType) matchingOverloads else listOf(matchingOverloads.first())
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
    // The arguments do not describe a class in the MRO, so `super` gives no class here.
    return PyAnyType.unknown
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
    return PyAnyType.unknown
  }

  @JvmStatic
  fun mapArguments(call: PyCallExpression, callableType: PyCallableType, context: TypeEvalContext): PyArgumentsMapping {
    return mapArguments(call, call.arguments.asList(), callableType, context)
  }

  @JvmStatic
  fun mapArguments(
    callSite: PyElement,
    arguments: List<PyExpression>,
    callableType: PyCallableType,
    context: TypeEvalContext,
  ): PyArgumentsMapping {
    val wrappedArguments = arguments.map { PyCallableArgument(it) }
    val parameters = callableType.getParameters(context)
        ?.let { unpackParameters(it, wrappedArguments, context) }

    if (parameters == null) return PyArgumentsMapping.empty(callSite, arguments)

    return mapArguments(callSite, arguments, wrappedArguments, callableType, parameters, context)
  }

  /**
   * Maps [arguments] to [parameters] as given, without the unpacking of container parameters.
   * The mapping refers to the same [PyCallableParameter] instances as [parameters].
   */
  @ApiStatus.Internal
  @JvmStatic
  fun mapArguments(
    callSite: PyElement,
    arguments: List<PyExpression>,
    callableType: PyCallableType,
    parameters: List<PyCallableParameter>,
    context: TypeEvalContext,
  ): PyArgumentsMapping {
    return mapArguments(callSite, arguments, arguments.map { PyCallableArgument(it) }, callableType, parameters, context)
  }

  private fun mapArguments(
    callSite: PyElement,
    arguments: List<PyExpression>,
    wrappedArguments: List<PyCallableArgument>,
    callableType: PyCallableType,
    parameters: List<PyCallableParameter>,
    context: TypeEvalContext,
  ): PyArgumentsMapping {
    val mappingResults = analyzeArguments(wrappedArguments, parameters, context)

    return PyArgumentsMapping(callSite,
                              arguments,
                              callableType,
                              mappingResults.mappedParameters.mapKeys { (argument, _) -> argument.expression!! },
                              mappingResults.unmappedParameters,
                              mappingResults.unmappedContainerParameters,
                              mappingResults.unmappedArguments.mapNotNull { it.expression },
                              mappingResults.parametersMappedToVariadicPositionalArguments,
                              mappingResults.parametersMappedToVariadicKeywordArguments,
                              mappingResults.mappedTupleParameters.mapKeys { (argument, _) -> argument.expression!! })
  }

  @JvmStatic
  fun mapArguments(callSite: PyElement, resolveContext: PyResolveContext): List<PyArgumentsMapping> {
    val context = resolveContext.typeEvalContext
    return when (callSite) {
      is PyCallExpression -> callSite.multiResolveCallee(resolveContext)
        .map { mapArguments(callSite, it, context) }
      is PyClass -> callSite.resolveInitSubclassCallee(resolveContext)
        .map { mapArguments(callSite, callSite.arguments, it, context) }
      is PyQualifiedExpression -> multiResolveOperator(callSite, resolveContext)
        .flatMap { operator ->
          PyTypeUtil.getCallableItems(operator.method).map { mapArguments(callSite, operator.arguments, it, context) }
        }
      else -> emptyList()
    }
  }

  /**
   * Resolves the implicit `__init_subclass__` invoked when [this] is defined.
   *
   * Per PEP 487 the keyword arguments of a class definition (other than `metaclass`) are passed to the
   * `__init_subclass__` of the *parent* classes, so the class's own definition (which targets *its* subclasses)
   * is excluded and the lookup walks the ancestors' MRO, stopping at the first defining class.
   * `object.__init_subclass__`, which accepts no arguments, is the implicit fallback.
   *
   * The check is skipped when a custom metaclass is involved, since its `__new__`/`__init__` may legitimately
   * consume the keyword arguments before they reach `__init_subclass__`.
   */
  private fun PyClass.resolveInitSubclassCallee(resolveContext: PyResolveContext): List<PyCallableType> {
    val context = resolveContext.typeEvalContext
    if (this.hasCustomMetaClass(context)) return emptyList()
    // TypedDict class-definition keyword arguments (total, closed, ...) are validated by PyTypedDictInspection.
    if (isTypingTypedDictInheritor(context)) return emptyList()

    for (ancestor in getAncestorTypes(context)) {
      if (ancestor !is PyClassType) continue
      val resolved = ancestor.resolveMember(PyNames.INIT_SUBCLASS, null, AccessDirection.READ, resolveContext, false)
      if (resolved.isNullOrEmpty()) continue
      return PyTypeUtil.getCallableItems(PyTypeUtil.getTypeOfBoundMember(ancestor, resolved, context))
    }
    return emptyList()
  }

  private fun PyClass.hasCustomMetaClass(context: TypeEvalContext): Boolean {
    val classType = context.getType(this) as? PyClassType ?: return false
    val metaClassType = classType.getMetaClassType(context, true) ?: return false
    return metaClassType !== PyBuiltinCache.getInstance(this).typeType
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
   * If [classType] is a class instance, resolves its `__call__` method.
   * If [classType] is a class definition, synthesizes the class constructor.
   */
  @JvmStatic
  @JvmOverloads
  fun createCallableFromClass(
    classType: PyClassType,
    resolveContext: PyResolveContext,
    errors: MutableList<ProblemMessage>? = null,
  ): PyType? {
    val resolveContext = resolveContext.withoutProperties()
    val context = resolveContext.typeEvalContext

    if (!classType.isDefinition()) {
      val dunderCallResolveResults = classType.resolveMember(PyNames.CALL, null, AccessDirection.READ, resolveContext)
      val callType = PyTypeUtil.getTypeOfBoundMember(classType, dunderCallResolveResults, context, errors)
      return if (callType is PyClassType) createCallableFromClass(callType, resolveContext, errors) else callType
    }

    // https://typing.python.org/en/latest/spec/constructors.html
    // TODO:
    //  To handle a constructor call, the call of class `__new__`, `__init__`, and metaclass `__call__`
    //  methods need to be evaluated using the call arguments. However, the calling function
    //  `getExplicitCalleeType` expects all possible callables regardless of the call site.
    //  This needs to be reworked, though the current implementation, which relies on raw return types,
    //  covers most cases.

    val classType = stripDefaultTypeArguments(classType, context)
    val classTypeParams = classType.typeArguments.filterIsInstance<PyTypeParameterType>()

    val metaClassCall = resolveMetaClassCallMethod(classType, resolveContext)
    if (metaClassCall != null) {
      val skipNewAndInitEvaluation = isMethodReturnTypeIncompatibleWithClass(classType, metaClassCall.get(), context)
      if (skipNewAndInitEvaluation) {
        val metaClassCallType = metaClassCall.get()
        val constructorType = PyTypeUtil.mapCallableType(metaClassCallType) {
          val methodType = PyTypeUtil.bindFunction(it, classType, context)?.boundMethodType ?: return@mapCallableType null
          createConstructor(classTypeParams, methodType, context)
        }
        if (constructorType == null) {
          errors?.add(PyTypeUtil.methodBindingErrorMessage(classType, metaClassCallType, context))
          return PyAnyType.unknown
        }
        return constructorType
      }
    }

    val objectType = PyBuiltinCache.getInstance(classType.pyClass).objectType
    val new = if (classType.pyClass.isNewStyleClass(context)) {
      resolveMemberSkippingClass(classType, PyNames.NEW, objectType, resolveContext)
    }
    else null

    val skipInitEvaluation = new != null && isMethodReturnTypeIncompatibleWithClass(classType, new.get(), context)
    if (!skipInitEvaluation) {
      val init = resolveMemberSkippingClass(classType, PyNames.INIT, objectType, resolveContext)
      if (init != null) {
        // Bind `__init__` to class instance and use its specialized `self` parameter type
        // as the return type of the synthesized constructor callable.
        val selfType = classType.toInstance()
        val initType = init.get()
        val constructorType = PyTypeUtil.mapCallableType(initType) {
          val (methodType, firstParamType) = PyTypeUtil.bindFunction(it, selfType, context) ?: return@mapCallableType null
          createConstructor(classTypeParams, methodType, context, firstParamType)
        }
        if (constructorType == null) {
          errors?.add(PyTypeUtil.methodBindingErrorMessage(selfType, initType, context))
          return PyAnyType.unknown
        }
        return constructorType
      }
    }

    if (new != null) {
      val newType = new.get()
      val constructorType = PyTypeUtil.mapCallableType(newType) {
        val methodType = PyTypeUtil.bindFunction(it, classType, context)?.boundMethodType ?: return@mapCallableType null
        var returnType = methodType.getReturnType(context)
        // PY-6671
        if (returnType.isUnknown) {
          returnType = PyUnionType.createWeakType(classType.toInstance())
        }
        createConstructor(classTypeParams, methodType, context, returnType)
      }
      if (constructorType == null) {
        errors?.add(PyTypeUtil.methodBindingErrorMessage(classType, newType, context))
        return PyAnyType.unknown
      }
      return constructorType
    }

    return PyCallableTypeImpl(classTypeParams, PyCallableParameterListTypeImpl(listOf()),
                              classType.toInstance(), null, null)
  }

  // TODO: PY-90204
  internal fun stripDefaultTypeArguments(classType: PyClassType, context: TypeEvalContext): PyClassType {
    val genericDef = PyTypeChecker.findGenericDefinitionType(classType.pyClass, context) ?: return classType
    if (!classType.isParameterized) return genericDef.toClass()

    val allSubstitutions = PyTypeChecker.collectTypeSubstitutions(classType, context)
    val nonDefaultSubstitutions = GenericSubstitutions(
      allSubstitutions.typeVars.filterNot { (tv, ref) -> isDefault(tv, Ref.deref(ref)) },
      allSubstitutions.typeVarTuples.filterNot { (tvt, v) -> isDefault(tvt, v) },
      allSubstitutions.paramSpecs.filterNot { (ps, v) -> isDefault(ps, v) },
      allSubstitutions.selfType,
    )
    val result = PyTypeChecker.substitute(genericDef, nonDefaultSubstitutions, context)
    return if (result is PyClassType) result.toClass() else classType
  }

  private fun isDefault(tp: PyTypeParameterType, actual: Any?): Boolean {
    val default = Ref.deref(tp.defaultType)
    return default != null && default == actual
  }

  private fun createConstructor(
    classTypeParams: List<PyTypeParameterType>,
    callableType: PyCallableType,
    context: TypeEvalContext,
    returnType: PyType? = callableType.getReturnType(context),
  ): PyCallableType = PyCallableTypeImpl(
    classTypeParams + callableType.getTypeParameters(context).orEmpty(),
    callableType.getParametersType(context),
    returnType,
    callableType.callable,
    callableType.modifier,
  )

  private fun isReturnTypeAnnotated(type: PyCallableType, context: TypeEvalContext): Boolean {
    val callable = type.callable
    if (callable is PyFunction) {
      val returnTypeAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(callable, context)
      return returnTypeAnnotation != null
    }
    return false
  }

  private fun resolveMetaClassCallMethod(type: PyClassType, resolveContext: PyResolveContext): Ref<PyType?>? {
    val metaClassType = type.getMetaClassType(resolveContext.typeEvalContext, true)
    if (metaClassType !is PyClassType) return null

    val typeType = PyBuiltinCache.getInstance(type.pyClass).typeType
    if (metaClassType === typeType) return null

    return resolveMemberSkippingClass(metaClassType, PyNames.CALL, typeType, resolveContext)
  }

  private fun resolveMemberSkippingClass(
    type: PyClassType,
    name: String,
    skipMembersOf: PyClassType?,
    resolveContext: PyResolveContext,
  ): Ref<PyType?>? {
    val resolveResults = type.resolveMember(name, null, AccessDirection.READ, resolveContext)
    if (resolveResults.isNullOrEmpty()) return null
    if (skipMembersOf != null) {
      val containingClass = PyTypeUtil.getContainingClass(resolveResults)
      if (containingClass === skipMembersOf.pyClass) {
        return null
      }
    }
    val context = resolveContext.typeEvalContext
    val memberType = PyTypeUtil.getTypeOfMember(resolveResults, context)
    return Ref.create(PyTypeUtil.specializeMemberType(type, type, memberType, context))
  }

  private fun isMethodReturnTypeIncompatibleWithClass(
    classType: PyClassType,
    methodType: PyType?,
    context: TypeEvalContext,
  ): Boolean {
    val instanceType = classType.toInstance()
    return PyTypeUtil.getCallableItems(methodType).any { callableType ->
      isReturnTypeAnnotated(callableType, context) &&
      callableType.getReturnType(context).toStream().any {
        it.isAny || it is PyNeverType || !PyTypeChecker.match(instanceType, it, context)
      }
    }
  }

  private fun isLegacyPositionalOnly(parameter: PyCallableParameter): Boolean =
    !parameter.isSelf && parameter.protectionLevel == ProtectionLevel.PRIVATE

  @JvmStatic
  fun analyzeArguments(
    arguments: List<PyCallableArgument>,
    parametersType: PyCallableParameterListType,
    context: TypeEvalContext,
  ): ArgumentMappingResults {
    val parameters = unpackParameters(parametersType.parameters, arguments, context)
    return analyzeArguments(arguments, parameters, context)
  }

  @JvmStatic
  fun analyzeArguments(
    arguments: List<PyCallableArgument>,
    parameters: List<PyCallableParameter>,
    context: TypeEvalContext,
  ): ArgumentMappingResults {
    val hasSlashParameter = parameters.any { it.isPositionOnlySeparator }
    val firstExplicitParam = parameters.dropWhile { it.isSelf }.firstOrNull()
    val oldStylePositionalOnly = firstExplicitParam != null && isLegacyPositionalOnly(firstExplicitParam)
    var positionalOnlyMode = hasSlashParameter || oldStylePositionalOnly
    var keywordOnlyMode = false
    var mappedVariadicArgumentsToParameters = false
    val mappedParameters = LinkedHashMap<PyCallableArgument, PyCallableParameter>()
    val unmappedParameters = mutableListOf<PyCallableParameter>()
    val unmappedContainerParameters = mutableListOf<PyCallableParameter>()
    val unmappedArguments = mutableListOf<PyCallableArgument>()
    val parametersMappedToVariadicKeywordArguments = mutableListOf<PyCallableParameter>()
    val parametersMappedToVariadicPositionalArguments = mutableListOf<PyCallableParameter>()
    val tupleMappedParameters = mutableMapOf<PyCallableArgument, PyCallableParameter>()

    val positionalResults = filterPositionalAndVariadicArguments(arguments, context)
    val keywordArguments = arguments.filterTo(mutableListOf()) { it.expression is PyKeywordArgument }
    val variadicPositionalArguments = positionalResults.variadicPositionalArguments
    val positionalComponentsOfVariadicArguments = positionalResults.componentsOfVariadicPositionalArguments.toSet()
    val variadicKeywordArguments = filterVariadicKeywordArguments(arguments)

    val allPositionalArguments = positionalResults.allPositionalArguments
    val reservedForPostArgs = mutableListOf<PyCallableArgument?>()

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
          if (source.isNotEmpty()) {
            val positionalArgument = source.removeFirst()
            if (positionalArgument != null) {
              mappedParameters[positionalArgument] = parameter
            }
            else {
              // null = placeholder for a value already supplied by a fixed-length `*tuple` spread; no argument to map here.
              parametersMappedToVariadicPositionalArguments.add(parameter)
            }
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
          val positionalArgument = allPositionalArguments.removeFirst()
          if (positionalArgument == null) {
            // null = placeholder for a value already supplied by a fixed-length `*tuple` spread; no argument to map here.
            parametersMappedToVariadicPositionalArguments.add(parameter)
          }
          else {
            mappedParameters[positionalArgument] = parameter
            if (positionalComponentsOfVariadicArguments.contains(positionalArgument)) {
              parametersMappedToVariadicPositionalArguments.add(parameter)
            }
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
        if (allPositionalArguments.isNotEmpty()) {
          val positionalArgument = allPositionalArguments.removeFirst()
          if (positionalArgument != null) {
            tupleMappedParameters[positionalArgument] = parameter
            val tupleMappingResults = mapComponentsOfTupleParameter(positionalArgument.expression, psi)
            for ((component, componentParameter) in tupleMappingResults.parameters) {
              mappedParameters[PyCallableArgument(component)] = componentParameter
            }
            unmappedParameters.addAll(tupleMappingResults.unmappedParameters)
            tupleMappingResults.unmappedArguments.mapTo(unmappedArguments) { PyCallableArgument(it) }
          }
          else {
            // null = placeholder for a value already supplied by a fixed-length `*tuple` spread; no argument to map here.
            mappedVariadicArgumentsToParameters = true
          }
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

    // Drop the `*tuple` placeholder slots: they stand for already-supplied values, not surplus arguments.
    unmappedArguments.addAll(allPositionalArguments.filterNotNull())
    unmappedArguments.addAll(keywordArguments)
    unmappedArguments.addAll(variadicPositionalArguments)
    unmappedArguments.addAll(variadicKeywordArguments)

    return ArgumentMappingResults(mappedParameters, unmappedParameters, unmappedContainerParameters, unmappedArguments,
                                  parametersMappedToVariadicPositionalArguments, parametersMappedToVariadicKeywordArguments,
                                  tupleMappedParameters)
  }

  private fun isParamSpecOrConcatenate(parameter: PyCallableParameter, context: TypeEvalContext): Boolean {
    // A ParamSpec or a Concatenate is always spelled out in an annotation or in a type comment, so there is no need to evaluate
    // the type of a parameter that has neither. Asking for it would make the mapping of the arguments of a call depend on the type
    // guessed for an unannotated parameter, which is in turn guessed from the arguments of that very call (PY-91387).
    val psiParameter = parameter.parameter
    if (psiParameter is PyNamedParameter && psiParameter.annotationValue == null && psiParameter.typeCommentAnnotation == null) {
      return false
    }
    val type = parameter.getType(context)
    return type is PyParamSpecType || type is PyConcatenateType
  }

  /**
   * Replaces the `*args: P.args, **kwargs: P.kwargs` parameters of [callableType] with the parameters that [call] gives to `P`.
   *
   * The other arguments of [call] solve `P`, for example the argument for `cls: Callable[P, T]`.
   * Returns [callableType] if it has no such parameters, or if these arguments do not solve `P`.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun expandParamSpecArgsKwargs(call: PyCallExpression, callableType: PyCallableType, context: TypeEvalContext): PyCallableType {
    val parameters = callableType.getParameters(context) ?: return callableType
    val argsIndex = parameters.indexOfFirst { it.isPositionalContainer }
    if (argsIndex < 0 || argsIndex + 1 >= parameters.size) return callableType
    val args = parameters[argsIndex]
    val kwargs = parameters[argsIndex + 1]
    if (!kwargs.isKeywordContainer || !isParamSpecOrConcatenate(args, context)) return callableType
    val paramSpec = args.getType(context) as? PyParamSpecType ?: return callableType
    if (kwargs.getType(context) != paramSpec) return callableType

    val mapping = mapArguments(call, callableType, context)
    val regularArguments = getRegularMappedParameters(mapping.mappedParameters).mapKeys { (argument, _) -> PyCallableArgument(argument) }
    val substitutions = PyTypeChecker.unifyGenericCall(regularArguments, context) ?: return callableType
    val paramSpecParameters = substitutions.paramSpecs[paramSpec] as? PyCallableParameterListType ?: return callableType

    val forwardedParameters = paramSpecParameters.parameters
    var prefix: List<PyCallableParameter> = parameters.subList(0, argsIndex)
    // The parameters before a `/` of `P` are positional-only, so the prefix needs no `/` of its own.
    if (forwardedParameters.any { it.isPositionOnlySeparator }) {
      prefix = prefix.filterNot { it.isPositionOnlySeparator }
    }
    val expandedParameters = prefix + forwardedParameters + parameters.subList(argsIndex + 2, parameters.size)
    return PyCallableTypeImpl(callableType.getTypeParameters(context),
                              PyCallableParameterListTypeImpl(expandedParameters),
                              callableType.getReturnType(context),
                              callableType.callable,
                              callableType.modifier)
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

  private fun matchesByArgumentTypes(function: PyCallableType, arguments: List<PyCallableArgument>, context: TypeEvalContext): Boolean {
    PyCodeInsightCounters.inc(Counter.OVERLOAD_CANDIDATES_CHECKED)
    val parameters = function.getParameters(context)?.let { unpackParameters(it, arguments, context) } ?: return true
    val mapping = analyzeArguments(arguments, parameters, context)
    return mapping.unmappedParameters.isEmpty() &&
           mapping.unmappedArguments.isEmpty() &&
           PyTypeChecker.unifyGenericCall(mapping.mappedParameters, context) != null
  }

  private fun mapComponentsOfTupleParameter(argument: PyExpression?, parameter: PyTupleParameter): TupleMappingResults {
    val unmappedParameters = mutableListOf<PyCallableParameter>()
    val unmappedArguments = mutableListOf<PyExpression>()
    val mappedParameters = mutableMapOf<PyExpression, PyCallableParameter>()
    val argument = PyPsiUtils.flattenParens(argument)
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

  private fun removeKeywordArgument(arguments: MutableList<PyCallableArgument>, name: String?): PyCallableArgument? {
    name ?: return null
    val result = arguments.find { (it.expression as? PyKeywordArgument)?.keyword == name }
    if (result != null) {
      arguments.remove(result)
    }
    return result
  }

  private fun filterPositionalAndVariadicArguments(
    arguments: List<PyCallableArgument>,
    context: TypeEvalContext,
  ): PositionalArgumentsAnalysisResults {
    val variadicArguments = ArrayList<PyCallableArgument>()
    val allPositionalArguments = ArrayList<PyCallableArgument?>()
    val componentsOfVariadicPositionalArguments = ArrayList<PyCallableArgument>()
    var seenVariadicPositionalArgument = false
    var seenVariadicKeywordArgument = false
    var seenKeywordArgument = false
    for ((index, argument) in arguments.withIndex()) {
      val argumentExpression = argument.expression
      if (argumentExpression is PyStarArgument) {
        if (argumentExpression.isKeyword) {
          seenVariadicKeywordArgument = true
        }
        else {
          val expr: PsiElement? = PyPsiUtils.flattenParens(PsiTreeUtil.getChildOfType(argumentExpression, PyExpression::class.java))
          if (expr is PySequenceExpression) {
            val elements = expr.elements.map { PyCallableArgument(it) }
            allPositionalArguments.addAll(elements)
            componentsOfVariadicPositionalArguments.addAll(elements)
          }
          else {
            // A fixed-length `*x` spread followed by positional arguments occupies known leading positions: reserve
            // them with `null` placeholders so the following arguments still map to the correct parameters (PY-40735),
            // e.g. `"2"` binding to `b` in `baz(*xe, "2")` where `xe: tuple[str]`.
            val knownLength = (expr as? PyExpression)?.let { knownFixedTupleSpreadLength(it, context) } ?: -1
            val followedByPositional = (index + 1..arguments.lastIndex).any {
              val next = arguments[it].expression
              next !is PyStarArgument && next !is PyKeywordArgument
            }
            if (knownLength >= 0 && followedByPositional) {
              repeat(knownLength) { allPositionalArguments.add(null) }
            }
            else {
              seenVariadicPositionalArgument = true
              variadicArguments.add(argument)
            }
          }
        }
      }
      else if (argumentExpression is PyKeywordArgument) {
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

  /** Number of values a `*operand` spread contributes for a fixed-length tuple, or `-1` when the length is indeterminate. */
  private fun knownFixedTupleSpreadLength(operand: PyExpression, context: TypeEvalContext): Int {
    val type = context.getType(operand)
    if (type !is PyTupleType || type.isHomogeneous) return -1
    if (type.elementTypes.any { it is PyUnpackedTupleType }) return -1
    return type.elementCount
  }

  private fun filterVariadicKeywordArguments(arguments: List<PyCallableArgument>): MutableList<PyCallableArgument> {
    val results = mutableListOf<PyCallableArgument>()
    for (argument in arguments) {
      val expression = argument.expression
      if (expression != null && isVariadicKeywordArgument(expression)) {
        results.add(argument)
      }
    }
    return results
  }

  @JvmStatic
  fun unpackParameters(
    parameters: List<PyCallableParameter>,
    arguments: List<PyCallableArgument>,
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

  /**
   * Returns the overload declaration of this function that best matches the given call expression,
   * or null if no overload matches or overloads are unavailable in the current context.
   */
  @Deprecated(message = "Use `selectMatchingOverloads(List<PyCallableType>, List<PyCallableArgument>, TypeEvalContext)`")
  @ApiStatus.Internal
  @JvmStatic
  fun selectMatchingOverload(
    function: PyFunction,
    callExpression: PyCallExpression,
    context: TypeEvalContext,
  ): PyFunction? {
    val overloads = PyiUtil.getOverloads(function, context).toSet()
    if (overloads.isEmpty()) return null

    val overloadTypes = callExpression.multiResolveCallee(PyResolveContext.defaultContext(context))
      .filter { it.callable in overloads }
    return selectMatchingOverloads(overloadTypes, callExpression.getArguments().map { PyCallableArgument(it) }, context)
      .singleOrNull()?.callable as? PyFunction
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

}

class ArgumentMappingResults internal constructor(
  val mappedParameters: Map<PyCallableArgument, PyCallableParameter>,
  val unmappedParameters: List<PyCallableParameter>,
  val unmappedContainerParameters: List<PyCallableParameter>,
  val unmappedArguments: List<PyCallableArgument>,
  val parametersMappedToVariadicPositionalArguments: List<PyCallableParameter>,
  val parametersMappedToVariadicKeywordArguments: List<PyCallableParameter>,
  val mappedTupleParameters: Map<PyCallableArgument, PyCallableParameter>,
)

private class TupleMappingResults(
  val parameters: Map<PyExpression, PyCallableParameter>,
  val unmappedParameters: List<PyCallableParameter>,
  val unmappedArguments: List<PyExpression>,
)

private class PositionalArgumentsAnalysisResults(
  val allPositionalArguments: MutableList<PyCallableArgument?>,
  val componentsOfVariadicPositionalArguments: List<PyCallableArgument>,
  val variadicPositionalArguments: MutableList<PyCallableArgument>,
)

@ApiStatus.Internal
data class ResolvedOperator(
  val selfType: PyType,
  val method: PyType?,
  val arguments: List<PyExpression>,
  val side: OperatorSide,
)
