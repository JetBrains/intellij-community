package com.jetbrains.python.psi.types

import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyDecoratorList
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyExpectedTypeJudgement.getExpectedType
import com.jetbrains.python.psi.types.PyLiteralType.Companion.promoteToLiteral
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser
import com.jetbrains.python.psi.types.PyTypeChecker.GenericSubstitutions
import com.jetbrains.python.psi.types.PyTypeChecker.collectGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import org.jetbrains.annotations.ApiStatus

class NotSupportedException : RuntimeException()


@ApiStatus.Experimental
object PyTypeInferenceCspFactory {

  @JvmStatic
  fun unifyReceiver(argsMapping: PyCallExpression.PyArgumentsMapping, context: TypeEvalContext): GenericSubstitutions {
    val callSite = argsMapping.callSiteExpression
    val callableType = argsMapping.callableType
    val receiver = callSite.getReceiver(callableType?.callable)
    try {
      return doUnifyFunctionCall(callSite, receiver, callableType, argsMapping.mappedParameters, context) ?: GenericSubstitutions()
    }
    catch (_: NotSupportedException) {
      return PyTypeChecker.unifyReceiver(receiver, context)
    }
  }

  @JvmStatic
  fun unifyGenericCall(
    callSite: PyCallSiteExpression?,
    receiver: PyExpression?,
    callableType: PyCallableType?,
    mappedParameters: Map<PyExpression, PyCallableParameter>,
    context: TypeEvalContext,
  ): GenericSubstitutions? {
    try {
      return doUnifyFunctionCall(callSite, receiver, callableType, mappedParameters, context)
    }
    catch (_: NotSupportedException) {
      return PyTypeChecker.unifyGenericCall(receiver, mappedParameters, context)
    }
  }

  // TODO: wrong parameter mapping passed by testExplicitlyParameterizedGenericConstructorCall: self missing?

  private fun doUnifyFunctionCall(
    callSite: PyCallSiteExpression?,
    receiver: PyExpression?,
    callableType: PyCallableType?,
    mappedParameters: Map<PyExpression, PyCallableParameter>,
    context: TypeEvalContext,
  ): GenericSubstitutions? {
    if (callSite !is PyCallExpression) {
      throw NotSupportedException()
    }

    val substitutions = PyTypeChecker.unifyReceiver(receiver, context)
    if (substitutions.paramSpecs.isNotEmpty() || substitutions.typeVarTuples.isNotEmpty()) {
      throw NotSupportedException()
    }

    val receiverType = getReceiverType(receiver, substitutions, context)
    val declaredReturn = callableType?.getReturnType(context)

    // Derive CSP from the function call
    val builder = CspBuilder(context)


    for (typeVarEntry in substitutions.typeVars.entries) {
      ensureInferenceVariables(builder, receiverType, typeVarEntry.key, context)
      if (typeVarEntry.value != null) {
        builder.addConstraint(typeVarEntry.key, typeVarEntry.value!!.get(), Variance.INVARIANT, ConstraintPriority.HIGH)
      }
    }

    // add type variables
    val generics = callableType.collectGenerics(context)
    for (typeVarEntry in generics.typeVars) {
      ensureInferenceVariables(builder, receiverType, typeVarEntry, context)
    }

    // arguments
    for (entry in mappedParameters) {
      val argument = entry.key
      val parameter: PyCallableParameter = entry.value
      if (parameter.isPositionalContainer() || parameter.isKeywordContainer()) {
        throw NotSupportedException()
      }

      val expectedParameterType = parameter.getArgumentType(context)
      val passedArgumentType = getArgumentType(parameter, argument, expectedParameterType, substitutions, context)

      if (expectedParameterType != null
          && (expectedParameterType.hasGenerics(context) || passedArgumentType.hasGenerics(context))
      ) {
        ensureInferenceVariables(builder, receiverType, expectedParameterType, context)
        val expectedParamType_selfBounded = substituteSelfTypes(expectedParameterType, receiverType, context)
        // semantics: Actual <: TV
        builder.addConstraint(expectedParamType_selfBounded, passedArgumentType, Variance.CONTRAVARIANT, ConstraintPriority.MEDIUM)
      }
    }

    // return type
    if (declaredReturn.hasGenerics(context)) {
      ensureInferenceVariables(builder, receiverType, declaredReturn, context)
      val expectedReturnType = getExpectedType(callSite, context)
      if (expectedReturnType != null) {
        val declaredReturn_selfBounded = substituteSelfTypes(declaredReturn, receiverType, context)
        // semantics: RT <: ExpectedReturnType
        builder.addConstraint(declaredReturn_selfBounded, expectedReturnType, Variance.COVARIANT, ConstraintPriority.LOW)
      }
    }

    builder.solve()

    // if this is a nested CPS, the outer CPS needs to reuse the unconstrained type variables
    val isNestedCsp = isNestedCsp(callSite, context)
    val solution = builder.getSolution(isNestedCsp)

    if (solution.instantiations.isEmpty() && substitutions.typeVars.isEmpty() && substitutions.typeVarTuples.isEmpty() && substitutions.paramSpecs.isEmpty() && substitutions.qualifierType == null) {
      return null
    }
    if (solution.failed) {
      if (solution.complete) {
        // since this solution is complete, we can return it as-is below

        // TODO: Remove this. At the moment, substitutions needs to be null to highlight problems.
        //        However, the throw statement should be removed and instead a partly solution returned.
        throw NotSupportedException()
      }
      else {
        // fallback to the old approach
        throw NotSupportedException()
      }
    }

    return substitutions.addToCopy(solution.instantiations, null, null)
  }

