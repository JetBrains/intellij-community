package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.parseStdOrDataclassTransformDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.CALLABLE
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.CALLABLE_EXT
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.GENERIC
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.PROTOCOL_EXT
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.isFinal
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.isReadOnly
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeCommentOwner
import com.jetbrains.python.psi.PyTypeDeclarationStatement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.attributeDoesNotAffectVarianceInference
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.combineVariance
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.functionDoesNotAffectVarianceInference
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.getDeclaredOrInferredVariance
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.BIVARIANT
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.COVARIANT
import com.jetbrains.python.psi.types.PyTypeParameterType.Variance.INVARIANT
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
object PyExpectedVarianceJudgment {

  /** Return the expected variance for the given location. The location must be a reference inside a type expression. */
  @JvmStatic
  fun getExpectedVariance(element: PyReferenceExpression, context: TypeEvalContext): Variance? {
    return getExpectedVariance(element as PsiElement, context)
  }

  /**
   * Return the expected variance for the given location.
   * Returns null usually if the given location is not applicable for variance judgment.
   */
  private fun getExpectedVariance(element: PsiElement, context: TypeEvalContext): Variance? {
    val parent = PyUtil.getFragmentContextAwareParent(element)
    if (parent == null) return null

    return when (element) {
      is PyClass,
      is PyTypeAliasStatement,
      is PyExpressionStatement, // parent of synthetic expressions created by PyElementGenerator#createExpressionFromText()
        -> BIVARIANT
      is PyAssignmentStatement,
        -> fromAssignmentStatement(element, parent, context)
      is PyFunction,
        -> fromFunction(element, parent)
      is PyTypeDeclarationStatement,
        -> fromTypeDeclarationStatement(element, parent, context)
      is PyNamedParameter,
        -> getExpectedVariance(parent, context)?.invert()

      // keep the following list as precise and short as possible to enforce returning null whenever possible
      is PyListLiteralExpression,
      is PyArgumentList,
      is PyBinaryExpression,
      is PyParameterList,
      is PyStatementList,
      is PyAnnotation,
      is PyStringLiteralExpression,
      is PyReferenceExpression,
      is PySubscriptionExpression,
      is PyTupleExpression,
      is PyStarExpression,
        -> {
        val grandParent = PyUtil.getFragmentContextAwareParent(parent)
        when (parent) {
          is PySubscriptionExpression,
            -> fromElementInSubscriptionExpression(0, parent, context)
          is PyTupleExpression if grandParent is PySubscriptionExpression
            -> fromElementInSubscriptionExpression(parent.elements.indexOf(element), grandParent, context)
          else
            -> getExpectedVariance(parent, context)
        }
      }
      else
        -> null
    }
  }

  private fun fromFunction(function: PyFunction, parent: PsiElement): Variance? {
    if (parent !is PyStatementList || PyUtil.getFragmentContextAwareParent(parent) !is PyClass) return null
    if (functionDoesNotAffectVarianceInference(function)) return null
    return COVARIANT
  }

  private fun fromTypeDeclarationStatement(element: PyTypeDeclarationStatement, parent: PsiElement, context: TypeEvalContext): Variance? {
    val parentClass = PyUtil.getFragmentContextAwareParent(parent)
    if (parentClass !is PyClass) {
      // assume that we are e.g., on top level
      return BIVARIANT
    }
    val targetExpr = element.target as? PyTargetExpression ?: return null
    if (attributeDoesNotAffectVarianceInference(targetExpr)) return null
    if (isEffectivelyReadOnly(targetExpr, parentClass, context)) return COVARIANT
    return INVARIANT
  }

  private fun fromAssignmentStatement(element: PyAssignmentStatement, parent: PsiElement, context: TypeEvalContext): Variance? {
    val targetExpr = element.targets.singleOrNull() as? PyTargetExpression ?: return null
    return fromAnnotatedAssignment(targetExpr, parent, context)
  }

