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
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.ast.PyAstFunction
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.isPrivate
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping
import com.jetbrains.python.psi.impl.references.PyReferenceImpl
import com.jetbrains.python.psi.resolve.*
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.toolbox.Maybe
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min
import com.intellij.openapi.util.Pair as JBPair

/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 */


/**
 * Tries to interpret a call as a call to built-in `classmethod` or `staticmethod`.
 *
 * @param this the possible call, generally a result of chasing a chain of assignments
 * @return a pair of wrapper name and wrapped function; for `staticmethod(foo)` it would be ("staticmethod", foo).
 */
fun PyCallExpression.interpretAsModifierWrappingCall(): JBPair<String, PyFunction>? {
  val redefining_callee = callee
  if (!isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD)) return null
  val referenceExpr = redefining_callee as PyReferenceExpression? ?: return null
  val refName = referenceExpr.referencedName
  if (!(PyNames.CLASSMETHOD == refName || PyNames.STATICMETHOD == refName) || !PyBuiltinCache.isInBuiltins(referenceExpr)) return null
  // yes, really a case of "foo = classmethod(foo)"
  val argumentList = argumentList ?: return null
  // really can't be any other way
  val possible_original_ref = argumentList.arguments.firstOrNull() as? PyReferenceExpression ?: return null
  val original = possible_original_ref.reference.resolve() as? PyFunction ?: return null
  // pinned down the original; replace our resolved callee with it and add flags.
  return JBPair.create(refName, original)
}

