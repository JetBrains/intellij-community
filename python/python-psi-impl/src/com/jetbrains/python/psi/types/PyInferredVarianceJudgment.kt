package com.jetbrains.python.psi.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.jetbrains.python.ProtectionLevel
import com.jetbrains.python.PyNames
import com.jetbrains.python.ast.PyAstFunction.Modifier
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeDeclarationStatement
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.VarianceInferenceKind.DECLARED_AND_INFERRED
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.VarianceInferenceKind.FUNCTION_RETURN_ONLY
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.VarianceInferenceKind.INFERRED_ONLY
import com.jetbrains.python.psi.types.PyVariance.BIVARIANT
import com.jetbrains.python.psi.types.PyVariance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyVariance.COVARIANT
import com.jetbrains.python.psi.types.PyVariance.INFER_VARIANCE
import com.jetbrains.python.psi.types.PyVariance.INVARIANT
import com.jetbrains.python.psi.types.PyVarianceIntermediateResult.RECURSIVE
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
object PyInferredVarianceJudgment {
  private val recursionGuard = RecursionManager.createGuard<TypeVariableId>("PyInferredVarianceJudgment")


  /**
   * Returns true for references to non-invariant type parameter at locations where variance is ignored
   * and is supposed to be treated as invariant, e.g., for type parameters of static methods in classes.
   */
  @JvmStatic
  fun isEffectivelyInvariant(element: PyTypedElement?, context: TypeEvalContext): Boolean {
    val reference = element as? PyReferenceExpression ?: return false
    val inferredVariance = getDeclaredOrInferredVariance(reference, context) ?: return false
    if (inferredVariance == INVARIANT) return false
    val allParentsSequence = generateSequence<PsiElement>(reference) { PyUtil.getFragmentContextAwareParent(it) }
    val nearestFunction = allParentsSequence.firstOrNull { it is PyFunction || it is PyClass } as? PyFunction ?: return false
    return functionIgnoresVariance(nearestFunction)
  }

  /**
   * Convenience method for [getDeclaredOrInferredVariance(PyTypeParameterType, TypeEvalContext)]:
   * It calls [findTypeVariable] first and passes the resulting type parameter type to [getDeclaredOrInferredVariance].
   */
  @JvmStatic
  fun getDeclaredOrInferredVariance(element: PyTypedElement?, context: TypeEvalContext): PyVariance? {
    val typeParameterType = findTypeVariable(element, context) ?: return null
    return guardedGetDeclaredOrInferredVarianceOrInvariant(typeParameterType, DECLARED_AND_INFERRED, context)
  }

  /**
   * Convenience method for [getInferredVariance(PyTypeParameterType, TypeEvalContext)]:
   * It calls [findTypeVariable] first and passes the resulting type parameter type to [getInferredVariance].
   */
  @JvmStatic
  fun getInferredVariance(element: PyTypedElement?, context: TypeEvalContext): PyVariance? {
    val typeVarType = findTypeVariable(element, context) ?: return null
    return guardedGetDeclaredOrInferredVarianceOrInvariant(typeVarType, INFERRED_ONLY, context)
  }

  /** Returns the type parameter type if the given element is a type parameter or a reference to a type parameter */
  fun findTypeVariable(element: PyTypedElement?, context: TypeEvalContext): PyTypeParameterType? {
    val typeParamType = when (element) {
      is PyTypeParameter
        -> PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(element, context)
      is PyReferenceExpression,
      is PyCallExpression,
        -> PyTypingTypeProvider.getType(element, context)?.get()
      else
        -> return null
    }
    return typeParamType as? PyTypeParameterType
  }

  /**
   * Returns either the declared variance if it is declared explicitly, or infers the variance of the given type parameter from its usages.
   */
  @JvmStatic
  fun getDeclaredOrInferredVariance(typeParameterType: PyTypeParameterType, context: TypeEvalContext): PyVariance {
    return guardedGetDeclaredOrInferredVarianceOrInvariant(typeParameterType, DECLARED_AND_INFERRED, context)
  }

