package com.jetbrains.python.psi.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecoratorList
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement.getExpectedType
import com.jetbrains.python.psi.types.PyLiteralType.Companion.promoteToLiteral
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.collectGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory.enterCsp
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
object PyTypeInferenceCspFactory {
  private val recursionGuard = RecursionManager.createGuard<PyExpression>("PyTypeInferenceCsp")

  @JvmStatic
  fun unifySequenceExpression(expression: PySequenceExpression, cls: PyClass, context: TypeEvalContext): GenericSubstitutions {
    val solution = enterCsp(SubstitutionsIdentifier(expression), context )
    if (solution != null) {
      return solution
    }
    else {
      val genericType = PyTypeChecker.findGenericDefinitionType(cls, context)
      val typeVar = genericType?.typeArguments?.firstOrNull() as PyTypeParameterType
      val typeArgument = PyCollectionTypeUtil.getListOrSetIteratedValueType(expression, context)
      return GenericSubstitutions(mapOf(typeVar to typeArgument))
    }
  }

  @JvmStatic
  fun unifyReceiver(argsMapping: PyCallExpression.PyArgumentsMapping, context: TypeEvalContext): GenericSubstitutions {
    val callSite = argsMapping.callSiteOwner
    val callSiteExpression = callSite as? PyExpression
    val callableType = argsMapping.callableType
    val receiver = callSite.getReceiver(callableType?.callable)
    val si = if (callSiteExpression == null) null else SubstitutionsIdentifier(callSiteExpression, callableType)
    val solution = enterCsp(si, context)
    return solution ?: PyTypeChecker.unifyReceiver(receiver, context)
  }

  @JvmStatic
  fun unifyGenericCall(
    callSite: PyCallSiteOwner?,
    receiver: PyExpression?,
    callableType: PyCallableType?,
    mappedParameters: Map<PyExpression, PyCallableParameter>,
    context: TypeEvalContext,
  ): GenericSubstitutions? {
    val callSiteExpression = callSite as? PyExpression
    val si = if (callSiteExpression == null) null else SubstitutionsIdentifier(callSiteExpression, callableType)
    val solution = enterCsp(si, context)
    return solution ?: PyTypeChecker.unifyGenericCall(receiver, mappedParameters, context)
  }

  /**
   * It is beneficial to solve nested CSPs in a single CSP at once instead of solving them independently.
   * The reason is that having a combined CSP that includes all nested CSPs allows for a better solution that fits all constraints.
   *
   * Nested CSPs can occur already in simple examples like shown below:
   * ```Python
   * class A: ...
   * class B(A): ...
   * def f[T](t: T) -> T: ...
   * x: A = f(B()) # no nesting, but CSP necessary for type inference
   * y: list[A] = [B()] # no nesting, but CSP necessary for type inference
   * z1: list[A] = f([B()]) # nesting of two CSPs
   * z2: list[A] = [f(B())] # nesting of two CSPs
   * ```
   *
   * To deal with nested CSPs, we first identify the top-most CSP. When creating its CSP,
   * also constraints from all nested CSPs will be included. Recursive calls to [enterCsp]
   * during CSP creation and solving will return only an empty [GenericSubstitutions].
   *
   * @return null if a fallback approach is necessary
   */
  private fun enterCsp(si: SubstitutionsIdentifier?, context: TypeEvalContext): GenericSubstitutions? {
    if (si == null) {
      return null // use old approach
    }
    if (!Registry.`is`("python.use.csp.type.inference")) {
      return null // use old approach
    }
    if (context !is TypeEvalContextImpl) {
      return null // use old approach
    }

    val topCsp = getTopCsp(si, context)
    if (topCsp == null) {
      return null // use old approach
    }

    val cachedSubstitutions = context.getKnownSubstitutions(si)
    if (cachedSubstitutions != null) {
      return cachedSubstitutions // return cached solution
    }

    val temporarySubstitutions = GenericSubstitutions()
    val cachedSubstitutionsTopCsp = context.getKnownSubstitutions(topCsp)
    if (cachedSubstitutionsTopCsp != null) {
      return temporarySubstitutions // return empty/temporary solution
    }

    val onRecursionStack = recursionGuard.currentStack().contains(topCsp.expression)
    if (onRecursionStack) {
      return temporarySubstitutions // early exit in case of recursion
    }

    val nonTempContext = if (context is TypeEvalContextImpl.TemporaryContext) context.myParent else context

    val builder =
      recursionGuard.computePreventingRecursion<CspBuilder?, Throwable>(topCsp.expression, false) {
        buildAndSolveCsp(topCsp, nonTempContext)
      } // returns also null in case of recursion

    val solution = builder?.getSolution()
    if (solution == null) {
      return null
    }
    if (solution.failed) {
      return null // TODO: return instead a partly solution
    }

    for ((nestedSi, substitutions) in builder.getAllSubstitutions()) {
      val nestedInstantiations = solution.instantiations[nestedSi] ?: emptyMap()
      val solvedSubstitutions = substitutions.addToCopy(nestedInstantiations)
      val simplifiedSubstitutions = solvedSubstitutions.simplify(context)
      nonTempContext.putSubstitutions(nestedSi, simplifiedSubstitutions)
    }

    return nonTempContext.getKnownSubstitutions(si)
  }