  private fun getReceiverType(receiver: PyExpression?, substitutions: GenericSubstitutions, context: TypeEvalContext): PyType? {
    val receiverType = substitutions.qualifierType ?: if (receiver is PyTypedElement) context.getType(receiver) else null
    return normalizeType(receiverType, context)
  }

  private fun getArgumentType(
    parameter: PyCallableParameter,
    argument: PyExpression,
    paramType: PyType?,
    substitutions: GenericSubstitutions,
    context: TypeEvalContext,
  ): PyType? {
    val promotedToLiteral = promoteToLiteral(argument, paramType, context, substitutions)
    val actualArgType = promotedToLiteral ?: context.getType(argument)
    val argTypeSelfInstantiated = if (parameter.isSelf && actualArgType is PyClassLikeType && actualArgType.isDefinition)
      actualArgType.toInstance()
    else
      actualArgType
    val normalizedType = normalizeType(argTypeSelfInstantiated, context)
    return normalizedType
  }

  private fun ensureInferenceVariables(builder: CspBuilder, receiverType: PyType?, type: PyType?, context: TypeEvalContext) {
    val generics = type.collectGenerics(context)

    if (generics.typeVarTuples.isNotEmpty() || generics.paramSpecs.isNotEmpty()) {
      throw NotSupportedException()
    }

    for (paramType in generics.typeVars) {
      if (builder.hasInferenceVariable(paramType)) continue
      builder.addInferenceVariable(paramType)

      // bounds
      if (paramType.getBound() != null) {
        val typeVarBound_selfBounded = substituteSelfTypes(paramType.getBound(), receiverType, context)
        // semantics: TV <: Bound
        builder.addConstraint(paramType, typeVarBound_selfBounded, Variance.COVARIANT, ConstraintPriority.HIGH)
      }
      else if (paramType.getConstraints().isNotEmpty()) {
        // Note: The Python type variable constraint(s) cannot be fully modeled without a specific CSP constraint that would model a strict logical OR.
        // A logical OR does unfortunately come with a performance impact since it makes backtracking during the solving process inevitable.
        // As a solution, Python type variable constraints will be modeled using an approximation that ensures that the type variable is both
        // (1) a subtype of the given constraints and (2) a supertype of the given constraints. The former is done modeling the tv-constraints
        // as a union type, and the latter is done modeling the tv-constraints as an intersection type.
        // Only at the very end, during instantiation, an actual set of remaining tv-constraints is chosen.
        // Note that both of these bounds are necessary to ensure that the TV will be instantiated as exactly one of the given tv-constraints
        // and not as a subtype of one of the given tv-constraints.
        val paramTypeConstraints = paramType.getConstraints().map { substituteSelfTypes(it, receiverType, context) }
        val intersectionOfConstraints = PyIntersectionType.intersection(paramTypeConstraints)
        val unionOfConstraints = PyUnionType.union(paramTypeConstraints)
        // semantics: TV approximates CV_1 ⊕ CV_2 ⊕ ... ⊕ CV_n by
        // CV_1 & CV_2 & ... & CV_n  <:  TV  <:  CV_1 | CV_2 | ... | CV_n
        builder.addConstraint(paramType, intersectionOfConstraints, Variance.CONTRAVARIANT, ConstraintPriority.HIGH)
        builder.addConstraint(paramType, unionOfConstraints, Variance.COVARIANT, ConstraintPriority.HIGH)
      }
    }
  }

  private fun substituteSelfTypes(typeRef: PyType?, replacement: PyType?, context: TypeEvalContext): PyType? {
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

  private fun isNestedCsp(callSite: PyCallSiteExpression, context: TypeEvalContext): Boolean {
    val parent = callSite.parent
    when (parent) {
      is PyArgumentList -> {
        val expectedType = getExpectedType(callSite, context)
        return expectedType != null && expectedType.hasGenerics(context)
      }
      is PyCallExpression,
      is PyDecoratorList,
        -> {
        return true
      }
    }
    return false
  }
}

fun normalizeType(type: PyType?, context: TypeEvalContext): PyType? {
  var normalizedType = type
  if (type is PyClassType && type !is PyCollectionType) {
    // convert raw types to generic types
    // keep the type variables for the CSP to be computed
    normalizedType = PyTypeChecker.findGenericDefinitionType(type.pyClass, context) ?: type
  }
  if (type is PyTupleType && type.elementTypes.isEmpty()) {
    // treat this as `tuple[()]` and normalize it to `tuple[Never, ...]`
    normalizedType = PyTupleType.createHomogeneous(type.declarationElement, PyNeverType.NEVER)
  }
  if (type is PyClassLikeType && normalizedType is PyClassLikeType && type.isDefinition && !normalizedType.isDefinition) {
    normalizedType = normalizedType.toClass()
  }
  return normalizedType
}