  /** Returns the inferred the variance of the given type parameter from its usages */
  @JvmStatic
  fun getInferredVariance(typeParameterType: PyTypeParameterType, context: TypeEvalContext): PyVariance {
    return guardedGetDeclaredOrInferredVarianceOrInvariant(typeParameterType, INFERRED_ONLY, context)
  }

  /**
   * Returns either the declared variance if it is declared explicitly, or infers the variance of the given type parameter from its usages.
   * Returns [RECURSIVE] iff the given parameter type's variance depends on itself.
   *
   * @see [RECURSIVE]
   */
  @JvmStatic
  fun getIntermediateVariance(typeParameterType: PyTypeParameterType, context: TypeEvalContext): PyVarianceIntermediateResult {
    return guardedGetDeclaredOrInferredVariance(typeParameterType, DECLARED_AND_INFERRED, context)
  }

  /**
   * Returns the variance of the given type parameter from its usages in the return-type of a function.
   *
   * Respects effective scope owner of function:
   * ```
   * def f[F]() -> Callable[[F], F]: ...
   * ```
   *
   * Usually, the scope owner would be function `f`. However, since `F` is not used/bound in the arguments of `f` nor via its default
   * type (it has none), `F` is still open. Since `F` is used only in the returned callable type of `f`, its effective scope owner is
   * that returned function.
   *
   * @see [UsageCollector.collectInFunctionReturn]
   */
  @JvmStatic
  fun getFunctionReturnVariance(typeParameterType: PyTypeParameterType, context: TypeEvalContext): PyVarianceIntermediateResult {
    return guardedGetDeclaredOrInferredVariance(typeParameterType, FUNCTION_RETURN_ONLY, context)
  }

  private enum class VarianceInferenceKind {
    DECLARED_AND_INFERRED,
    INFERRED_ONLY,
    FUNCTION_RETURN_ONLY,
  }

  private fun guardedGetDeclaredOrInferredVarianceOrInvariant(
    typeParameterType: PyTypeParameterType,
    inferenceKind: VarianceInferenceKind,
    context: TypeEvalContext,
  ): PyVariance {
    val result = guardedGetDeclaredOrInferredVariance(typeParameterType, inferenceKind, context)
    return when (result) {
      is RECURSIVE -> INVARIANT
      is PyVariance -> result
    }
  }

  private fun guardedGetDeclaredOrInferredVariance(
    typeParameterType: PyTypeParameterType,
    inferenceKind: VarianceInferenceKind,
    context: TypeEvalContext,
  ): PyVarianceIntermediateResult {
    val scopeOwner = typeParameterType.scopeOwner
    if (inferenceKind != FUNCTION_RETURN_ONLY && scopeOwner is PyFunction) return INVARIANT // inferred/declared variance assumes invariance
    if (inferenceKind == FUNCTION_RETURN_ONLY && scopeOwner !is PyFunction) return INVARIANT // inference kind works only with functions
    if (inferenceKind == DECLARED_AND_INFERRED && typeParameterType.variance != INFER_VARIANCE) return typeParameterType.variance
    if (scopeOwner == null) return INVARIANT

    val cachedVariance = context.getVarianceCache()[typeParameterType]
    if (cachedVariance != null) {
      return cachedVariance
    }

    // Notes on recursive situations: Returning BIVARIANT is most permitting and avoids incorrect results.
    // Also, returning BIVARIANT guarantees stable variance inferences independent of the entry point.
    try {
      val tvId = TypeVariableId(typeParameterType.name, scopeOwner)
      val result = recursionGuard.doPreventingRecursion(tvId, true) {
        val actualResult = doGetInferredVariance(tvId, inferenceKind, context)
        context.getVarianceCache()[typeParameterType] = actualResult
        actualResult
      } ?: RECURSIVE

      return result
    }
    catch (_: StackOverflowPreventedException) {
      // this is supposed to happen in recursive situations and a safe exit. No need to bother tests.
      return RECURSIVE
    }

  }