  // TODO: wrong parameter mapping passed by testExplicitlyParameterizedGenericConstructorCall: self missing?


  private fun buildAndSolveCsp(si: SubstitutionsIdentifier, nonTempContext: TypeEvalContextImpl) : CspBuilder? {
    try {
      // Use TemporaryContext to avoid pollution of original context with intermediate results
      val tmpContext = TypeEvalContextImpl.TemporaryContext(nonTempContext, true)
      val builder = CspBuilder(tmpContext)
      val returnType = when (si.expression) {
        is PyCallExpression -> buildCallSiteExpressionCsp(si, builder, tmpContext)
        is PySequenceExpression -> buildSequenceExpressionCsp(SubstitutionsIdentifier(si.expression), builder, tmpContext)
        else -> throw NotSupportedException()
      }

      val expectedType = getExpectedType(si.expression, tmpContext)
      if (expectedType != null && !expectedType.isAnyOrUnknown && !returnType.isAnyOrUnknown) {
        builder.addConstraint(returnType, expectedType, Variance.COVARIANT, ConstraintPriority.LOW)
      }

      builder.solve()

      return builder
    }
    catch (_: NotSupportedException) {
      return null
    }
  }

  private fun buildNestedCsp(expression: PyExpression, builder: CspBuilder, context: TypeEvalContext) : PyType? {
    val actualCspAnchor = findChildCsp(expression)
    if (!isCsp(actualCspAnchor)) {
      if (actualCspAnchor is PyTypedElement) {
        val anchorType = context.getType(actualCspAnchor)
        return normalizeType(anchorType, context)
      }
      else {
        return null
      }
    }

    return when (actualCspAnchor) {
      is PyCallExpression -> buildCallSiteExpressionCsp(actualCspAnchor, builder, context)
      is PySequenceExpression -> buildSequenceExpressionCsp(SubstitutionsIdentifier(actualCspAnchor), builder, context)
      else -> throw NotSupportedException()
    }
  }

  private fun buildSequenceExpressionCsp(si: SubstitutionsIdentifier, builder: CspBuilder, context: TypeEvalContext) : PyType? {
    val sequenceTypeName = when (si.expression) {
      is PyListLiteralExpression -> "list"
      is PySetLiteralExpression -> "set"
      else -> throw NotSupportedException()
    }
    val sequenceClass = PyBuiltinCache.getInstance(si.expression).getClass(sequenceTypeName) ?: throw NotSupportedException()
    val sequenceTypeGenericUnnormalized = sequenceClass.getType(context)?.toInstance() ?: throw NotSupportedException()
    val sequenceTypeGeneric = normalizeType(sequenceTypeGenericUnnormalized, context) as? PyClassType ?: throw NotSupportedException()
    val sequenceTypeVar = sequenceTypeGeneric.typeArguments.getOrNull(0) as? PyTypeVarType ?: throw NotSupportedException()
    val iv = builder.addInferenceVariable(sequenceTypeVar, si) // sequences have only a single type variable

    val cspSubstitutions = PyTypeChecker.unifyReceiver(sequenceTypeGeneric, context)
    builder.putSubstitutions(si, cspSubstitutions)
    val ivSubstitutions = GenericSubstitutions(mapOf(sequenceTypeVar to iv))
    val sequenceTypeIV = PyTypeChecker.substitutePlainly(sequenceTypeGeneric, ivSubstitutions, context)

    // not calling ensureBoundsAndConstraints since sequences have no bounds/constraints

    val sequenceElements = unpackStarredListLiterals(si.expression.elements).take(10)
    val elementTypes = sequenceElements.map { element ->
      // A star element that survived unpacking (its operand isn't a list/set literal) contributes its
      // iterated item type, not the type of the iterable itself (mirrors getListOrSetIteratedValueType).
      val starOperand = (element as? PyStarExpression)?.expression
      if (starOperand != null) {
        (context.getType(starOperand) as? PyClassType)?.let { PyTypeChecker.getIteratedItemType(it, context) }
      }
      else {
        buildNestedCsp(element, builder, context)
      }
    }
    val sequenceTypeArg = PyUnionType.union(elementTypes)

    // Literal element types as union
    builder.addConstraint(iv, sequenceTypeArg, Variance.CONTRAVARIANT, ConstraintPriority.MEDIUM)

    return sequenceTypeIV
  }

