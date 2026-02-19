package com.jetbrains.python.psi.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.parseStdDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression
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
        val typeAliasType = PyTypingTypeProvider.getType(typeExpression, context)?.get() ?: return INVARIANT
        collector.collectInType(tvId, typeAliasType, COVARIANT)
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
        val isDataclassFrozen = parseStdDataclassParameters(clazz, context)?.frozen ?: false
        val processor = Processor<PsiElement> { element ->
          when (element) {
            is PyTargetExpression -> collectInAttribute(tvId, element, isDataclassFrozen)
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
        val declaredType = PyTypeChecker.findGenericDefinitionType(superType.pyClass, context) ?: continue

        val typeParams = declaredType.elementTypes
        val typeArgs = superType.elementTypes
        val idxMax = typeParams.size.coerceAtMost(typeArgs.size)
        for (idx in 0 until idxMax) {
          val typeParam = typeParams[idx] as? PyTypeVarType ?: continue
          val typeArg = superType.elementTypes[idx]
          if (typeArg is PyTypeVarType && typeArg.name == tvId.name && typeArg.scopeOwner == tvId.scopeOwner && typeParam.scopeOwner != null) {
            // we need to infer the variance of the base classes type variable: `class Derived[T](Base[T])`
            val substitutedTvId = TypeVariableId(typeParam.name, typeParam.scopeOwner!!)
            collectInType(substitutedTvId, declaredType, BIVARIANT)
          }
        }
      }
    }

    private fun collectInAttribute(tvId: TypeVariableId, target: PyTargetExpression, isDataclassFrozen: Boolean) {
      if (attributeDoesNotAffectVarianceInference(target)) return
      val attributeType = context.getType(target)
      val variance = if (isDataclassFrozen || PyTypingTypeProvider.isFinal(target, context)) COVARIANT else INVARIANT
      collectInType(tvId, attributeType, variance)
    }

    private fun collectInFunction(tvId: TypeVariableId, function: PyFunction) {
      if (functionDoesNotAffectVarianceInference(function)) return
      val callableType = context.getType(function) as? PyCallableType ?: return

      val returnType = callableType.getReturnType(context)
      collectInType(tvId, returnType, COVARIANT)

      val parameters = callableType.getParameters(context) ?: return
      for (parameter in parameters) {
        if (parameter.isSelf) continue
        val parameterType = parameter.getType(context)
        collectInType(tvId, parameterType, CONTRAVARIANT)
      }
    }

    fun collectInType(tvId: TypeVariableId, type: PyType?, currentVariance: Variance) {
      if (type == null) return
      val visitor = VarianceInferenceTypeVisitor(tvId, usages, currentVariance, context)
      PyTypeVisitor.visit(type, visitor)
    }
  }


  private class VarianceInferenceTypeVisitor(
    val tvId: TypeVariableId,
    val usages: MutableSet<Variance>,
    currentVariance: Variance,
    val context: TypeEvalContext,
  ) : PyTypeVisitorExt<Unit>() {

    val varianceStack: MutableList<Variance> = mutableListOf(currentVariance)

    override fun visitPyTypeVarType(typeVarType: PyTypeVarType) {
      if (typeVarType.name == tvId.name && typeVarType.scopeOwner == tvId.scopeOwner) {
        usages.add(varianceStack.last())
      }
    }

    override fun visitPyGenericType(genericType: PyCollectionType) {
      val declaredType = PyTypeChecker.findGenericDefinitionType(genericType.pyClass, context) ?: return
      val typeParams = declaredType.elementTypes
      val typeArgs = genericType.elementTypes
      val idxMax = typeParams.size.coerceAtMost(typeArgs.size)
      for (idx in 0 until idxMax) {
        val declaredTV = typeParams[idx] as? PyTypeVarType ?: continue
        val paramVariance = if (declaredTV.variance == INFER_VARIANCE) getInferredVariance(declaredTV, context) else declaredTV.variance
        val nextVariance = combineVariance(varianceStack.last(), paramVariance)

        varianceStack.add(nextVariance)
        visit(typeArgs[idx], this)
        varianceStack.removeAt(varianceStack.size - 1)
      }
    }

    override fun visitPyCallableType(callableType: PyCallableType) {
      val returnType = callableType.getReturnType(context)
      val nextCovariant = combineVariance(varianceStack.last(), COVARIANT)
      varianceStack.add(nextCovariant)
      visit(returnType, this)
      varianceStack.removeAt(varianceStack.size - 1)

      val parameters = callableType.getParameters(context) ?: return
      val nextContravariant = combineVariance(varianceStack.last(), CONTRAVARIANT)
      for (parameter in parameters) {
        val parameterType = parameter.getType(context)
        varianceStack.add(nextContravariant)
        visit(parameterType, this)
        varianceStack.removeAt(varianceStack.size - 1)
      }
    }

    override fun visitPyUnionType(unionType: PyUnionType) {
      for (member in unionType.members) {
        visit(member, this)
      }
    }

    override fun visitPyIntersectionType(unionType: PyIntersectionType) {
      for (member in unionType.members) {
        visit(member, this)
      }
    }

    override fun visitPyTupleType(tupleType: PyTupleType) {
      for (elementType in tupleType.elementTypes) {
        visit(elementType, this)
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