  private fun doGetInferredVariance(tvId: TypeVariableId, inferenceKind: VarianceInferenceKind, context: TypeEvalContext): PyVariance {
    val collector = UsageCollector(tvId, context)
    when (tvId.scopeOwner) {
      is PyClass -> collector.collectInClass(tvId.scopeOwner)
      is PyTypeAliasStatement -> {
        val typeExpression = tvId.scopeOwner.typeExpression ?: return INVARIANT
        collector.collectReferencesInTypeExpr(typeExpression)
      }
      is PyFunction if (inferenceKind == FUNCTION_RETURN_ONLY) -> collector.collectInFunctionReturn(tvId.scopeOwner)
      else -> return INVARIANT
    }

    return when {
      collector.usages.isEmpty()
        // By definition of PEP-695/#variance-inference, variance must be invariant here.
        // However, returning bivariant here improves the soundness of variance inference.
        -> BIVARIANT
      collector.usages.contains(INVARIANT) -> INVARIANT
      collector.usages.contains(COVARIANT) && collector.usages.contains(CONTRAVARIANT) -> INVARIANT
      collector.usages.contains(COVARIANT) -> COVARIANT
      collector.usages.contains(CONTRAVARIANT) -> CONTRAVARIANT
      collector.usages.contains(BIVARIANT) -> BIVARIANT
      else -> INVARIANT
    }
  }

  private class UsageCollector(
    val tvId: TypeVariableId,
    val context: TypeEvalContext,
  ) {
    val usages = mutableSetOf<PyVariance>()

    fun collectInClass(clazz: PyClass) {
      val classType = context.getType(clazz)
      if (classType is PyClassLikeType) {
        classType.visitMembers(
          { element ->
            when (element) {
              is PyTargetExpression -> collectInAttribute(element)
              is PyFunction -> collectInFunction(element)
            }
            val isInvariantAlready =
              INVARIANT in usages || (COVARIANT in usages && CONTRAVARIANT in usages)
            !isInvariantAlready // performance tweak: no need to search for further usages
          }, false, context)
      }
      collectInBaseClasses(clazz)
    }

    fun collectInFunctionReturn(function: PyFunction) {
      if (effectiveScopeOwnerIsReturnedCallable(function)) {
        // TODO: Aliases and quoted type annotations
        val returnedCallableExpression = PyTypingTypeProvider.getAnnotationValue(function, context) as? PySubscriptionExpression
        val callableTupleExpression = returnedCallableExpression?.indexExpression as? PyTupleExpression
        val callableReturnExpression = callableTupleExpression?.elements?.getOrNull(1)
        if (callableReturnExpression != null) {
          collectReferencesInTypeExpr(callableReturnExpression)
        }
      }
      else {
        collectReferencesInAnnotation(function)
      }
      if (usages.isEmpty()) {
        // happens when the return type is not annotated
        usages.add(INVARIANT)
      }
    }

    /** This clears the [usages] field when done. */
    private fun effectiveScopeOwnerIsReturnedCallable(function: PyFunction): Boolean {
      val retType = function.getReturnType(context)
      if (retType !is PyCallableType) return false
      collectInFunctionArguments(function) // fills usages if type parameter is used in arguments
      val notUsedInArguments = usages.isEmpty()
      usages.clear()
      return notUsedInArguments
    }

    private fun collectInBaseClasses(clazz: PyClass) {
      for (superClassExpr in PyTypingTypeProvider.getSuperClassExpressions(clazz)) {
        collectReferencesInTypeExpr(superClassExpr)
      }
    }

    private fun collectInAttribute(target: PyTargetExpression) {
      if (attributeDoesNotAffectVarianceInference(target)) return
      collectReferencesInAnnotation(target)
    }

    private fun collectInFunction(function: PyFunction) {
      collectAnnotatedInstanceAttributesInInit(function)
      if (functionDoesNotAffectVarianceInference(function)) return
      collectReferencesInAnnotation(function)
      collectInFunctionArguments(function)
    }

    private fun collectInFunctionArguments(function: PyFunction) {
      for (parameter in function.parameterList.parameters) {
        if (parameter.isSelf) continue
        if (parameter !is PyNamedParameter) continue
        collectReferencesInAnnotation(parameter)
      }
    }

    private fun collectAnnotatedInstanceAttributesInInit(function: PyFunction) {
      if (function.name != PyNames.INIT) return
      val tgtExprs = if (function.stub == null) {
        val tgtExprsFromDeclarations = function.statementList.childrenOfType<PyTypeDeclarationStatement>().map { it.target }
        val tgtExprsFromAssignments = function.statementList.childrenOfType<PyAssignmentStatement>().flatMap { it.targets.toList() }
        val allTgtExprs = tgtExprsFromDeclarations + tgtExprsFromAssignments
        allTgtExprs.filterIsInstance<PyTargetExpression>()
      }
      else {
        // use stub if available to avoid un-stubbing
        PsiTreeUtil.getStubChildrenOfTypeAsList(function, PyTargetExpression::class.java)
      }

      for (tgtExpr in tgtExprs) {
        if (attributeDoesNotAffectVarianceInference(tgtExpr)) continue
        if (!PyUtil.isInstanceAttribute(tgtExpr)) continue
        if (tgtExpr.annotationValue == null) {
          // attributes without type annotations are supposed to be of type Any:
          // def __init__(self, x: T) -> None:
          //   self.attr = x # attr is Any
        }
        else {
          // case for: def __init__(self, x: T) -> None: self.attr: T = x
          collectReferencesInAnnotation(tgtExpr)
        }
      }
    }

    fun collectReferencesInAnnotation(annotationOwner: PyAnnotationOwner) {
      val annotationValue = PyTypingTypeProvider.getAnnotationValue(annotationOwner, context)
      if (annotationValue != null) {
        return collectReferencesInTypeExpr(annotationValue)
      }
    }

    fun collectReferencesInTypeExpr(element: PyExpression) {
      if (element is PyReferenceExpression) {
        val refType = PyTypingTypeProvider.getType(element, context)
        val typeParamType = refType?.get() as? PyTypeParameterType
        if (typeParamType == null) {
          if (element.qualifier != null) {
            collectReferencesInTypeExpr(element.qualifier!!)
          }
          return
        }
        if (typeParamType.name == tvId.name && typeParamType.scopeOwner == tvId.scopeOwner) {
          val exprVariance = PyExpectedVarianceJudgment.getExpectedVariance(element, context).toPyVariance() ?: return
          usages.add(exprVariance)
        }
      }
      else if (element is PyStringLiteralExpression) {
        val syntheticElement = PyUtil.createExpressionFromFragment(element.stringValue, element) ?: return
        return collectReferencesInTypeExpr(syntheticElement)
      }
      else {
        val refExpressions = PsiTreeUtil.findChildrenOfAnyType(element, PyReferenceExpression::class.java, PyStringLiteralExpression::class.java)
        for (refExpression in refExpressions) {
          collectReferencesInTypeExpr(refExpression)
        }
      }
    }
  }