fun PyCallExpression.resolveCalleeClass(): PyClass? {
  val callee = callee

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
fun PyCallExpression.getCalleeType(resolveContext: PyResolveContext): PyType? {
  val callableTypes = mutableListOf<PyType?>()
  val context = resolveContext.typeEvalContext

  val results = PyUtil.filterTopPriorityResults(
    callee
      .multipleResolveCallee(resolveContext)
      .forEveryScopeTakeOverloadsOtherwiseImplementations(context) { it.element }
  )

  for (resolveResult in results) {
    val element = resolveResult.element
    if (element != null) {
      val typeFromProviders =
        Ref.deref(PyReferenceExpressionImpl.getReferenceTypeFromProviders(element, resolveContext.typeEvalContext, this))

      if (PyTypeUtil.toStream(typeFromProviders).allMatch { it is PyCallableType }) {
        PyTypeUtil.toStream(typeFromProviders).forEachOrdered { callableTypes.add(it) }
        continue
      }
    }

    for (clarifiedResolveResult in resolveResult.clarifyResolveResult(resolveContext)) {
      ContainerUtil.addIfNotNull<PyCallableType?>(callableTypes, toCallableType(clarifiedResolveResult, context))
    }
  }

  return PyUnionType.union(callableTypes)
}

fun multiResolveCallee(x: PyCallExpression, resolveContext: PyResolveContext): List<PyCallableType> =
  x.multipleResolveCallee(resolveContext)

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
fun PyCallExpression.multipleResolveCallee(resolveContext: PyResolveContext): List<PyCallableType> {
  return PyUtil.getParameterizedCachedValue(
    this,
    resolveContext) {
      getExplicitResolveResults(it) +
      getImplicitResolveResults(it) +
      getRemoteResolveResults(it)
  }
}

private fun PyReferenceOwner.multipleResolveCallee(resolveContext: PyResolveContext): List<PyCallableType> {
  val context = resolveContext.typeEvalContext

  val results = getReference(resolveContext)
    .multiResolve(false)
    .toList()
    .forEveryScopeTakeOverloadsOtherwiseImplementations(context)

  return results.selectCallableTypes(context)
}

private fun PyCallExpression.getExplicitResolveResults(resolveContext: PyResolveContext): List<PyCallableType> {
  val callee = this.callee
  if (callee == null) return listOf()

  val context = resolveContext.typeEvalContext
  val calleeType = context.getType(callee)

  val provided = PyTypeProvider.EP_NAME.extensionList.mapNotNull { it.prepareCalleeTypeForCall(calleeType, this, context) }
  if (!provided.isEmpty())
    return provided.mapNotNull { Ref.deref(it) }

  val result = mutableListOf<PyCallableType>()

  for (type in PyTypeUtil.toStream(calleeType)) {
    if (type is PyClassType) {
      val implicitlyInvokedMethods = type
        .resolveImplicitlyInvokedMethods(this, resolveContext)
        .forEveryScopeTakeOverloadsOtherwiseImplementations(context)

      if (implicitlyInvokedMethods.isEmpty()) {
        result.add(type)
      }
      else {
        result.addAll(type.changeToImplicitlyInvokedMethods(implicitlyInvokedMethods, this, context))
      }
    }
    else if (type is PyCallableType) {
      result.add(type)
    }
  }

  return result
}

private fun PyCallExpression.getImplicitResolveResults(resolveContext: PyResolveContext): List<PyCallableType> {
  if (!resolveContext.allowImplicits()) return listOf()

  val callee = this.callee
  val context = resolveContext.typeEvalContext
  if (callee is PyQualifiedExpression) {
    val referencedName = callee.referencedName
    if (referencedName == null) return listOf()

    val qualifier = callee.qualifier
    if (qualifier == null || !qualifier.canQualifyAnImplicitName()) return listOf()

    val qualifierType = context.getType(qualifier)
    if (PyTypeChecker.isUnknown(qualifierType, context) ||
        qualifierType is PyStructuralType && qualifierType.isInferredFromUsages
    ) {
      val resolveResults = ResolveResultList()
      PyResolveUtil.addImplicitResolveResults(referencedName, resolveResults, callee)

      return resolveResults.forEveryScopeTakeOverloadsOtherwiseImplementations(context).selectCallableTypes(context)
    }
  }

  return listOf()
}

private fun PyCallExpression.getRemoteResolveResults(resolveContext: PyResolveContext): List<PyCallableType> {
  if (!resolveContext.allowRemote()) return mutableListOf()
  val file = containingFile
  if (file == null || !PythonRuntimeService.getInstance().isInPydevConsole(file)) return mutableListOf()
  val calleeType = getCalleeType(resolveContext)
  return PyTypeUtil.toStream(calleeType).select(PyCallableType::class.java).toList()
}

private fun List<PsiElement>.selectCallableTypes(context: TypeEvalContext): List<PyCallableType> {
  return this
    .filterIsInstance<PyTypedElement>()
    .map { context.getType(it) }
    .flatMap { PyTypeUtil.toStream(it) }
    .filterIsInstance<PyCallableType>()
}

private fun PyExpression?.multipleResolveCallee(resolveContext: PyResolveContext): List<QualifiedRatedResolveResult> {
  return when (this) {
    is PyReferenceExpression -> multiFollowAssignmentsChain(resolveContext)
    is PyLambdaExpression -> listOf(QualifiedRatedResolveResult(this, listOf<PyExpression?>(), RatedResolveResult.RATE_NORMAL, false))
    else -> listOf()
  }
}

private fun QualifiedRatedResolveResult.clarifyResolveResult(resolveContext: PyResolveContext): List<ClarifiedResolveResult> {
  val resolved = element

  if (resolved is PyCallExpression) { // foo = classmethod(foo)

    val wrapperInfo = resolved.interpretAsModifierWrappingCall()
    if (wrapperInfo != null) {
      val wrapperName = wrapperInfo.first
      val wrappedModifier = if (PyNames.CLASSMETHOD == wrapperName)
        PyAstFunction.Modifier.CLASSMETHOD
      else
        if (PyNames.STATICMETHOD == wrapperName)
          PyAstFunction.Modifier.STATICMETHOD
        else
          null

      val result = ClarifiedResolveResult(this, wrapperInfo.second, wrappedModifier, false)
      return listOf(result)
    }
  }
  else if (resolved is PyFunction) {
    val context = resolveContext.typeEvalContext

    if (resolved.property != null && resolved.isQualifiedByInstance(qualifiers, context)) {
      val type = context.getReturnType(resolved)

      return if (type is PyFunctionType) listOf(
        ClarifiedResolveResult(this, type.callable, null, false))
      else emptyList()
    }
  }

  return if (resolved != null) listOf(
    ClarifiedResolveResult(this, resolved, null, resolved is PyClass))
  else emptyList()
}

private fun PyCallSiteExpression.toCallableType(resolveResult: ClarifiedResolveResult, context: TypeEvalContext): PyCallableType? {
  val clarifiedResolved = resolveResult.clarifiedResolved as? PyTypedElement ?: return null

  val callableType = context.getType(clarifiedResolved) as? PyCallableType
                     ?: return null

  if (clarifiedResolved is PyCallable) {
    val originalModifier = if (clarifiedResolved is PyFunction) clarifiedResolved.modifier else null
    val resolvedModifier = originalModifier ?: resolveResult.wrappedModifier

    val isConstructorCall = resolveResult.isConstructor
    val qualifiers = resolveResult.originalResolveResult.qualifiers

    val isByInstance = isConstructorCall
                       || clarifiedResolved.isQualifiedByInstance(qualifiers, context)

    val lastQualifier = qualifiers.lastOrNull()
    val isByClass = lastQualifier != null && clarifiedResolved.isQualifiedByClass(lastQualifier, context)

    val resolvedImplicitOffset =
      clarifiedResolved.getImplicitArgumentCount(resolvedModifier, isConstructorCall, isByInstance, isByClass)

    val clarifiedConstructorCallType =
      if (PyUtil.isInitOrNewMethod(clarifiedResolved)) resolveResult.clarifyConstructorCallType(this, context) else null

    if (callableType.modifier == resolvedModifier && callableType.implicitOffset == resolvedImplicitOffset && clarifiedConstructorCallType == null) {
      return callableType
    }

    return PyCallableTypeImpl(
      callableType.getParameters(context),
      clarifiedConstructorCallType ?: callableType.getCallType(context, this),
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
 * @param this@getImplicitArgumentCount the call site, where arguments are given.
 * @param function      resolved method which is being called; plain functions are OK but make little sense.
 * @return a non-negative number of parameters that are implicit to this call.
 */
fun PyReferenceExpression.getImplicitArgumentCount(function: PyFunction, resolveContext: PyResolveContext): Int {
  val followed = followAssignmentsChain(resolveContext)
  val qualifiers = followed.qualifiers
  val firstQualifier = qualifiers.firstOrNull()
  val isByInstance = function.isQualifiedByInstance(qualifiers, resolveContext.typeEvalContext)
  val name = this.name
  val isConstructorCall = PyUtil.isInitOrNewMethod(function) &&
                          (!isQualified || PyNames.INIT != name && PyNames.NEW != name)
  val isByClass = firstQualifier != null && function.isQualifiedByClass(firstQualifier, resolveContext.typeEvalContext)
  return function.getImplicitArgumentCount(function.modifier, isConstructorCall, isByInstance, isByClass)
}

/**
 * Finds how many arguments are implicit in a given call.
 *
 * @param this@getImplicitArgumentCount     resolved method which is being called; non-methods immediately return 0.
 * @param isByInstance true if the call is known to be by instance (not by class).
 * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
 * because one parameter ('self') is implicit.
 */
private fun PyCallable.getImplicitArgumentCount(
  modifier: PyAstFunction.Modifier?,
  isConstructorCall: Boolean,
  isByInstance: Boolean,
  isByClass: Boolean,
): Int {
  var implicit_offset = 0
  var firstIsArgsOrKwargs = false
  val parameters = parameterList.parameters
  if (parameters.size > 0) {
    val first = parameters[0]
    val named = first.asNamed
    if (named != null && (named.isPositionalContainer || named.isKeywordContainer)) {
      firstIsArgsOrKwargs = true
    }
  }
  if (!firstIsArgsOrKwargs && (isByInstance || isConstructorCall)) {
    implicit_offset += 1
  }
  val method = asMethod() ?: return implicit_offset

  if (PyUtil.isNewMethod(method)) {
    return if (isConstructorCall) 1 else 0
  }
  if (!isByInstance && !isByClass && PyUtil.isInitMethod(method)) {
    return 1
  }

  // decorators?
  if (modifier == PyAstFunction.Modifier.STATICMETHOD) {
    if (isByInstance && implicit_offset > 0) implicit_offset -= 1 // might have marked it as implicit 'self'
  }
  else if (modifier == PyAstFunction.Modifier.CLASSMETHOD) {
    if (!isByInstance) implicit_offset += 1 // Both Foo.method() and foo.method() have implicit the first arg
  }
  return implicit_offset
}

private fun PyCallable?.isQualifiedByInstance(qualifiers: List<PyExpression?>, context: TypeEvalContext): Boolean {
  val owner = PsiTreeUtil.getStubOrPsiParentOfType(this, PyDocStringOwner::class.java)
  if (owner !is PyClass) {
    return false
  }
  // true = call by instance
  if (qualifiers.isEmpty()) {
    return true // unqualified + method = implicit constructor call
  }
  for (qualifier in qualifiers) {
    if (qualifier == null) continue
    val byInstance = isQualifiedByInstance(qualifier, context)
    if (byInstance != ThreeState.UNSURE) {
      return byInstance.toBoolean()
    }
  }
  return true
}

private fun PyCallable?.isQualifiedByInstance(qualifier: PyExpression, context: TypeEvalContext): ThreeState {
  if (isQualifiedByClass(qualifier, context)) {
    return ThreeState.NO
  }
  val qualifierType = context.getType(qualifier)
  // TODO: handle UnionType
  if (qualifierType is PyModuleType) {
    return ThreeState.UNSURE
  }
  return ThreeState.YES // NOTE. best guess: unknown qualifier is more probably an instance.
}

private fun PyCallable?.isQualifiedByClass(qualifier: PyExpression, context: TypeEvalContext): Boolean {
  val qualifierType = context.getType(qualifier)

  if (qualifierType is PyClassType) {
    return qualifierType.isDefinition() && belongsToSpecifiedClassHierarchy(qualifierType.pyClass, context)
  }
  else if (qualifierType is PyClassLikeType) {
    return qualifierType.isDefinition() // Any definition means callable is classmethod
  }
  else if (qualifierType is PyUnionType) {
    val members = qualifierType.members

    if (members.all { it == null || it.isNoneType || it is PyClassType }) {
      return members
        .filterIsInstance<PyClassType>()
        .filter { belongsToSpecifiedClassHierarchy(it.pyClass, context) }
        .all { it.isDefinition() }
    }
  }

  return false
}

private fun PsiElement?.belongsToSpecifiedClassHierarchy(cls: PyClass, context: TypeEvalContext): Boolean {
  val parent = PsiTreeUtil.getStubOrPsiParentOfType(this, PyClass::class.java)
  return parent != null && (cls.isSubclass(parent, context) || parent.isSubclass(cls, context))
}

/**
 * This method should not be called directly,
 * please obtain its result via [TypeEvalContext.getType] with `call` as an argument.
 */
fun PyCallExpression.getCallType(context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
  val callee = callee
  if (callee is PyReferenceExpression) {
    // hardwired special cases
    if (PyNames.SUPER == callee.text) {
      val superCallType = getSuperCallType(context)
      if (superCallType.isDefined) {
        return superCallType.value()
      }
    }
    if ("type" == callee.text) {
      val args = arguments
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
  if (callee is PySubscriptionExpression) {
    val parametrizedType = Ref.deref(PyTypingTypeProvider.getType(callee, context))
    if (parametrizedType != null) {
      return parametrizedType
    }
  }
  val resolveContext = PyResolveContext.defaultContext(context)
  return multiResolveCallee(resolveContext).getCallType(this, context)
}

/**
 * This method should not be called directly,
 * please obtain its result via [TypeEvalContext.getType] with `subscription` as an argument.
 */
fun PySubscriptionExpression.getCallType(context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
  val resolveContext = PyResolveContext.defaultContext(context)
  return (this as PyReferenceOwner).multipleResolveCallee(resolveContext).getCallType(this, context)
}

private fun List<PyCallableType>.getCallType(callSite: PyCallSiteExpression, context: TypeEvalContext): PyType? {
  return filter { it.isCallable }
    .groupBy {
      val callable = it.callable
      ScopeUtil.getScopeOwner(callable) to (callable != null && PyiUtil.isOverload(callable, context))
    }
    .values.flatMap {
      it.getSameScopeCallablesCallTypes(callSite, context)
    }
    .let(PyUnionType::union)
}

fun PyBinaryExpression.getCallType(context: TypeEvalContext, @Suppress("unused") key: TypeEvalContext.Key): PyType? {
  val resolveContext = PyResolveContext.defaultContext(context)
  val callableTypes = (this as PyReferenceOwner).multipleResolveCallee(resolveContext)
  // TODO split normal and reflected operator methods and process them separately
  //  e.g. if there is matching __add__ of the left operand, don't consider signatures of __radd__ of the right operand, etc.
  val matchingCallableTypes = callableTypes.filter {
    val callable = it.callable
    callable is PyFunction && callable.matchesByArgumentTypes(this, context)
  }
  return matchingCallableTypes.ifEmpty { callableTypes }.getCallType(this, context)
}

private fun List<PyCallableType>.getSameScopeCallablesCallTypes(callSite: PyCallSiteExpression, context: TypeEvalContext): List<PyType?> {
  val firstCallable = this[0].callable
  if (firstCallable != null && PyiUtil.isOverload(firstCallable, context)) {
    return listOf(resolveOverloadsCallType(callSite, context))
  }
  return map { it.getCallType(context, callSite) }
}

private fun List<PyCallableType>.resolveOverloadsCallType(callSite: PyCallSiteExpression, context: TypeEvalContext): PyType? {
  val arguments = callSite.getArguments(this[0].callable)
  val matchingOverloads = filter { (it.callable as PyFunction).matchesByArgumentTypes(callSite, context) }
  if (matchingOverloads.isEmpty()) {
    return this
      .map { it.getCallType(context, callSite) }
      .let { PyUnionType.union(it) }
  }
  if (matchingOverloads.size == 1) {
    return matchingOverloads[0].getCallType(context, callSite)
  }
  val someArgumentsHaveUnknownType = arguments.any {
    context.getType(it) == null
  }
  if (someArgumentsHaveUnknownType) {
    return matchingOverloads
      .map { it.getCallType(context, callSite) }
      .let { PyUnionType.union(it) }
  }
  return matchingOverloads.firstOrNull()?.getCallType(context, callSite)
}

private fun ClarifiedResolveResult.clarifyConstructorCallType(callSite: PyCallSiteExpression, context: TypeEvalContext): PyType? {
  val initOrNewMethod = clarifiedResolved as PyFunction
  val initOrNewClass = initOrNewMethod.containingClass

  val receiverClass = originalResolveResult.element as? PyClass ?: initOrNewClass!!

  val initOrNewCallType = initOrNewMethod.getCallType(context, callSite)
  if (receiverClass !== initOrNewClass) {
    if (initOrNewCallType is PyTupleType) {
      return PyTupleType(receiverClass, initOrNewCallType.elementTypes, initOrNewCallType.isHomogeneous)
    }

    if (initOrNewCallType is PyCollectionType) {
      val elementTypes = initOrNewCallType.elementTypes
      return PyCollectionTypeImpl(receiverClass, false, elementTypes)
    }

    return PyClassTypeImpl(receiverClass, false)
  }

  if (initOrNewCallType is PyCollectionType) {
    return initOrNewCallType
  }
  if (initOrNewCallType == null) {
    // TODO requires weak union. See PyUnresolvedReferencesInspectionTest.testCustomNewReturnInAnotherModule
    return PyUnionType.createWeakType(PyClassTypeImpl(receiverClass, false))
  }

  return null
}

private fun PyCallExpression.getSuperCallType(context: TypeEvalContext): Maybe<PyType?> {
  val callee = this.callee
  if (callee !is PyReferenceExpression) return Maybe()
  val must_be_super = callee.reference.resolve()
  if (must_be_super !== PyBuiltinCache.getInstance(this).getClass(PyNames.SUPER)) return Maybe()
  val args = argumentList?.arguments ?: return Maybe()
  val containingClass = PsiTreeUtil.getParentOfType(this, PyClass::class.java)
  if (containingClass != null && args.size > 1) {
    val first_arg = args[0]
    if (first_arg !is PyReferenceExpression) return Maybe()
    val qualifier = first_arg.qualifier
    if (qualifier != null && PyNames.__CLASS__ == first_arg.referencedName) {
      val element = qualifier.reference?.resolve()
      if (element is PyParameter) {
        val parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList::class.java)
        if (parameterList != null && element === parameterList.parameters[0]) {
          return Maybe(getSuperCallTypeForArguments(context, containingClass, args[1]))
        }
      }
    }
    val possible_class = first_arg.reference.resolve()
    if (possible_class is PyClass && possible_class.isNewStyleClass(context)) {
      return Maybe(getSuperCallTypeForArguments(context, possible_class, args[1]))
    }
  }
  else if ((containingFile as? PyFile)?.languageLevel?.isPy3K == true && containingClass != null) {
    return Maybe(containingClass.getSuperClassUnionType(context))
  }
  return Maybe()
}

private fun getSuperCallTypeForArguments(context: TypeEvalContext, firstClass: PyClass, second_arg: PyExpression?): PyType? {
  // check 2nd argument, too; it should be an instance
  if (second_arg != null) {
    val second_type = context.getType(second_arg)
    if (second_type is PyClassType) {
      // imitate isinstance(second_arg, possible_class)
      val secondClass = second_type.pyClass
      if (CompletionUtilCoreImpl.getOriginalOrSelf(firstClass) === secondClass) {
        return firstClass.getSuperClassUnionType(context)
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

private fun PyClass.getSuperClassUnionType(context: TypeEvalContext?): PyType? {
  // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
  // super can also delegate to sibling types
  // TODO handle __mro__ here
  val supers = getSuperClasses(context)
  if (supers.size > 0) {
    if (supers.size == 1) {
      return PyClassTypeImpl(supers[0], false)
    }
    val superTypes = mutableListOf<PyType?>()
    for (aSuper in supers) {
      superTypes.add(PyClassTypeImpl(aSuper, false))
    }
    return PyUnionType.union(superTypes)
  }
  return null
}

/**
 * `argument` can be (parenthesized) expression or a value of a [PyKeywordArgument]
 */
@ApiStatus.Internal
fun PyExpression.getMappedParameters(resolveContext: PyResolveContext): List<PyCallableParameter>? {
  var argument = this
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
  return parent.mapArguments(resolveContext).mapNotNull {
    it.mappedParameters[finalArgument]
  }
}

/**
 * Gets implicit offset from the `callableType`,
 * should be used with the methods below since they specify correct offset value.
 *
 * @see PyCallExpression.multiResolveCalleeFunction
 * @see PyCallExpression.multipleResolveCallee
 */
fun PyCallSiteExpression.mapArguments(callableType: PyCallableType, context: TypeEvalContext): PyArgumentsMapping {
  return mapArguments(this.getArguments(callableType.callable), callableType, context)
}

private fun PyCallSiteExpression.mapArguments(
  arguments: List<PyExpression>,
  callableType: PyCallableType,
  context: TypeEvalContext,
): PyArgumentsMapping {
  val parameters = callableType.getParameters(context)
                   ?: return PyArgumentsMapping.empty(this)

  val safeImplicitOffset = min(callableType.implicitOffset, parameters.size)
  val explicitParameters = parameters.subList(safeImplicitOffset, parameters.size)
  val implicitParameters = parameters.subList(0, safeImplicitOffset)
  val mappingResults = analyzeArguments(arguments, explicitParameters, context)

  return PyArgumentsMapping(this,
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

fun PyCallSiteExpression.mapArguments(resolveContext: PyResolveContext): List<PyArgumentsMapping> {
  val context = resolveContext.typeEvalContext
  return multiResolveCalleeFunction(resolveContext).map {
    mapArguments(it, context)
  }
}

private fun PyCallSiteExpression.multiResolveCalleeFunction(resolveContext: PyResolveContext): List<PyCallableType> {
  when (this) {
    is PyCallExpression -> {
      return multiResolveCallee(resolveContext)
    }
    is PySubscriptionExpression -> {
      return (this as PyReferenceOwner).multipleResolveCallee(resolveContext)
    }
    else -> {
      val results = mutableListOf<PyCallableType>()

      for (result in PyUtil.multiResolveTopPriority(this, resolveContext)) {
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
fun PyCallSiteExpression.mapArguments(callable: PyCallable, context: TypeEvalContext): PyArgumentsMapping {
  val callableType = context.getType(callable) as PyCallableType?
                     ?: return PyArgumentsMapping.empty(this)

  val parameters = callableType.getParameters(context)
                   ?: return PyArgumentsMapping.empty(this)

  val resolveContext = PyResolveContext.defaultContext(context)
  val arguments = getArguments(callable)
  val explicitParameters = parameters.filterExplicitParameters(callable, this, resolveContext)
  val implicitParameters = parameters.subList(0, parameters.size - explicitParameters.size)

  val mappingResults = analyzeArguments(arguments, explicitParameters, context)

  return PyArgumentsMapping(this,
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

fun <T> Map<T, PyCallableParameter>.getArgumentsMappedToPositionalContainer(): List<T> {
  return filterValues { it.isPositionalContainer() }.keys.toList()
}

fun <T> Map<T, PyCallableParameter>.getArgumentsMappedToKeywordContainer(): List<T> {
  return filterValues { it.isKeywordContainer() }.keys.toList()
}

fun <T> Map<T, PyCallableParameter>.getRegularMappedParameters(): Map<T, PyCallableParameter> {
  val result = LinkedHashMap<T, PyCallableParameter>()
  for (entry in entries) {
    val argument = entry.key
    val parameter: PyCallableParameter = entry.value
    if (!parameter.isPositionalContainer() && !parameter.isKeywordContainer()) {
      result.put(argument, parameter)
    }
  }
  return result
}

fun <T> Map<T, PyCallableParameter>.getMappedPositionalContainer(): PyCallableParameter? {
  return values.find { it.isPositionalContainer() }
}

fun <T> Map<T, PyCallableParameter>.getMappedKeywordContainer(): PyCallableParameter? {
  return values.find { it.isKeywordContainer() }
}

fun PyClassType.resolveImplicitlyInvokedMethods(
  callSite: PyCallSiteExpression?,
  resolveContext: PyResolveContext,
): List<RatedResolveResult> {
  return if (isDefinition()) resolveConstructors(callSite, resolveContext)
  else resolveDunderCall(callSite, resolveContext)
}

fun PyClassType.getImplicitlyInvokedMethodTypes(
  callSite: PyCallSiteExpression?,
  resolveContext: PyResolveContext,
): List<PyTypedResolveResult> {
  return if (isDefinition()) getConstructorTypes(callSite, resolveContext)
  else getDunderCallType(callSite, resolveContext)
}

private fun PyClassType.changeToImplicitlyInvokedMethods(
  implicitlyInvokedMethods: List<PsiElement>,
  call: PyCallExpression,
  context: TypeEvalContext,
): List<PyCallableType> {
  val cls = pyClass
  return implicitlyInvokedMethods
    .map {
      ClarifiedResolveResult(
        QualifiedRatedResolveResult(cls, listOf(), RatedResolveResult.RATE_NORMAL, false),
        it,
        null,
        PyUtil.isInitOrNewMethod(it)
      )
    }
    .mapNotNull { call.toCallableType(it, context) }
}

private fun PyClassType.resolveConstructors(callSite: PyCallSiteExpression?, resolveContext: PyResolveContext): List<RatedResolveResult> {
  // https://typing.python.org/en/latest/spec/constructors.html#metaclass-call-method
  val metaclassDunderCall = resolveMetaclassDunderCall(callSite, resolveContext)
  val context = resolveContext.typeEvalContext
  val skipNewAndInitEvaluation = metaclassDunderCall
    .map { it.element }
    .filterIsInstance<PyTypedElement>()
    .map { context.getType(it) }
    .filterIsInstance<PyCallableType>()
    .any { callableType: PyCallableType? ->
      if (callableType!!.isReturnTypeAnnotated(context)) {
        val callType = if (callSite != null) callableType.getCallType(context, callSite) else callableType.getReturnType(context)
        val expectedType = toInstance()
        PyTypeUtil.toStream(callType).anyMatch {
          it == null || it is PyNeverType || !PyTypeChecker.match(expectedType, it, context)
        }
      }
      else false
    }

  if (skipNewAndInitEvaluation) {
    return metaclassDunderCall
  }

  val initAndNew = pyClass.multiFindInitOrNew(true, context)
  return initAndNew.preferInitOverNew().map { RatedResolveResult(PyReferenceImpl.getRate(it, context), it) }
}

private fun PyClassType.getConstructorTypes(callSite: PyCallSiteExpression?, resolveContext: PyResolveContext): List<PyTypedResolveResult> {
  val initTypes = getMemberTypes(PyNames.INIT, callSite, AccessDirection.READ, resolveContext)
  if (initTypes != null) {
    return initTypes
  }

  val newTypes = getMemberTypes(PyNames.NEW, callSite, AccessDirection.READ, resolveContext)
  if (newTypes != null) {
    return newTypes
  }

  return emptyList()
}

private fun PyCallableType.isReturnTypeAnnotated(context: TypeEvalContext): Boolean {
  val callable = this.callable
  if (callable is PyFunction) {
    val returnTypeAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(callable, context)
    return returnTypeAnnotation != null
  }
  return false
}

private fun List<PyFunction>.preferInitOverNew(): Collection<PyFunction> {
  val functions = groupBy { it.name }
  return functions[PyNames.INIT] ?: functions.values.flatten()
}

private fun PyClassType.resolveMetaclassDunderCall(
  callSite: PyCallSiteExpression?,
  resolveContext: PyResolveContext,
): List<RatedResolveResult> {
  val context = resolveContext.typeEvalContext

  val metaClassType = getMetaClassType(context, true) ?: return emptyList()

  val typeType = PyBuiltinCache.getInstance(pyClass).typeType
  if (metaClassType === typeType) return emptyList()

  val results = metaClassType.resolveDunderCall(callSite, resolveContext)
  if (results.isEmpty()) return emptyList()

  val typeDunderCall =
    typeType
      ?.resolveDunderCall(null, resolveContext)
      ?.asSequence()
      ?.map { it.element }
      ?.toSet()
      ?: emptySet()

  return results.filter { it.element !in typeDunderCall }
}

private fun PyClassLikeType.resolveDunderCall(location: PyExpression?, resolveContext: PyResolveContext): List<RatedResolveResult> {
  return resolveMember(PyNames.CALL, location, AccessDirection.READ, resolveContext) ?: emptyList()
}

private fun PyClassLikeType.getDunderCallType(location: PyExpression?, resolveContext: PyResolveContext): List<PyTypedResolveResult> {
  return getMemberTypes(PyNames.CALL, location, AccessDirection.READ, resolveContext) ?: emptyList()
}

fun analyzeArguments(
  arguments: List<PyExpression>,
  parameters: List<PyCallableParameter>,
  context: TypeEvalContext,
): ArgumentMappingResults {
  val hasSlashParameter = parameters.any { it.parameter is PySlashParameter }
  var positionalOnlyMode = hasSlashParameter
  var seenStarArgs = false
  var seenSingleStar = false
  var mappedVariadicArgumentsToParameters = false
  val mappedParameters = LinkedHashMap<PyExpression?, PyCallableParameter?>()
  val unmappedParameters = mutableListOf<PyCallableParameter?>()
  val unmappedContainerParameters = mutableListOf<PyCallableParameter?>()
  val unmappedArguments = mutableListOf<PyExpression?>()
  val parametersMappedToVariadicKeywordArguments = mutableListOf<PyCallableParameter?>()
  val parametersMappedToVariadicPositionalArguments = mutableListOf<PyCallableParameter?>()
  val tupleMappedParameters = mutableMapOf<PyExpression?, PyCallableParameter?>()

  val positionalResults = arguments.filterPositionalAndVariadicArguments()
  val keywordArguments = arguments.filterIsInstanceTo(mutableListOf<PyKeywordArgument>())
  val variadicPositionalArguments = positionalResults.variadicPositionalArguments
  val positionalComponentsOfVariadicArguments = positionalResults.componentsOfVariadicPositionalArguments.toSet()
  val variadicKeywordArguments = arguments.filterVariadicKeywordArguments()

  val allPositionalArguments = positionalResults.allPositionalArguments

  for (parameter in parameters) {
    val psi = parameter.parameter

    if (psi is PyNamedParameter || psi == null) {
      val parameterName = parameter.name
      if (parameter.isPositionalContainer()) {
        for (argument in allPositionalArguments) {
          if (argument != null) {
            mappedParameters.put(argument, parameter)
          }
        }
        if (variadicPositionalArguments.size == 1) {
          mappedParameters.put(variadicPositionalArguments[0], parameter)
        }
        if (variadicPositionalArguments.size != 1 && allPositionalArguments.isEmpty()) {
          unmappedContainerParameters.add(parameter)
        }
        allPositionalArguments.clear()
        variadicPositionalArguments.clear()
        seenStarArgs = true
      }
      else if (parameter.isKeywordContainer()) {
        for (argument in keywordArguments) {
          mappedParameters.put(argument, parameter)
        }
        for (variadicKeywordArg in variadicKeywordArguments) {
          mappedParameters.put(variadicKeywordArg, parameter)
        }
        keywordArguments.clear()
        variadicKeywordArguments.clear()
      }
      else if (seenSingleStar) {
        val keywordArgument: PyExpression? = keywordArguments.removeKeywordArgument(parameterName)
        if (keywordArgument != null) {
          mappedParameters.put(keywordArgument, parameter)
        }
        else if (variadicKeywordArguments.isEmpty()) {
          if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
        else {
          parametersMappedToVariadicKeywordArguments.add(parameter)
          mappedVariadicArgumentsToParameters = true
        }
      }
      else if (parameter.isParamSpecOrConcatenate(context)) {
        for (argument in arguments) {
          mappedParameters.put(argument, parameter)
        }
        allPositionalArguments.clear()
        keywordArguments.clear()
        variadicPositionalArguments.clear()
        variadicKeywordArguments.clear()
      }
      else {
        if (positionalOnlyMode) {
          val positionalArgument = allPositionalArguments.next()

          if (positionalArgument != null) {
            mappedParameters.put(positionalArgument, parameter)
          }
          else if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
        else if (allPositionalArguments.isEmpty()) {
          val keywordArgument = keywordArguments.removeKeywordArgument(parameterName)
          if (keywordArgument != null && !(!hasSlashParameter && !seenStarArgs && parameterName != null && isPrivate(parameterName))) {
            mappedParameters.put(keywordArgument, parameter)
          }
          else if (variadicPositionalArguments.isEmpty() && variadicKeywordArguments.isEmpty() && !parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
          else {
            if (!variadicPositionalArguments.isEmpty()) {
              parametersMappedToVariadicPositionalArguments.add(parameter)
            }
            if (!variadicKeywordArguments.isEmpty()) {
              parametersMappedToVariadicKeywordArguments.add(parameter)
            }
            mappedVariadicArgumentsToParameters = true
          }
        }
        else {
          val positionalArgument = allPositionalArguments.next()
          if (positionalArgument != null) {
            mappedParameters.put(positionalArgument, parameter)
            if (positionalComponentsOfVariadicArguments.contains(positionalArgument)) {
              parametersMappedToVariadicPositionalArguments.add(parameter)
            }
          }
          else if (!parameter.hasDefaultValue()) {
            unmappedParameters.add(parameter)
          }
        }
      }
    }
    else if (psi is PyTupleParameter) {
      val positionalArgument = allPositionalArguments.next()
      if (positionalArgument != null) {
        tupleMappedParameters.put(positionalArgument, parameter)
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
    else if (psi is PySlashParameter) {
      positionalOnlyMode = false
    }
    else if (psi is PySingleStarParameter) {
      seenSingleStar = true
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

private fun PyCallableParameter.isParamSpecOrConcatenate(context: TypeEvalContext): Boolean {
  val type = getType(context)
  return type is PyParamSpecType || type is PyConcatenateType
}

private fun List<ResolveResult>.forEveryScopeTakeOverloadsOtherwiseImplementations(context: TypeEvalContext): List<PsiElement> {
  return PyUtil.filterTopPriorityElements(forEveryScopeTakeOverloadsOtherwiseImplementations(context) { it.element })
}

private fun <E : ResolveResult> List<E>.forEveryScopeTakeOverloadsOtherwiseImplementations(
  context: TypeEvalContext,
  mapper: (E) -> PsiElement?,
): List<E> {
  if (!containsOverloadsAndImplementations(context, mapper)) {
    return this
  }
  return groupBy {
    ScopeUtil.getScopeOwner(mapper(it))
  }
    .values
    .flatMap {
      it.takeOverloadsOtherwiseImplementations(context, mapper)
    }
}

private fun <E : ResolveResult> Collection<E>.containsOverloadsAndImplementations(
  context: TypeEvalContext,
  mapper: (E) -> PsiElement?,
): Boolean {
  var containsOverloads = false
  var containsImplementations = false

  for (element in this) {
    val mapped = mapper(element)
    if (mapped == null) continue

    val overload = PyiUtil.isOverload(mapped, context)
    containsOverloads = containsOverloads or overload
    containsImplementations = containsImplementations or !overload

    if (containsOverloads && containsImplementations) return true
  }

  return false
}

private fun <E : ResolveResult> List<E>.takeOverloadsOtherwiseImplementations(
  context: TypeEvalContext,
  mapper: (E) -> PsiElement?,
): Sequence<E> {
  if (!containsOverloadsAndImplementations(context, mapper)) {
    return asSequence()
  }

  return asSequence()
    .filter {
      val mapped = mapper(it)
      mapped != null && (PyiUtil.isInsideStub(mapped) || PyiUtil.isOverload(mapped, context))
    }
}

private fun PyFunction.matchesByArgumentTypes(callSite: PyCallSiteExpression, context: TypeEvalContext): Boolean {
  val fullMapping = callSite.mapArguments(this, context)
  if (!fullMapping.isComplete) return false

  // TODO properly handle bidirectional operator methods, such as __eq__ and __neq__.
  //  Based only on its name, it's impossible to which operand is the receiver and which one is the argument.
  val receiver = callSite.getReceiver(this)
  val mappedExplicitParameters = fullMapping.mappedParameters

  val allMappedParameters = LinkedHashMap<PyExpression?, PyCallableParameter?>()
  val firstImplicit = fullMapping.implicitParameters.firstOrNull()
  if (receiver != null && firstImplicit != null) {
    allMappedParameters.put(receiver, firstImplicit)
  }
  allMappedParameters.putAll(mappedExplicitParameters)

  return PyTypeChecker.unifyGenericCall(receiver, allMappedParameters, context) != null
}

private fun mapComponentsOfTupleParameter(argument: PyExpression?, parameter: PyTupleParameter): TupleMappingResults {
  var argument = argument
  val unmappedParameters = mutableListOf<PyCallableParameter?>()
  val unmappedArguments = mutableListOf<PyExpression?>()
  val mappedParameters = mutableMapOf<PyExpression?, PyCallableParameter?>()
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
              mappedParameters.put(arg, PyCallableParameterImpl.psi(param))
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

private fun MutableList<PyKeywordArgument>.removeKeywordArgument(name: String?): PyKeywordArgument? {
  name ?: return null
  var result: PyKeywordArgument? = null
  for (argument in this) {
    val keyword = argument.keyword
    if (keyword != null && keyword == name) {
      result = argument
      break
    }
  }
  if (result != null) {
    remove(result)
  }
  return result
}

private fun List<PyExpression>.filterPositionalAndVariadicArguments(): PositionalArgumentsAnalysisResults {
  val variadicArguments = ArrayList<PyExpression?>()
  val allPositionalArguments = ArrayList<PyExpression?>()
  val componentsOfVariadicPositionalArguments = ArrayList<PyExpression?>()
  var seenVariadicPositionalArgument = false
  var seenVariadicKeywordArgument = false
  var seenKeywordArgument = false
  for (argument in this) {
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
      if (seenKeywordArgument ||
          seenVariadicKeywordArgument || seenVariadicPositionalArgument && LanguageLevel.forElement(argument).isOlderThan(
          LanguageLevel.PYTHON35)
      ) {
        continue
      }
      allPositionalArguments.add(argument)
    }
  }
  return PositionalArgumentsAnalysisResults(allPositionalArguments, componentsOfVariadicPositionalArguments, variadicArguments)
}

private fun List<PyExpression?>.filterVariadicKeywordArguments(): MutableList<PyExpression> {
  val results = mutableListOf<PyExpression>()
  for (argument in this) {
    if (argument != null && argument.isVariadicKeywordArgument()) {
      results.add(argument)
    }
  }
  return results
}

fun PyExpression.isVariadicKeywordArgument(): Boolean {
  return this is PyStarArgument && isKeyword
}

fun PyExpression.isVariadicPositionalArgument(): Boolean {
  return this is PyStarArgument && !isKeyword
}

private fun <T> MutableList<T?>.next(): T? {
  return if (isEmpty()) null else removeAt(0)
}

private fun List<PyCallableParameter>.filterExplicitParameters(
  callable: PyCallable?,
  callSite: PyCallSiteExpression,
  resolveContext: PyResolveContext,
): List<PyCallableParameter> {
  val implicitOffset: Int
  if (callSite is PyCallExpression) {
    val callee = callSite.callee
    if (callee is PyReferenceExpression && callable is PyFunction) {
      implicitOffset = callee.getImplicitArgumentCount(callable, resolveContext)
    }
    else {
      implicitOffset = 0
    }
  }
  else {
    implicitOffset = 1
  }
  return subList(min(implicitOffset, size), size)
}

fun PyExpression.canQualifyAnImplicitName(): Boolean {
  if (this !is PyCallExpression) return true
  val callee = callee
  if (callee is PyReferenceExpression && PyNames.SUPER == callee.name) {
    val target = callee.reference.resolve()
    if (target != null && PyBuiltinCache.getInstance(this).isBuiltin(target)) return false // super() of unresolved type
  }
  return true
}

class ArgumentMappingResults internal constructor(
  val mappedParameters: Map<PyExpression?, PyCallableParameter?>,
  val unmappedParameters: List<PyCallableParameter?>,
  val unmappedContainerParameters: List<PyCallableParameter?>,
  val unmappedArguments: List<PyExpression?>,
  val parametersMappedToVariadicPositionalArguments: List<PyCallableParameter?>,
  val parametersMappedToVariadicKeywordArguments: List<PyCallableParameter?>,
  val mappedTupleParameters: Map<PyExpression?, PyCallableParameter?>,
)

private class TupleMappingResults(
  val parameters: Map<PyExpression?, PyCallableParameter?>,
  val unmappedParameters: List<PyCallableParameter?>,
  val unmappedArguments: List<PyExpression?>,
)

private class PositionalArgumentsAnalysisResults(
  val allPositionalArguments: MutableList<PyExpression?>,
  val componentsOfVariadicPositionalArguments: List<PyExpression?>,
  val variadicPositionalArguments: MutableList<PyExpression?>,
)

private class ClarifiedResolveResult(
  val originalResolveResult: QualifiedRatedResolveResult,
  val clarifiedResolved: PsiElement,
  val wrappedModifier: PyAstFunction.Modifier?,
  val isConstructor: Boolean,
)

