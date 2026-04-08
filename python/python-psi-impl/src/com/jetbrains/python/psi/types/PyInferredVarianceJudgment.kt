package com.jetbrains.python.psi.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.python.ProtectionLevel
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.functionIgnoresVariance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.BIVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.COVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.INFER_VARIANCE
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.INVARIANT
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
object PyInferredVarianceJudgment {
  private val recursionGuard = RecursionManager.createGuard<TypeVariableId>("PyInferredVarianceJudgment")


  /**
   * Returns true for references to non-invariant type parameter at locations where variance is ignored
   * and is supposed to be treated as invariant, e.g., for type parameters of static methods in classes.
   */
  @JvmStatic
  fun isEffectivelyInvariant(element: PsiElement?, context: TypeEvalContext): Boolean {
    val reference = element as? PyReferenceExpression ?: return false
    val parent = PsiTreeUtil.getParentOfType(reference, PyFunction::class.java, PyClass::class.java) as? PyFunction ?: return false
    val inferredVariance = getDeclaredOrInferredVariance(reference, context) ?: return false
    if (inferredVariance == INVARIANT) return false
    return functionIgnoresVariance(parent)
  }

  /** Returns the declared variance or infers the variance from the use cases of the type parameter */
  @JvmStatic
  fun getDeclaredOrInferredVariance(element: PsiElement?, context: TypeEvalContext): Variance? {
    val typeVarType = findTypeVariable(element, context) ?: return null
    return guardedGetDeclaredOrInferredVariance(typeVarType, true, context)
  }

  /** Returns the inferred the variance from the use cases of the type parameter */
  @JvmStatic
  fun getInferredVariance(element: PsiElement?, context: TypeEvalContext): Variance? {
    val typeVarType = findTypeVariable(element, context) ?: return null
    return guardedGetDeclaredOrInferredVariance(typeVarType, false, context)
  }

  private fun findTypeVariable(element: PsiElement?, context: TypeEvalContext): PyTypeVarType? {
    val typeParamType = when (element) {
      is PyTypeParameter -> PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(element, context)
      is PyReferenceExpression,
      is PyCallExpression,
        -> PyTypingTypeProvider.getType(element, context)?.get()
      else -> return null
    }
    return typeParamType as? PyTypeVarType
  }

  /** Returns the declared variance or infers the variance from the use cases of the type parameter */
  @JvmStatic
  fun getDeclaredOrInferredVariance(typeVarType: PyTypeVarType, context: TypeEvalContext): Variance {
    return guardedGetDeclaredOrInferredVariance(typeVarType, true, context)
  }

  /** Returns the inferred the variance from the use cases of the type parameter */
  @JvmStatic
  fun getInferredVariance(typeVarType: PyTypeVarType, context: TypeEvalContext): Variance {
    return guardedGetDeclaredOrInferredVariance(typeVarType, false, context)
  }

  private fun guardedGetDeclaredOrInferredVariance(typeVarType: PyTypeVarType, checkDeclaredVariance: Boolean, context: TypeEvalContext): Variance {
    val scopeOwner = typeVarType.scopeOwner
    if (scopeOwner is PyFunction) return INVARIANT
    if (checkDeclaredVariance && typeVarType.variance != INFER_VARIANCE) return typeVarType.variance
    if (scopeOwner == null) return INVARIANT

    val tvId = TypeVariableId(typeVarType.name, scopeOwner)

    // Notes on recursive situations: Returning BIVARIANT is most permitting and avoids incorrect results.
    // Also, returning BIVARIANT guarantees stable variance inferences independent of the entry point.
    try {
      return recursionGuard.doPreventingRecursion(tvId, true) {
        doGetInferredVariance(tvId, context)
      } ?: BIVARIANT
    }
    catch (_: StackOverflowPreventedException) {
      // this is supposed to happen in recursive situations and a safe exit. No need to bother tests.
      return BIVARIANT
    }

  }