  private fun buildCallSiteExpressionCsp(callSite: PyCallSiteExpression, builder: CspBuilder, context: TypeEvalContext) : PyType? {
    if (callSite !is PyCallExpression) throw NotSupportedException()
    val resolveContext = PyResolveContext.defaultContext(context)
    val callableTypes = callSite.multiResolveCallee(resolveContext)
    val returnTypes = mutableListOf<PyType?>()
    for (callableType in callableTypes) {
      val returnType = buildCallSiteExpressionCsp(SubstitutionsIdentifier(callSite, callableType), builder, context)
      returnTypes.add(returnType)
    }
    return PyUnionType.union(returnTypes)
  }

  private fun buildCallSiteExpressionCsp(si: SubstitutionsIdentifier, builder: CspBuilder, context: TypeEvalContext) : PyType? {
    val (callSite, callableType) = si
    if (callSite !is PyCallExpression) throw NotSupportedException()
    if (callableType == null) throw NotSupportedException()
    val mapping = PyCallExpressionHelper.mapArguments(callSite, callableType, context)
    if (!mapping.isComplete) throw NotSupportedException()
    val mappedParameters = mapping.mappedParameters
    val receiverType = getReceiverType(callSite, callableType, context)
    val cspSubstitutions = PyTypeChecker.unifyReceiver(receiverType, context)
    if (cspSubstitutions.paramSpecs.isNotEmpty() || cspSubstitutions.typeVarTuples.isNotEmpty()) throw NotSupportedException()
    val declaredReturn = callableType.getReturnType(context)

    builder.putSubstitutions(si, cspSubstitutions)
    // find type variables
    val ivSubstitutionMap = HashMap<PyTypeVarType, Ref<PyType?>>()
    val generics = callableType.collectGenerics(context)
    if (generics.paramSpecs.isNotEmpty() || generics.typeVarTuples.isNotEmpty()) throw NotSupportedException()
    for (typeVarEntry in generics.typeVars) {
      val iv = builder.addInferenceVariable(typeVarEntry, si)
      ivSubstitutionMap[typeVarEntry] = Ref.create(iv)
      ensureBoundsAndConstraints(builder, typeVarEntry, iv, cspSubstitutions, context)
    }
    val ivSubstitutions = cspSubstitutions.addToCopy(typeVars = ivSubstitutionMap)

    // type arguments
    for ((key, value) in cspSubstitutions.typeVars) {
      if (value != null) {
        val keyIV = PyTypeChecker.substitutePlainly(key, ivSubstitutions, context)
        val valueIV = PyTypeChecker.substitutePlainly(value.get(), ivSubstitutions, context)
        builder.addConstraint(keyIV, valueIV, Variance.INVARIANT, ConstraintPriority.HIGH)
      }
    }
    ivSubstitutions.qualifierType = PyTypeChecker.substitutePlainly(ivSubstitutions.qualifierType, ivSubstitutions, context) // LOL

    // self argument
    if (receiverType != null && mapping.implicitParameters.getOrNull(0)?.isSelf == true) {
      val selfParameterType = mapping.implicitParameters[0].getArgumentType(context)
      if (!selfParameterType.isUnknown && (hasGenericsOrIVs(receiverType, context) || hasGenericsOrIVs(selfParameterType, context))) {
        val receiverIV = PyTypeChecker.substitutePlainly(receiverType, ivSubstitutions, context)
        val selfParameterIV = PyTypeChecker.substitutePlainly(selfParameterType, ivSubstitutions, context)
        builder.addConstraint(receiverIV, selfParameterIV, Variance.INVARIANT, ConstraintPriority.HIGH)
      }
    }

    // function arguments
    for ((argument, parameter) in mappedParameters) {
      if (parameter.isPositionalContainer || parameter.isKeywordContainer) {
        throw NotSupportedException()
      }

      val nestedReturnType = buildNestedCsp(argument, builder, context)
      val expectedParameterType = parameter.getArgumentType(context)
      val passedArgumentType = nestedReturnType ?: getArgumentType(parameter, argument, expectedParameterType, ivSubstitutions, context)

      if (!expectedParameterType.isUnknown
          && (hasGenericsOrIVs(expectedParameterType, context) || hasGenericsOrIVs(passedArgumentType, context))
      ) {
        val expectedParamTypeSelf = substituteSelfTypes(expectedParameterType, receiverType, context)
        val expectedParamTypeSelfIV = PyTypeChecker.substitutePlainly(expectedParamTypeSelf, ivSubstitutions, context)
        val passedArgumentTypeIV = PyTypeChecker.substitutePlainly(passedArgumentType, ivSubstitutions, context)
        // semantics: Actual <: TV
        builder.addConstraint(expectedParamTypeSelfIV, passedArgumentTypeIV, Variance.CONTRAVARIANT, ConstraintPriority.MEDIUM)
      }
    }

    // return type
    val declaredReturnSelf = substituteSelfTypes(declaredReturn, receiverType, context)
    val declaredReturnSelfIV = PyTypeChecker.substitutePlainly(declaredReturnSelf, ivSubstitutions, context)
    return declaredReturnSelfIV
  }