  private data class TypeVariableId(val name: String, val scopeOwner: PyQualifiedNameOwner)

  /**
   * Returns true iff
   * - The attribute's name starts with an underscore (i.e., is private or protected).
   */
  fun attributeDoesNotAffectVarianceInference(target: PyTargetExpression): Boolean {
    return target.protectionLevel != ProtectionLevel.PUBLIC
  }

  /**
   * Returns true iff either:
   * - The function's name starts with an underscore (i.e., is private or protected), or
   * - If [functionIgnoresVariance] is true
   */
  fun functionDoesNotAffectVarianceInference(callable: PyFunction): Boolean {
    if (callable.protectionLevel != ProtectionLevel.PUBLIC) return true
    return functionIgnoresVariance(callable)
  }

  /**
   * Returns true iff the function either:
   * - Has no parameters, or
   * - Is `__init__` or `__new__`, or
   * - Is decorated as a class or static method.
   */
  fun functionIgnoresVariance(callable: PyFunction): Boolean {
    if (callable.parameterList.parameters.isEmpty()) {
      return true // methods with no parameters are unbound instance methods and cannot be called on an instance
    }
    if (PyUtil.isInitOrNewMethod(callable)) {
      return true // new and init methods take covariant parameters
    }
    when (callable.modifier) {
      Modifier.CLASSMETHOD, Modifier.STATICMETHOD,
        -> return true // static and class methods ignore variance
      else
        -> return false
    }
  }
}