  private fun doGetInferredVariance(tvId: TypeVariableId, context: TypeEvalContext): Variance {
    val collector = UsageCollector(context)
    when (tvId.scopeOwner) {
      is PyClass -> collector.collectInClass(tvId, tvId.scopeOwner)
      is PyTypeAliasStatement -> {
        val typeExpression = tvId.scopeOwner.typeExpression ?: return INVARIANT
        collector.collectReferencesTo(tvId, typeExpression)
      }
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
    val context: TypeEvalContext,
  ) {
    val usages = mutableSetOf<Variance>()

    fun collectInClass(tvId: TypeVariableId, clazz: PyClass) {
      val classType = context.getType(clazz)
      if (classType is PyClassLikeType) {
        val processor = Processor<PsiElement> { element ->
          when (element) {
            is PyTargetExpression -> collectInAttribute(tvId, element)
            is PyFunction -> collectInFunction(tvId, element)
          }
          val isInvariantAlready = usages.contains(INVARIANT) || (usages.contains(COVARIANT) && usages.contains(CONTRAVARIANT))
          !isInvariantAlready // performance tweak: no need to search for further usages
        }
        classType.visitMembers(processor, false, context)
      }
      collectInBaseClasses(tvId, clazz)
    }

    private fun collectInBaseClasses(tvId: TypeVariableId, clazz: PyClass) {
      for (superClassExpr in clazz.superClassExpressions) {
        val superType = PyTypingTypeProvider.getType(superClassExpr, context)?.get() ?: continue
        if (superType !is PyCollectionType) continue
        collectReferencesTo(tvId, superClassExpr)
      }
    }

    private fun collectInAttribute(tvId: TypeVariableId, target: PyTargetExpression) {
      if (attributeDoesNotAffectVarianceInference(target)) return
      collectReferencesTo(tvId, target.annotation)
    }

    private fun collectInFunction(tvId: TypeVariableId, function: PyFunction) {
      if (functionDoesNotAffectVarianceInference(function)) return
      val callableType = context.getType(function) as? PyCallableType ?: return
      collectReferencesTo(tvId, function.annotation)

      val parameters = callableType.getParameters(context) ?: return
      for (parameter in parameters) {
        if (parameter.isSelf) continue
        collectReferencesTo(tvId, parameter.parameter)
      }
    }

    fun collectReferencesTo(tvId: TypeVariableId, element: PsiElement?) {
      if (element == null) return

      if (element is PyReferenceExpression) {
        val refType = PyTypingTypeProvider.getType(element, context) ?: return
        val typeVarType = refType.get() as? PyTypeVarType ?: return
        if (typeVarType.name == tvId.name && typeVarType.scopeOwner == tvId.scopeOwner) {
          val exprVariance = PyExpectedVarianceJudgment.getExpectedVariance(element, context) ?: return
          usages.add(exprVariance)
        }
      }

      var annValue = if (element is PyNamedParameter) element.annotation else element
      annValue = if (annValue is PyAnnotation) annValue.value else annValue
      if (annValue is PyStringLiteralExpression) {
        val syntheticElement = PyUtil.createExpressionFromFragment(annValue.stringValue, annValue) ?: return
        return collectReferencesTo(tvId, syntheticElement)
      }

      val refExpressions = PsiTreeUtil.findChildrenOfType(element, PyReferenceExpression::class.java)
      for (refExpression in refExpressions) {
        collectReferencesTo(tvId, refExpression)
      }
    }
  }

  private data class TypeVariableId(val name: String, val scopeOwner: PyQualifiedNameOwner)

  fun combineVariance(outer: Variance, inner: Variance): Variance {
    return when {
      outer == INVARIANT || inner == INVARIANT -> INVARIANT
      outer == INFER_VARIANCE || inner == INFER_VARIANCE -> INVARIANT
      outer == BIVARIANT -> inner
      inner == BIVARIANT -> outer
      outer == inner -> COVARIANT
      else -> CONTRAVARIANT
    }
  }

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
    when (callable.name) {
      PyNames.INIT, PyNames.NEW,
        -> return true // new and init methods take covariant parameters
    }
    val decorators = callable.decoratorList?.decorators ?: return false
    for (decorator in decorators) {
      when (decorator.name) {
        PyNames.CLASSMETHOD, PyNames.STATICMETHOD,
          -> return true // static and class methods ignore variance
      }
    }
    return false
  }
}