  private fun getReceiverType(callSite: PyCallSiteExpression, callableType: PyCallableType, context: TypeEvalContext): PyType? {
    val receiver = callSite.getReceiver(callableType.callable)
    val receiverType = if (receiver is PyTypedElement) context.getType(receiver) else PyAnyType.any
    val receiverTypeNewOrInit = if (receiverType is PyClassLikeType && PyUtil.isInitOrNewMethod(callableType.callable))
      receiverType.toInstance()
    else receiverType

    return normalizeType(receiverTypeNewOrInit, context)
  }

  fun hasGenericsOrIVs(type: PyType?, context: TypeEvalContext): Boolean {
    if (type is PyInferenceVariable) return true
    if (type is PyTypeParameterType) return true
    if (type is PyClassType && type.typeArguments.filterIsInstance<PyInferenceVariable>().isNotEmpty()) return true
    if (type.hasGenerics(context)) return true
    return false
  }

  private fun getArgumentType(
    parameter: PyCallableParameter,
    argument: PyExpression,
    paramType: PyType?,
    substitutions: GenericSubstitutions,
    context: TypeEvalContext,
  ): PyType? {
    val promotedToLiteral = promoteToLiteral(argument, paramType, context, substitutions)
    val actualArgType = promotedToLiteral.takeIf { !it.isUnknown } ?: context.getType(argument)
    val argTypeSelfInstantiated = if (parameter.isSelf && actualArgType is PyClassLikeType && actualArgType.isDefinition)
      actualArgType.toInstance()
    else
      actualArgType
    val normalizedType = normalizeType(argTypeSelfInstantiated, context)
    return normalizedType
  }

  private fun ensureBoundsAndConstraints(
    builder: CspBuilder,
    tv: PyTypeParameterType,
    iv: PyInferenceVariable,
    ivGenericSubstitutions: GenericSubstitutions,
    context: TypeEvalContext
  ) {
    if (tv !is PyTypeVarType) return

    // bounds
    if (tv.bound != null && !tv.bound.isAnyOrUnknown) {
      val typeVarBoundIV = PyTypeChecker.substitutePlainly(tv.bound, ivGenericSubstitutions, context)
      // semantics: TV <: Bound
      builder.addConstraint(iv, typeVarBoundIV, Variance.COVARIANT, ConstraintPriority.HIGH, false)
    }
    else if (tv.getConstraints().isNotEmpty()) {
      // Note: The Python type variable constraint(s) cannot be fully modeled without a specific CSP constraint that would model a strict logical OR.
      // A logical OR does unfortunately come with a performance impact since it makes backtracking during the solving process inevitable.
      // As a solution, Python type variable constraints will be modeled using an approximation that ensures that the type variable is both
      // (1) a subtype of the given constraints and (2) a supertype of the given constraints. The former is done modeling the tv-constraints
      // as a union type, and the latter is done modeling the tv-constraints as an intersection type.
      // Only at the very end, during instantiation, an actual set of remaining tv-constraints is chosen.
      // Note that both of these bounds are necessary to ensure that the TV will be instantiated as exactly one of the given tv-constraints
      // and not as a subtype of one of the given tv-constraints.
      val constraintsIV = tv.getConstraints().map { PyTypeChecker.substitutePlainly(it, ivGenericSubstitutions, context) }
      val intersectionOfConstraints = PyIntersectionType.intersection(constraintsIV)
      val unionOfConstraints = PyUnionType.union(constraintsIV)
      // semantics: TV approximates CV_1 ⊕ CV_2 ⊕ ... ⊕ CV_n by
      // CV_1 & CV_2 & ... & CV_n <: TV <: CV_1 | CV_2 | ... | CV_n
      builder.addConstraint(iv, intersectionOfConstraints, Variance.CONTRAVARIANT, ConstraintPriority.HIGH, false)
      builder.addConstraint(iv, unionOfConstraints, Variance.COVARIANT, ConstraintPriority.HIGH, false)
    }
  }

