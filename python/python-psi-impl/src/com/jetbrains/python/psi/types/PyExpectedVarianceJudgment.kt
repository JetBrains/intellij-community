package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.parseStdDataclassParameters
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.GENERIC
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.PROTOCOL
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.PROTOCOL_EXT
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.isFinal
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.isReadOnly
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeCommentOwner
import com.jetbrains.python.psi.PyTypeDeclarationStatement
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.attributeDoesNotAffectVarianceInference
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.combineVariance
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.functionDoesNotAffectVarianceInference
import com.jetbrains.python.psi.types.PyInferredVarianceJudgment.getInferredVariance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.BIVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.CONTRAVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.COVARIANT
import com.jetbrains.python.psi.types.PyTypeVarType.Variance.INVARIANT
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
object PyExpectedVarianceJudgment {

  /**
   * Returns the variance expected from the given type variable at its current location.
   * Returns null usually if the given location is not applicable for variance judgment.
   */
  @JvmStatic
  fun getExpectedVariance(element: PsiElement, context: TypeEvalContext): Variance? {
    val parent = element.parent ?: return null

    return when (element) {
      is PyClass,
      is PyTypeAliasStatement,
      is PyExpressionStatement, // parent of synthetic expressions created by PyElementGenerator#createExpressionFromText()
        -> BIVARIANT
      is PyFunction,
        -> fromFunction(element, parent)
      is PyTypeDeclarationStatement,
        -> fromTypeDeclarationStatement(element, parent, context)
      is PyNamedParameter,
        -> getExpectedVariance(parent, context)?.invert()
      is PyListLiteralExpression,
        -> fromListLiteral(element, parent, context)

      // keep the following list as precise and short as possible to enforce returning null whenever possible
      is PyArgumentList,
      is PyBinaryExpression,
      is PyParameterList,
      is PyStatementList,
      is PyAnnotation,
      is PyStringLiteralExpression,
      is PyReferenceExpression,
      is PySubscriptionExpression,
      is PyTupleExpression,
        -> {
        when (parent) {
          is PySubscriptionExpression,
            -> fromElementInSubscriptionExpression(0, parent, context)
          is PyTupleExpression if parent.parent is PySubscriptionExpression
            -> fromElementInSubscriptionExpression(parent.elements.indexOf(element),
                                                   parent.parent as PySubscriptionExpression, context)
          else
            -> getExpectedVariance(parent, context)
        }
      }
      else
        -> null
    }
  }

  private fun fromFunction(function: PyFunction, parent: PsiElement): Variance? {
    if (parent !is PyStatementList && parent.parent !is PyClass) return null
    if (functionDoesNotAffectVarianceInference(function)) return null
    return COVARIANT
  }

  private fun fromTypeDeclarationStatement(element: PyTypeDeclarationStatement, parent: PsiElement, context: TypeEvalContext): Variance? {
    val parentClass = parent.parent as? PyClass ?: return null
    val targetExpr = element.target as? PyTargetExpression ?: return null
    if (attributeDoesNotAffectVarianceInference(targetExpr)) return null
    if (isEffectivelyReadOnly(targetExpr, parentClass, context)) return COVARIANT
    return INVARIANT
  }

  private fun fromElementInSubscriptionExpression(
    refIndex: Int,
    subscriptionExpr: PySubscriptionExpression,
    context: TypeEvalContext,
  ): Variance? {
    val qualifier = subscriptionExpr.qualifier as? PyReferenceExpression ?: return null
    var qualifierType = context.getType(qualifier)
    if (qualifierType is PyClassType && qualifierType !is PyCollectionType) {
      // convert raw types to generic types
      qualifierType = PyTypeChecker.findGenericDefinitionType(qualifierType.pyClass, context) ?: qualifierType
    }

    if (qualifierType is PyClassLikeType && setOf(GENERIC, PROTOCOL, PROTOCOL_EXT).contains(qualifierType.classQName)) {
      return BIVARIANT // for T in: `class C(Generic[T])` or `class C(Protocol[T])`
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
      val typeParamType = qualifierType.elementTypes.getOrNull(index) as? PyTypeVarType ?: return null
      return getInferredVariance(typeParamType, context)
    }
    return null
  }

  private fun fromListLiteral(element: PyListLiteralExpression, parent: PsiElement, context: TypeEvalContext): Variance? {
    val parentVariance = getExpectedVariance(parent, context) ?: return null
    val grandParent = parent.parent
    if (grandParent is PySubscriptionExpression && parent is PyTupleExpression
        && parent.elements.size == 2 && parent.elements[0] === element && grandParent.qualifier != null
    ) {
      val type = context.getType(grandParent.qualifier!!)
      if (isTypingCallable(type)) {
        // element is at the argument position of Callable[[...], ...]
        return combineVariance(parentVariance, CONTRAVARIANT)
      }
    }
    return parentVariance
  }

  private fun isTypingCallable(type: PyType?): Boolean {
    return type is PyClassLikeType && PyTypingTypeProvider.CALLABLE == type.classQName
  }

  private fun isEffectivelyReadOnly(element: PsiElement, parentClass: PyClass, context: TypeEvalContext): Boolean {
    if (element is PyTypeCommentOwner && element is PyAnnotationOwner) {
      if (isFinal(element, context) || isReadOnly(element, context)) {
        return true
      }
    }
    val isFrozen = parseStdDataclassParameters(parentClass, context)?.frozen ?: false
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
