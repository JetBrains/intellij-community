package com.jetbrains.python.psi.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeParameter
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
    val inferredVariance = getInferredVariance(reference, context) ?: return false
    if (inferredVariance == INVARIANT) return false
    return functionIgnoresVariance(parent)
  }

  @JvmStatic
  fun getInferredVariance(element: PsiElement?, context: TypeEvalContext): Variance? {
    val typeParamType = when (element) {
      is PyTypeParameter -> PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(element, context)
      is PyReferenceExpression,
      is PyCallExpression,
        -> PyTypingTypeProvider.getType(element, context)?.get()
      else -> return null
    }
    val typeVarType = typeParamType as? PyTypeVarType ?: return null
    return guardedGetInferredVariance(typeVarType, context)
  }

  /** Returns the variance inferred from the use of a type parameter */
  @JvmStatic
  fun getInferredVariance(typeVarType: PyTypeVarType, context: TypeEvalContext): Variance {
    return guardedGetInferredVariance(typeVarType, context)
  }

  private fun guardedGetInferredVariance(typeVarType: PyTypeVarType, context: TypeEvalContext): Variance {
    // only infer variance if implicitly/explicitly declared as INFER_VARIANCE
    val scopeOwner = typeVarType.scopeOwner
    if (scopeOwner is PyFunction) return INVARIANT
    if (typeVarType.variance != INFER_VARIANCE) return typeVarType.variance
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
      val annValue = if (element is PyAnnotation) element.value else element
      if (annValue is PyStringLiteralExpression) {
        val elementGenerator = PyElementGenerator.getInstance(annValue.project)
        val syntheticElement = elementGenerator.createExpressionFromText(LanguageLevel.forElement(annValue), annValue.stringValue)
        return collectReferencesTo(tvId, syntheticElement)
      }

      val refExpressions = PsiTreeUtil.findChildrenOfType(element, PyReferenceExpression::class.java)
      for (refExpression in refExpressions) {
        val refType = PyTypingTypeProvider.getType(refExpression, context) ?: continue
        val typeVarType = refType.get() as? PyTypeVarType ?: continue
        if (typeVarType.name == tvId.name && typeVarType.scopeOwner == tvId.scopeOwner) {
          val exprVariance = PyExpectedVarianceJudgment.getExpectedVariance(refExpression, context) ?: continue
          usages.add(exprVariance)
        }
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
    val attrName = target.name ?: return false
    return PyNames.isPrivate(attrName) || PyNames.isProtected(attrName)
  }

  /**
   * Returns true iff either
   * - the function's name starts with an underscore (i.e., is private or protected), or
   * - if [functionIgnoresVariance] is true
   */
  fun functionDoesNotAffectVarianceInference(callable: PyFunction): Boolean {
    val funName = callable.name ?: return false
    if (PyNames.isPrivate(funName) || PyNames.isProtected(funName)) return true
    return functionIgnoresVariance(callable)
  }

  /**
   * Returns true iff the function either
   * - Has no parameters, or
   * - Is `__init__` or `__new__`, or
   * - Is decorated as a class or static method.
   */
  fun functionIgnoresVariance(callable: PyFunction): Boolean {
    if (callable.parameterList.parameters.size == 0) {
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