  fun findChildCsp(expression: PyExpression) : PyExpression? {
    when (expression) {
      is PyKeywordArgument
        -> return expression.valueExpression
      else
        -> return expression
    }
  }

  fun findParentCsp(expression: PsiElement) : PsiElement? {
    val parent = expression.parent
    when (parent) {
      is PyCallExpression -> {
        when (expression) {
          is PyArgumentList,
          is PyKeywordArgument
            -> return parent
          else
            -> return null
        }
      }
      is PyArgumentList,
      is PyKeywordArgument
        -> return findParentCsp(parent)
      else if (isCsp(parent))
        -> return parent
      else
        -> return null
    }
  }

  fun getTopCsp(si: SubstitutionsIdentifier, context: TypeEvalContext) : SubstitutionsIdentifier? {
    var top: PyExpression? = null
    var cursor: PyExpression = si.expression
    while (isCsp(cursor)) {
      top = cursor
      val parent = findParentCsp(cursor)
      cursor = parent as? PyExpression ?: break
    }
    if (top == null) {
      return null
    }
    if (top == si.expression) {
      return si
    }
    if (top is PyCallExpression) {
      val resolveContext = PyResolveContext.defaultContext(context)
      val callableType = top.multiResolveCallee(resolveContext).firstOrNull() ?: return null
      return SubstitutionsIdentifier(top, callableType)
    }
    return SubstitutionsIdentifier(top)
  }

  private fun isCsp(expression: PsiElement?): Boolean {
    when (expression) {
      is PyCallExpression,
      is PyDecoratorList,
      is PySequenceExpression,
        -> {
        return true
      }
    }
    return false
  }
}

class NotSupportedException : RuntimeException()


fun substituteSelfTypes(typeRef: PyType?, replacement: PyType?, context: TypeEvalContext): PyType? {
  if (typeRef == null || replacement == null) {
    return typeRef
  }

  var referencesInfVar = false
  PyRecursiveTypeVisitor.traverse(typeRef, context, object : PyTypeTraverser() {
    override fun visitPySelfType(selfType: PySelfType): PyRecursiveTypeVisitor.Traversal {
      referencesInfVar = true
      return PyRecursiveTypeVisitor.Traversal.TERMINATE
    }
  })
  if (!referencesInfVar) return typeRef

  return PyCloningTypeVisitor.clone(typeRef, object : PyCloningTypeVisitor(context) {
    override fun visitPySelfType(selfType: PySelfType): PyType {
      if (replacement is PyInstantiableType<*> && replacement.isDefinition != selfType.isDefinition) {
        return if (selfType.isDefinition) replacement.toClass() else replacement.toInstance()
      }
      else {
        return replacement
      }
    }
  })
}

fun normalizeType(type: PyType?, context: TypeEvalContext): PyType? {
  var normalizedType = type
  if (type is PyClassType && !type.isParameterized) {
    // convert raw types to generic types
    // keep the type variables for the CSP to be computed
    normalizedType = PyTypeChecker.findGenericDefinitionType(type.pyClass, type.isDefinition, context) ?: type
  }
  if (type is PyTupleType && type.elementTypes.isEmpty()) {
    // treat this as `tuple[()]` and normalize it to `tuple[Never, ...]`
    normalizedType = PyTupleType.createHomogeneous(type.declarationElement, PyNeverType.NEVER)
  }
  return normalizedType
}

fun unpackStarredListLiterals(elements: Array<out PyExpression?>): List<PyExpression> {
  val result = mutableListOf<PyExpression>()
  for (element in elements) {
    val starred = element as? PyStarExpression
    val sequenceLiteral = starred?.expression
    when (sequenceLiteral) {
      is PyListLiteralExpression -> result.addAll(sequenceLiteral.elements)
      is PySetLiteralExpression -> result.addAll(sequenceLiteral.elements)
      else -> result.addIfNotNull(element)
    }
  }
  return result
}