  private fun fromAnnotatedAssignment(targetExpr: PyTargetExpression, parent: PsiElement, context: TypeEvalContext): Variance? {
    if (attributeDoesNotAffectVarianceInference(targetExpr)) return null
    val parentOwner = PyUtil.getFragmentContextAwareParent(parent)
    val parentClass = when (parentOwner) {
                        is PyClass -> parentOwner
                        is PyFunction if parentOwner.name == PyNames.INIT && PyUtil.isInstanceAttribute(targetExpr) -> parentOwner.containingClass
                        else -> null
                      } ?: return null
    if (isEffectivelyReadOnly(targetExpr, parentClass, context)) return COVARIANT
    return INVARIANT
  }

  private fun fromElementInSubscriptionExpression(
    refIndex: Int,
    subscriptionExpr: PySubscriptionExpression,
    context: TypeEvalContext,
  ): Variance? {
    val qualifier = subscriptionExpr.operand as? PyReferenceExpression ?: return null
    val physicalElement = PyUtil.getFragmentContext(qualifier)
    val parentNamedParameter = PsiTreeUtil.getStubOrPsiParentOfType(physicalElement, PyNamedParameter::class.java)
    if (parentNamedParameter?.isSelf == true) return null

    val qualifierQNames = PyTypingTypeProvider.resolveToQualifiedNames(qualifier, context)
    if (qualifierQNames.any { it in setOf(GENERIC, PROTOCOL, PROTOCOL_EXT) }) {
      return BIVARIANT // for T in: `class C(Generic[T])` or `class C(Protocol[T])`
    }
    if (qualifierQNames.any { it in setOf(CALLABLE, CALLABLE_EXT) } && refIndex == 0) {
      val outerVariance = getExpectedVariance(subscriptionExpr, context) ?: return null
      return combineVariance(outerVariance, CONTRAVARIANT)
    }

    var qualifierType = PyTypingTypeProvider.getType(subscriptionExpr.operand, context)?.get()
    if (qualifierType is PyClassType && qualifierType !is PyCollectionType) {
      // convert raw types to generic types
      qualifierType = PyTypeChecker.findGenericDefinitionType(qualifierType.pyClass, context) ?: qualifierType
    }
    if (qualifierType is PyCollectionType) {
      val paramVariance = getTypeParameterVarianceAtIndex(qualifierType, refIndex, context) ?: return null
      val outerVariance = getExpectedVariance(subscriptionExpr, context) ?: return null
      return combineVariance(outerVariance, paramVariance)
    }
    return getExpectedVariance(subscriptionExpr, context)
  }

  private fun getTypeParameterVarianceAtIndex(qualifierType: PyClassType, index: Int, context: TypeEvalContext): Variance? {
    if (qualifierType is PyCollectionType) {
      if (qualifierType.classQName == PyNames.TUPLE) {
        return COVARIANT
      }
      // check definition type since generic type aliases are parameterized, i.e.: `A_Alias_1 = ClassA[T_co]` will be ClassA[Any]
      val definitionType = PyTypeChecker.findGenericDefinitionType(qualifierType.pyClass, context) ?: qualifierType
      val typeParamType = definitionType.elementTypes.getOrNull(index) as? PyTypeParameterType
                          ?: qualifierType.elementTypes.getOrNull(index) as? PyTypeParameterType
                          ?: return null
      return getDeclaredOrInferredVariance(typeParamType, context)
    }
    return null
  }

  private fun isEffectivelyReadOnly(element: PsiElement, parentClass: PyClass, context: TypeEvalContext): Boolean {
    if (element is PyTypeCommentOwner && element is PyAnnotationOwner) {
      if (isFinal(element, context) || isReadOnly(element, context)) {
        return true
      }
    }
    val isFrozen = parseStdOrDataclassTransformDataclassParameters(parentClass, context)?.frozen ?: false
    return isFrozen
  }

  private fun Variance.invert(): Variance {
    return when (this) {
      COVARIANT -> CONTRAVARIANT
      CONTRAVARIANT -> COVARIANT
      INVARIANT -> INVARIANT
      BIVARIANT -> BIVARIANT
      else -> this
    }
  }

}
